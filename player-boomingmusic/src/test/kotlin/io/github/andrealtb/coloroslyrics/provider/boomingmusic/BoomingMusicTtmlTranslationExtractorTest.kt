/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoomingMusicTtmlTranslationExtractorTest {

    @Test
    fun collectsInlineTranslationSpansPerParagraph() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="00:10.000" end="00:12.000">
                  <span begin="00:10.000" end="00:11.000">Hello</span>
                  <span ttm:role="x-translation">你好</span>
                </p>
                <p begin="00:14.500" end="00:16.000">
                  <span ttm:role="x-translation">世界</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val translations = BoomingMusicTtmlTranslationExtractor.extract(ttml)
        assertEquals(2, translations.size)
        assertEquals("你好", translations[10_000L])
        assertEquals("世界", translations[14_500L])
    }

    @Test
    fun ignoresRomanisationAndBackgroundRoles() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="00:10.000">
                <span ttm:role="x-roman">ni hao</span>
                <span ttm:role="x-bg">oh</span>
              </p></div></body>
            </tt>
        """.trimIndent()

        assertTrue(BoomingMusicTtmlTranslationExtractor.extract(ttml).isEmpty())
    }

    @Test
    fun appliesTheAppleLyricOffset() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head><audio lyricOffset="1.5"/></head>
              <body><div><p begin="00:10.000">
                <span ttm:role="x-translation">你好</span>
              </p></div></body>
            </tt>
        """.trimIndent()

        assertEquals("你好", BoomingMusicTtmlTranslationExtractor.extract(ttml)[11_500L])
    }

    @Test
    fun fallsBackToTheFirstSpanBeginLikeTheSharedParser() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p>
                <span begin="00:20.000" end="00:21.000">Hello</span>
                <span ttm:role="x-translation">你好</span>
              </p></div></body>
            </tt>
        """.trimIndent()

        assertEquals("你好", BoomingMusicTtmlTranslationExtractor.extract(ttml)[20_000L])
    }

    @Test
    fun malformedInputFailsOpen() {
        assertTrue(BoomingMusicTtmlTranslationExtractor.extract("not xml at all").isEmpty())
        assertTrue(BoomingMusicTtmlTranslationExtractor.extract(null).isEmpty())
        assertTrue(
            BoomingMusicTtmlTranslationExtractor
                .extract("""<!DOCTYPE tt [<!ENTITY a "boom">]><tt><body/></tt>""")
                .isEmpty()
        )
    }
}
