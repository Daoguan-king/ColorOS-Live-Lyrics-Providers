/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.content.Context
import android.os.SystemClock
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics

/**
 * Resolves the host lyric payload for one track through the host's own source order
 * (sidecar file, embedded tag, downloaded/stored row) and caches decoded documents per track key.
 *
 * The provider process usually has no lyrics screen open, so the host never decodes these sources
 * itself; reading them here is what makes the lock screen work while the player is in the
 * background. Every source is read lazily, and a source that only yields untimed text does not stop
 * the chain, matching the host screen's own "first synced source wins" behaviour.
 *
 * The host context is used directly (see [BoomingMusicHostEnvironment]): the module is constructed
 * while the host application is still attaching, so no base-context access happens here.
 */
class BoomingMusicLyricLoader(
    context: Context,
    private val environment: BoomingMusicHostEnvironment,
    classLoader: ClassLoader
) {

    private val hostContext: Context = context
    private val sidecarSource = BoomingMusicSidecarLyricSource(environment)
    private val embeddedSource = BoomingMusicEmbeddedLyricSource(classLoader)
    private val storedSource = BoomingMusicStoredLyricSource(environment)

    private val cacheLock = Any()
    private val cache = LinkedHashMap<String, BoomingLyricDocument>()

    fun cached(stableKey: String?): BoomingLyricDocument? {
        val key = stableKey ?: return null
        return synchronized(cacheLock) { cache[key] }
    }

    fun load(track: BoomingTrack): BoomingLyricDocument? {
        cached(track.stableKey)?.let { return it }

        val startedAt = SystemClock.elapsedRealtime()
        val audio = BoomingMusicAudioLocator.locate(hostContext, track)
        val filePath = audio?.filePath
        val songId = audio?.songId ?: track.songId
        val trackLengthMs = track.identity.durationMs
        StructuredDiagnostics.logDebug(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "lyric",
                event = "LYRIC_SOURCE_LOCATED",
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                reason = "path=${filePath != null} songId=${songId != null}"
            )
        )

        for (readSource in sourceReaders(filePath, songId, track)) {
            val raw = runCatching { readSource() }.getOrNull() ?: continue
            val document = BoomingMusicLyricDecoder.decode(raw, trackLengthMs) ?: continue
            StructuredDiagnostics.logDebug(
                DiagnosticEvent(
                    component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                    area = "lyric",
                    event = "LYRIC_SOURCE_HIT",
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    reason = raw.sourceLabel
                )
            )
            remember(track.stableKey, document)
            return document
        }
        StructuredDiagnostics.logDebug(
            DiagnosticEvent(
                component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
                area = "lyric",
                event = "LYRIC_SOURCE_MISS",
                durationMs = SystemClock.elapsedRealtime() - startedAt
            )
        )
        return null
    }

    private fun sourceReaders(
        filePath: String?,
        songId: Long?,
        track: BoomingTrack
    ): List<() -> BoomingRawLyric?> = buildList {
        if (filePath != null) {
            add { sidecarSource.read(filePath, track) }
            add { embeddedSource.read(filePath) }
        }
        add { storedSource.read(songId) }
    }

    private fun remember(stableKey: String, document: BoomingLyricDocument) {
        synchronized(cacheLock) {
            cache[stableKey] = document
            while (cache.size > MAX_CACHED_DOCUMENTS) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
    }

    private companion object {
        const val MAX_CACHED_DOCUMENTS = 16
    }
}
