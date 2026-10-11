/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.model.LyricTimingType
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.publisher.NativeLyricInfoPublisher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoomingMusicPublicationPolicyTest {

    private val track = TrackIdentity(id = "7", title = "Title", artist = "Artist")

    @Test
    fun loadGateAllowsOneAttemptPerTrackGeneration() {
        val gate = BoomingMusicLyricLoadGate()
        assertTrue(gate.shouldStart("7|title|artist|0", 1L))
        assertFalse(gate.shouldStart("7|title|artist|0", 1L))
        assertTrue(gate.shouldStart("7|title|artist|0", 2L))
        assertFalse(gate.shouldStart(null, 3L))
        assertFalse(gate.shouldStart("7|title|artist|0", 0L))
    }

    @Test
    fun loadGateStaysBoundedForLongSessions() {
        val gate = BoomingMusicLyricLoadGate(trackedAttempts = 2)
        assertTrue(gate.shouldStart("a", 1L))
        assertTrue(gate.shouldStart("b", 1L))
        assertTrue(gate.shouldStart("c", 1L))
        // "a" was evicted, so it may be attempted again instead of the map growing without bound.
        assertTrue(gate.shouldStart("a", 1L))
        assertFalse(gate.shouldStart("c", 1L))
        gate.reset()
        assertTrue(gate.shouldStart("c", 1L))
    }

    @Test
    fun pendingStoreReplacesAndConsumesOnlyTheSameInstance() {
        val store = BoomingMusicPendingPublicationStore()
        val first = pending(1L)
        val second = pending(2L)

        assertNull(store.replace(first))
        assertEquals(first, store.peek())
        assertEquals(first, store.replace(second))
        assertEquals(second, store.peek())

        assertFalse(store.takeIfSame(first))
        assertEquals(second, store.peek())
        assertTrue(store.takeIfSame(second))
        assertNull(store.peek())
    }

    @Test
    fun commitFailureIsReportedOnlyForPublishedPayloads() {
        assertEquals(
            NativeLyricInfoPublisher.Result.COMMIT_FAILED,
            BoomingMusicLyricPublisher.classifyCommit(
                NativeLyricInfoPublisher.Result.PUBLISHED,
                sessionCommitSucceeded = false
            )
        )
        assertEquals(
            NativeLyricInfoPublisher.Result.PUBLISHED,
            BoomingMusicLyricPublisher.classifyCommit(
                NativeLyricInfoPublisher.Result.PUBLISHED,
                sessionCommitSucceeded = true
            )
        )
        assertEquals(
            NativeLyricInfoPublisher.Result.STALE_GENERATION,
            BoomingMusicLyricPublisher.classifyCommit(
                NativeLyricInfoPublisher.Result.STALE_GENERATION,
                sessionCommitSucceeded = true
            )
        )
    }

    @Test
    fun documentTimingDecidesWhetherItCanBePublished() {
        assertTrue(pending(1L).document.hasTimedLines)
        assertFalse(
            pending(1L).document.copy(timing = LyricTimingType.UNTIMED_TEXT).hasTimedLines
        )
    }

    private fun pending(generation: Long) = BoomingPendingPublication(
        track = track,
        generation = generation,
        document = BoomingLyricDocument(
            lines = emptyList(),
            timing = LyricTimingType.LINE,
            sourceLabel = BoomingMusicPlayerConstants.SOURCE_SIDECAR
        )
    )
}
