/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BoomingMusicTrackIdentityResolverTest {

    @Test
    fun usesHostMediaIdAsTheOnlyAnchor() {
        val track = assertNotNull(
            BoomingMusicTrackIdentityResolver.trackFrom(
                BoomingMetadataSnapshot(
                    mediaId = "1421",
                    title = "Title",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 210_000L,
                    mediaUri = "content://media/external/audio/media/1421"
                )
            )
        )
        assertEquals("1421", track.identity.id)
        assertEquals(1421L, track.songId)
        assertEquals("content://media/external/audio/media/1421", track.mediaUri)
        assertEquals("1421|title|artist|210", track.stableKey)
    }

    @Test
    fun rejectsSnapshotsWithoutIdentity() {
        assertNull(BoomingMusicTrackIdentityResolver.trackFrom(BoomingMetadataSnapshot()))
        assertNull(
            BoomingMusicTrackIdentityResolver.trackFrom(
                BoomingMetadataSnapshot(mediaId = "  ", title = " ", artist = "")
            )
        )
    }

    @Test
    fun acceptsOnlyPositiveDecimalSongIds() {
        assertEquals(7L, BoomingMusicTrackIdentityResolver.songIdOf("7"))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf(null))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf(""))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf("0"))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf("-3"))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf("track-42"))
        assertNull(BoomingMusicTrackIdentityResolver.songIdOf("1234567890123456789"))
    }

    @Test
    fun keepsIdentityWhenTheMediaIdIsNotANumericRowId() {
        val track = assertNotNull(
            BoomingMusicTrackIdentityResolver.trackFrom(
                BoomingMetadataSnapshot(title = "Only Title", artist = "Only Artist")
            )
        )
        assertNull(track.identity.id)
        assertNull(track.songId)
        assertEquals("noid|only title|only artist|0", track.stableKey)
    }
}
