/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import java.lang.ref.WeakReference

/**
 * Tracks the platform media sessions of the host process.
 *
 * BoomingMusic creates one session for playback (a media3 `MediaLibrarySession`), but helper
 * sessions are possible, so selection prefers the session that is active, reports a real playback
 * state and was last bound to the same track. Sessions are held weakly: the module must not keep a
 * released session alive.
 */
class BoomingMusicMediaSessionRegistry {

    private data class Entry(
        val session: WeakReference<MediaSession>,
        var track: TrackIdentity? = null,
        var hostMetadata: MediaMetadata? = null,
        var playbackState: Int = PlaybackState.STATE_NONE,
        var active: Boolean = false,
        var released: Boolean = false
    )

    private val entries = mutableListOf<Entry>()
    private val moduleWrite = ThreadLocal<Boolean>()

    @Synchronized
    fun onConstructed(session: MediaSession) {
        prune()
        if (entry(session) == null) entries += Entry(WeakReference(session))
    }

    @Synchronized
    fun onHostMetadata(session: MediaSession, track: TrackIdentity?, metadata: MediaMetadata?) {
        ensure(session).apply {
            this.track = track?.takeUnless { it.isBlank }
            this.hostMetadata = metadata
        }
    }

    @Synchronized
    fun onPlaybackState(session: MediaSession, state: Int) {
        ensure(session).playbackState = state
    }

    @Synchronized
    fun onActive(session: MediaSession, active: Boolean) {
        ensure(session).active = active
    }

    @Synchronized
    fun onReleased(session: MediaSession) {
        entry(session)?.released = true
    }

    /** The single live session currently bound to [track], or the only live session left. */
    @Synchronized
    fun selectUnique(track: TrackIdentity): MediaSession? {
        prune()
        val trackMatches = candidates().filter { TrackIdentityPolicy.isSameTrack(it.track, track) }
        val selected = trackMatches.firstOrNull { it.active && isPlaybackStateValid(it.playbackState) }
            ?: trackMatches.singleOrNull()
        selected?.session?.get()?.let { return it }
        return uniqueLiveSessionLocked()?.takeIf { session ->
            entry(session)?.track?.let { bound -> TrackIdentityPolicy.isSameTrack(bound, track) } == true
        }
    }

    @Synchronized
    fun uniqueLiveSession(): MediaSession? {
        prune()
        return uniqueLiveSessionLocked()
    }

    @Synchronized
    fun hostTrack(session: MediaSession): TrackIdentity? = entry(session)?.track

    fun isModuleWrite(): Boolean = moduleWrite.get() == true

    /** Marks the current thread as the module writing into the host session, never the host itself. */
    fun <T> withModuleWrite(block: () -> T): T {
        val previous = moduleWrite.get() == true
        moduleWrite.set(true)
        return try {
            block()
        } finally {
            if (previous) moduleWrite.set(true) else moduleWrite.remove()
        }
    }

    @Synchronized
    private fun ensure(session: MediaSession): Entry = entry(session) ?: Entry(
        session = WeakReference(session)
    ).also(entries::add)

    private fun entry(session: MediaSession): Entry? = entries.firstOrNull { it.session.get() === session }

    private fun prune() = entries.removeAll { it.session.get() == null || it.released }

    private fun candidates(): List<Entry> = entries.filter { !it.released && it.session.get() != null }

    private fun uniqueLiveSessionLocked(): MediaSession? {
        val live = candidates()
        val preferred = live.filter { it.active && isPlaybackStateValid(it.playbackState) }
        val preferredSession = preferred.mapNotNull { it.session.get() }.singleOrNull()
        if (preferredSession != null) return preferredSession
        return live.mapNotNull { it.session.get() }.singleOrNull()
    }

    companion object {
        fun isPlaybackStateValid(state: Int): Boolean = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING ||
            state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
    }
}
