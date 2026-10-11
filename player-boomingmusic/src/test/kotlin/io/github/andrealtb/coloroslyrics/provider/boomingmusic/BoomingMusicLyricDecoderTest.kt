/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.model.LyricTimingType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoomingMusicLyricDecoderTest {

    @Test
    fun decodesPlainLrcAsLineTimedPayload() {
        val document = assertNotNull(
            decode(
                """
                [00:10.000]First line
                [00:14.500]Second line
                """.trimIndent()
            )
        )
        assertEquals(LyricTimingType.LINE, document.timing)
        assertEquals(2, document.lines.size)
        assertEquals(10_000L, document.lines[0].begin)
        assertEquals(14_500L, document.lines[0].end)
        assertEquals("First line", document.lines[0].text)
        assertEquals(14_500L, document.lines[1].begin)
        assertEquals(60_000L, document.lines[1].end)
    }

    @Test
    fun keepsEnhancedLrcWordTiming() {
        val document = assertNotNull(
            decode("[00:10.000]<00:10.000>Hel<00:10.500>lo\n")
        )
        assertEquals(LyricTimingType.WORD, document.timing)
        val words = assertNotNull(document.lines.single().words)
        assertEquals(listOf("Hel", "lo"), words.map { it.text })
        assertEquals(10_500L, words[1].begin)
    }

    @Test
    fun keepsTranslationFromPairedSameTimeLines() {
        val document = assertNotNull(
            decode(
                """
                [00:10.000]Hello
                [00:10.000]你好
                """.trimIndent()
            )
        )
        assertEquals(1, document.lines.size)
        assertEquals("Hello", document.lines.single().text)
        assertEquals("你好", document.lines.single().secondary)
    }

    @Test
    fun keepsTranslationOnItsOwnLineOnly() {
        val document = assertNotNull(
            decode(
                """
                [00:10.000]Hello
                [00:11.000]World
                [00:11.000]世界
                """.trimIndent()
            )
        )
        assertEquals(2, document.lines.size)
        assertEquals("Hello", document.lines[0].text)
        assertNull(document.lines[0].secondary, "a neighbouring translation must not move onto this line")
        assertEquals("World", document.lines[1].text)
        assertEquals("世界", document.lines[1].secondary)
    }

    @Test
    fun decodesTtmlWithWordTimingAndInlineTranslation() {
        val document = assertNotNull(
            decode(
                """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                     <body><div><p begin="00:10.000" end="00:12.000">
                       <span begin="00:10.000" end="00:11.000">Hello</span>
                       <span ttm:role="x-translation">你好</span>
                     </p></div></body></tt>"""
            )
        )
        assertEquals(LyricTimingType.WORD, document.timing)
        val line = document.lines.single()
        assertEquals(10_000L, line.begin)
        assertEquals("Hello", line.text)
        assertEquals("你好", line.secondary)
    }

    @Test
    fun appliesTheAppleTtmlLyricOffsetToBothLanes() {
        val document = assertNotNull(
            decode(
                """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                     <head><audio lyricOffset="1.5"/></head>
                     <body><div><p begin="00:10.000">
                       <span begin="00:10.000" end="00:11.000">Hello</span>
                       <span ttm:role="x-translation">你好</span>
                     </p></div></body></tt>"""
            )
        )
        assertEquals(11_500L, document.lines.single().begin)
        assertEquals("你好", document.lines.single().secondary)
    }

    @Test
    fun rejectsUntimedText() {
        assertNull(decode("just some words\nwithout any timeline"))
        assertNull(decode("[ar:Artist]\n[ti:Title]"))
        assertNull(decode(""))
    }

    @Test
    fun reportsSourceDiagnostics() {
        val document = assertNotNull(
            BoomingMusicLyricDecoder.decode(
                BoomingRawLyric("[00:01.000]Line", BoomingMusicPlayerConstants.SOURCE_STORED, "lrclib"),
                30_000L
            )
        )
        assertEquals(BoomingMusicPlayerConstants.SOURCE_STORED, document.sourceLabel)
        assertEquals("lrclib", document.providerLabel)
        assertTrue(document.hasTimedLines)
    }

    private fun decode(raw: String): BoomingLyricDocument? = BoomingMusicLyricDecoder.decode(
        BoomingRawLyric(raw, BoomingMusicPlayerConstants.SOURCE_SIDECAR, null),
        TRACK_LENGTH_MS
    )

    private companion object {
        const val TRACK_LENGTH_MS = 60_000L
    }
}
