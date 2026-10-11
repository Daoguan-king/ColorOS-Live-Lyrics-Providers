/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoomingMusicLyricFilePolicyTest {

    private val baseNames = BoomingMusicLyricFilePolicy.baseNames(
        audioFileNameWithoutExtension = "Song",
        artist = "Artist",
        title = "Title"
    )

    @Test
    fun buildsHostBaseNames() {
        assertEquals(listOf("Song", "Artist - Title"), baseNames)
        assertEquals(
            listOf("Song"),
            BoomingMusicLyricFilePolicy.baseNames("Song", artist = null, title = null)
        )
        assertEquals(
            listOf("Song"),
            BoomingMusicLyricFilePolicy.baseNames("Song", artist = "Artist", title = " ")
        )
    }

    @Test
    fun matchesHostNamingPatternCaseInsensitively() {
        assertEquals("lrc", BoomingMusicLyricFilePolicy.formatOf("Song.lrc", baseNames))
        assertEquals("ttml", BoomingMusicLyricFilePolicy.formatOf("song.TTML", baseNames))
        assertEquals("lrc", BoomingMusicLyricFilePolicy.formatOf("Song - live.lrc", baseNames))
        assertEquals("lrc", BoomingMusicLyricFilePolicy.formatOf("Artist - Title.lrc", baseNames))
        assertEquals(null, BoomingMusicLyricFilePolicy.formatOf("Other.txt", baseNames))
        assertEquals(null, BoomingMusicLyricFilePolicy.formatOf("Other.lrc", baseNames))
    }

    @Test
    fun preferredFormatWinsOverListingOrder() {
        val candidates = BoomingMusicLyricFilePolicy.candidates(
            fileNames = listOf("Song.lrc", "Song.ttml"),
            baseNames = baseNames,
            preferredFormat = "ttml"
        )
        assertEquals(listOf("Song.ttml", "Song.lrc"), candidates.map { it.fileName })
        assertEquals(listOf("ttml", "lrc"), candidates.map { it.format })
    }

    @Test
    fun fallsBackToTheFirstListedMatchWithoutAPreferredFile() {
        val candidates = BoomingMusicLyricFilePolicy.candidates(
            fileNames = listOf("Song.lrc", "Artist - Title.ttml"),
            baseNames = baseNames,
            preferredFormat = "ttml"
        )
        assertEquals(listOf("Artist - Title.ttml", "Song.lrc"), candidates.map { it.fileName })
    }

    @Test
    fun ignoresUnrelatedFiles() {
        assertTrue(
            BoomingMusicLyricFilePolicy.candidates(
                fileNames = listOf("cover.jpg", "Other.lrc"),
                baseNames = baseNames,
                preferredFormat = null
            ).isEmpty()
        )
    }
}
