/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes a local lyric sidecar.
 *
 * The host prefers UTF-8 (`force_utf8_encoding_for_lyrics`, default on) and otherwise runs charset
 * detection. This Provider keeps the UTF-8 preference and, without pulling a detector dependency,
 * falls back to strict UTF-8, then GB18030/GBK (the common legacy Chinese lyric encoding), then
 * Latin-1 as the last resort.
 */
object BoomingMusicLyricTextDecoder {

    private val FALLBACK_CHARSETS = listOf("GB18030", "GBK")

    fun decode(bytes: ByteArray, forceUtf8: Boolean): String {
        if (bytes.isEmpty()) return ""
        if (forceUtf8) return stripBom(String(bytes, Charsets.UTF_8))
        return decodeDetected(bytes)
    }

    private fun decodeDetected(bytes: ByteArray): String {
        readBomCharset(bytes)?.let { (charset, offset) ->
            return stripBom(String(bytes, offset, bytes.size - offset, charset))
        }
        decodeStrict(bytes, Charsets.UTF_8)?.let { return stripBom(it) }
        for (name in FALLBACK_CHARSETS) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            decodeStrict(bytes, charset)?.let { return stripBom(it) }
        }
        return stripBom(String(bytes, Charsets.ISO_8859_1))
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun readBomCharset(bytes: ByteArray): Pair<Charset, Int>? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            Charsets.UTF_8 to 3

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            Charsets.UTF_16LE to 2

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            Charsets.UTF_16BE to 2

        else -> null
    }

    private fun stripBom(text: String): String = text.trimStart('\uFEFF')
}
