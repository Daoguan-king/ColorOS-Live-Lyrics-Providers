/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity

/** A decoded document that could not be published yet, bound to the generation that produced it. */
data class BoomingPendingPublication(
    val track: TrackIdentity,
    val generation: Long,
    val document: BoomingLyricDocument
)

/** Holds at most one pending publication; a newer track always replaces an older payload. */
class BoomingMusicPendingPublicationStore {

    private var pending: BoomingPendingPublication? = null

    @Synchronized
    fun replace(value: BoomingPendingPublication): BoomingPendingPublication? {
        val previous = pending
        pending = value
        return previous
    }

    @Synchronized
    fun peek(): BoomingPendingPublication? = pending

    @Synchronized
    fun clear() {
        pending = null
    }

    /** Clears the slot only when it still holds [value], so a concurrent replace is never lost. */
    @Synchronized
    fun takeIfSame(value: BoomingPendingPublication): Boolean {
        if (pending !== value) return false
        pending = null
        return true
    }
}
