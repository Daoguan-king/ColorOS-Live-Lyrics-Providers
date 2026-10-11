/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoomingMusicLyricTextDecoderTest {

    @Test
    fun forcedUtf8IsUsedAsTheHostDefaultsToIt() {
        val bytes = "[00:01.000]你好".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.000]你好", BoomingMusicLyricTextDecoder.decode(bytes, forceUtf8 = true))
    }

    @Test
    fun utf8ByteOrderMarkIsRemoved() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.000]Hi".toByteArray()
        assertEquals("[00:01.000]Hi", BoomingMusicLyricTextDecoder.decode(bytes, forceUtf8 = true))
    }

    @Test
    fun invalidUtf8FallsBackToALegacyChineseEncoding() {
        val bytes = "[00:01.000]你好".toByteArray(charset("GB18030"))
        assertEquals("[00:01.000]你好", BoomingMusicLyricTextDecoder.decode(bytes, forceUtf8 = false))
    }

    @Test
    fun utf8PayloadStaysUtf8WhenDetectionIsEnabled() {
        val bytes = "[00:01.000]café".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.000]café", BoomingMusicLyricTextDecoder.decode(bytes, forceUtf8 = false))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertTrue(BoomingMusicLyricTextDecoder.decode(ByteArray(0), forceUtf8 = false).isEmpty())
    }
}
