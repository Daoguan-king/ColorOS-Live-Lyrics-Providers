/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.app.Application
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugConfig
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeModeResolver
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackGenerationPolicy
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import io.github.andrealtb.coloroslyrics.provider.core.publisher.NativeLyricInfoPublisher
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BoomingMusic adapter hooks.
 *
 * The host decodes lyrics only while its lyrics/now-playing screen is open, and every host class
 * that owns that pipeline is minified in a release APK. This adapter therefore hooks only framework
 * classes (`android.media.session.MediaSession`) and rebuilds the host's own lyric-source order from
 * host-visible storage (sidecar file, embedded tag, downloaded row) when the player is in the
 * background, which is exactly the case the lock screen and AOD need.
 */
class BoomingMusicSessionHooker(private val hookContext: ProviderHookContext) {

    private val hookRuntime = hookContext.runtime
    private val hostContext = hookContext.application
    private val hostPackage = hookContext.packageName
    private val hostVersion = hookContext.hostVersion

    private val environment = BoomingMusicHostEnvironment(hostContext)
    private val sessions = BoomingMusicMediaSessionRegistry()
    private val generationPolicy = TrackGenerationPolicy()
    private val loadGate = BoomingMusicLyricLoadGate()
    private val pendingStore = BoomingMusicPendingPublicationStore()
    private val loader = BoomingMusicLyricLoader(hostContext, environment, hookContext.classLoader)

    private val loadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val loadLock = Any()

    @Volatile
    private var loadJob: Job? = null

    fun onHook() {
        val resolution = RuntimeModeResolver.resolve(hostContext)
        if (!resolution.mode.isSupported) {
            StructuredDiagnostics.logWarning(
                DiagnosticEvent(
                    component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                    area = "bootstrap",
                    event = "HOOK_DISABLED",
                    mode = resolution.mode,
                    process = resolution.processName,
                    reason = resolution.markerSource
                )
            )
            return
        }

        val debug = ProviderDebugConfig.applyDiagnostics(
            mode = resolution.mode,
            provider = ProviderId.BOOMINGMUSIC,
            rootSource = hookContext.debugSource,
            frameworkSink = hookContext.frameworkSink
        )
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "bootstrap",
                event = "DEBUG_CONFIG_APPLIED",
                mode = resolution.mode,
                process = hostPackage,
                providerVersion = hostVersion,
                reason = debug.reason
            )
        )

        installSessionHooks()
        installApplicationHooks()
    }

    private fun installSessionHooks() {
        runCatching {
            val sessionType = MediaSession::class.java
            sessionType.declaredConstructors.forEachIndexed { index, constructor ->
                hookRuntime.hook(constructor, "boomingmusic.session.MediaSession#ctor$index") {
                    after {
                        (instanceOrNull as? MediaSession)?.let(sessions::onConstructed)
                    }
                }
            }

            hookRuntime.hook(
                sessionType.getDeclaredMethod("setMetadata", MediaMetadata::class.java),
                "boomingmusic.session.MediaSession#setMetadata"
            ) {
                before {
                    if (sessions.isModuleWrite()) return@before
                    val session = instanceOrNull as? MediaSession ?: return@before
                    val incoming = args.getOrNull(0) as? MediaMetadata ?: return@before
                    // lyricInfo is appended into the host's own object; args[0] is never replaced.
                    runCatching { onHostMetadata(session, incoming) }
                }
            }

            hookRuntime.hook(
                sessionType.getDeclaredMethod("setPlaybackState", PlaybackState::class.java),
                "boomingmusic.session.MediaSession#setPlaybackState"
            ) {
                before {
                    val session = instanceOrNull as? MediaSession ?: return@before
                    val state = (args.getOrNull(0) as? PlaybackState)?.state ?: PlaybackState.STATE_NONE
                    sessions.onPlaybackState(session, state)
                    runCatching { publishPending() }
                }
            }

            hookRuntime.hook(
                sessionType.getDeclaredMethod("setActive", Boolean::class.javaPrimitiveType),
                "boomingmusic.session.MediaSession#setActive"
            ) {
                before {
                    val session = instanceOrNull as? MediaSession ?: return@before
                    sessions.onActive(session, args.getOrNull(0) as? Boolean ?: false)
                }
            }

            hookRuntime.hook(
                sessionType.getDeclaredMethod("release"),
                "boomingmusic.session.MediaSession#release"
            ) {
                before {
                    (instanceOrNull as? MediaSession)?.let(sessions::onReleased)
                }
            }

            StructuredDiagnostics.logInfo(
                DiagnosticEvent(
                    component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                    area = "hook",
                    event = "SESSION_HOOK_INSTALLED",
                    process = hostPackage,
                    reason = "android.media.session.MediaSession"
                )
            )
        }.onFailure { logFailure("hook", "SESSION_HOOK_FAILED", it) }
    }

    private fun installApplicationHooks() {
        runCatching {
            hookRuntime.hook(
                Application::class.java.getDeclaredMethod("onTerminate"),
                "boomingmusic.app.Application#onTerminate"
            ) {
                after {
                    loadJob?.cancel()
                    loadScope.cancel()
                }
            }
        }.onFailure { logFailure("hook", "APP_TERMINATE_HOOK_FAILED", it) }
    }

    private fun onHostMetadata(session: MediaSession, incoming: MediaMetadata) {
        val track = BoomingMusicTrackIdentityResolver
            .trackFrom(BoomingMusicMetadataReader.snapshotOf(incoming))
            ?: return
        val generation = generationPolicy.onTrackObserved(track.identity)
        sessions.onHostMetadata(session, track.identity, incoming)

        if (attachPendingToIncoming(incoming, track)) return

        val cached = loader.cached(track.stableKey)
        if (cached != null) {
            val result = BoomingMusicLyricPublisher.attachInPlace(
                metadata = incoming,
                track = track.identity,
                document = cached,
                generation = generation,
                generationPolicy = generationPolicy,
                hostPackage = hostPackage
            )
            logPublication(result.name, generation, "host-metadata")
            return
        }

        startLyricLoadIfNeeded(track, generation)
    }

    /** Appends a payload that finished loading earlier into the metadata the host publishes now. */
    private fun attachPendingToIncoming(incoming: MediaMetadata, track: BoomingTrack): Boolean {
        val pending = pendingStore.peek() ?: return false
        if (!generationPolicy.isGenerationValid(pending.generation)) {
            pendingStore.clear()
            logPublication("STALE", pending.generation, "pending")
            return false
        }
        if (!TrackIdentityPolicy.isSameTrack(pending.track, track.identity)) return false

        val result = BoomingMusicLyricPublisher.attachInPlace(
            metadata = incoming,
            track = pending.track,
            document = pending.document,
            generation = pending.generation,
            generationPolicy = generationPolicy,
            hostPackage = hostPackage
        )
        if (result.isPublished && pendingStore.takeIfSame(pending)) {
            logPublication(result.name, pending.generation, "pending")
            return true
        }
        return false
    }

    private fun startLyricLoadIfNeeded(track: BoomingTrack, generation: Long) {
        if (!generationPolicy.isGenerationValid(generation)) return
        if (!loadGate.shouldStart(track.stableKey, generation)) return

        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "track",
                event = "TRACK_BOUND",
                generation = generation,
                trackHash = DiagnosticHasher.sha256(track.stableKey),
                durationMs = track.identity.durationMs
            )
        )

        synchronized(loadLock) {
            loadJob?.cancel()
            loadJob = loadScope.launch {
                StructuredDiagnostics.logDebug(
                    DiagnosticEvent(
                        component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                        area = "lyric",
                        event = "LYRIC_LOAD_ENTERED",
                        generation = generation,
                        reason = "songId=${track.songId != null}"
                    )
                )
                val document = try {
                    loader.load(track)
                } catch (error: CancellationException) {
                    logPublication("LOAD_CANCELLED", generation, "load")
                    throw error
                } catch (error: Throwable) {
                    logFailure("lyric", "LYRIC_LOAD_FAILED", error)
                    null
                }
                // Publication stays on this worker: the host main looper belongs to the player, and
                // the platform session API is safe to drive from any thread.
                publishLoadedLyrics(track, generation, document)
            }
        }
    }

    private fun publishLoadedLyrics(track: BoomingTrack, generation: Long, document: BoomingLyricDocument?) {
        StructuredDiagnostics.logDebug(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "lyric",
                event = "LYRIC_LOAD_RESULT_ENTERED",
                generation = generation,
                reason = "document=${document != null}"
            )
        )
        if (!generationPolicy.isGenerationValid(generation) ||
            !TrackIdentityPolicy.isSameTrack(generationPolicy.currentTrack, track.identity)
        ) {
            logPublication("STALE", generation, "load")
            return
        }
        if (document == null) {
            logPublication("NO_LYRIC", generation, "load")
            return
        }
        val pending = BoomingPendingPublication(track.identity, generation, document)
        pendingStore.replace(pending)
        publishPending()
    }

    /** Commits a pending payload through the session's live metadata. */
    private fun publishPending() {
        val pending = pendingStore.peek() ?: return
        if (!generationPolicy.isGenerationValid(pending.generation) ||
            !TrackIdentityPolicy.isSameTrack(generationPolicy.currentTrack, pending.track)
        ) {
            pendingStore.clear()
            logPublication("STALE", pending.generation, "live")
            return
        }
        val session = sessions.selectUnique(pending.track) ?: return

        val result = BoomingMusicLyricPublisher.publishLive(
            session = session,
            track = pending.track,
            document = pending.document,
            generation = pending.generation,
            generationPolicy = generationPolicy,
            registry = sessions,
            hostPackage = hostPackage
        )
        when {
            result.isPublished -> pendingStore.takeIfSame(pending)
            result == NativeLyricInfoPublisher.Result.STALE_GENERATION -> pendingStore.takeIfSame(pending)
        }
        logPublication(result.name, pending.generation, "live")
    }

    private fun logPublication(result: String, generation: Long, source: String) =
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "publication",
                event = "BOOMINGMUSIC_RESULT_$result",
                generation = generation,
                reason = source
            )
        )

    private fun logFailure(area: String, event: String, error: Throwable) = StructuredDiagnostics.logError(
        DiagnosticEvent(
            component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
            area = area,
            event = event,
            process = hostPackage,
            reason = error.javaClass.simpleName,
            message = error.message?.take(500)
        ),
        throwable = error
    )
}
