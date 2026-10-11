/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

/**
 * Single-flight gate for lyric loading.
 *
 * A host track publishes metadata several times (identity completion, artwork, state sync), so the
 * same (track, generation) pair must start at most one load. Real track changes mint a new
 * generation and are allowed through again, which also lets a track whose lyrics were downloaded
 * after the first attempt be retried on the next play.
 */
class BoomingMusicLyricLoadGate(private val trackedAttempts: Int = TRACKED_ATTEMPTS) {

    private val attempts = LinkedHashMap<String, Boolean>()

    @Synchronized
    fun shouldStart(stableKey: String?, generation: Long): Boolean {
        if (stableKey.isNullOrBlank() || generation <= 0L) return false
        val key = "$stableKey#$generation"
        if (attempts.containsKey(key)) return false
        attempts[key] = true
        while (attempts.size > trackedAttempts) {
            val oldest = attempts.keys.firstOrNull() ?: break
            attempts.remove(oldest)
        }
        return true
    }

    @Synchronized
    fun reset() {
        attempts.clear()
    }

    private companion object {
        const val TRACKED_ATTEMPTS = 12
    }
}
