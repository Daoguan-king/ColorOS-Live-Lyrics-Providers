/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TTML translation lane.
 *
 * The shared TTML parser intentionally drops `x-translation`/`x-roman`/`x-bg` spans, because a
 * karaoke line must not mix lanes. BoomingMusic renders those spans as the translation of the line
 * they sit in, so this extractor recovers them and the decoder attaches them as the line's
 * secondary lane, which is what `lyricInfo.translationLyric` carries.
 *
 * Timing follows the shared parser exactly: the paragraph `begin` plus the global `<audio
 * lyricOffset>` (seconds) used by Apple-style TTML.
 */
object BoomingMusicTtmlTranslationExtractor {

    private val WHITESPACE = Regex("\\s+")
    private const val ROLE_TRANSLATION = "x-translation"
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

    /** Paragraph start (absolute media ms) to translation text, in document order. */
    fun extract(ttml: String?): Map<Long, String> {
        if (ttml.isNullOrBlank()) return emptyMap()
        return runCatching {
            val document = parseSecurely(ttml)
            val offsetMs = lyricOffsetMillis(document)
            val translations = LinkedHashMap<Long, String>()
            val paragraphs = document.getElementsByTagNameNS("*", "p")
            for (index in 0 until paragraphs.length) {
                val paragraph = paragraphs.item(index) as? Element ?: continue
                val begin = paragraphBegin(paragraph) ?: continue
                val beginMs = applyOffset(parseTime(begin), offsetMs)
                if (beginMs < 0L) continue
                translationOf(paragraph)?.let { translations[beginMs] = it }
            }
            translations
        }.getOrDefault(emptyMap())
    }

    /** Same paragraph start resolution the shared parser uses: `begin`, else the first span begin. */
    private fun paragraphBegin(paragraph: Element): String? {
        paragraph.getAttribute("begin").takeIf { it.isNotEmpty() }?.let { return it }
        val spans = paragraph.getElementsByTagNameNS("*", "span")
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            span.getAttribute("begin").takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun parseSecurely(ttml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
    }

    private fun translationOf(paragraph: Element): String? {
        val spans = paragraph.getElementsByTagNameNS("*", "span")
        val builder = StringBuilder()
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            if (roleOf(span) != ROLE_TRANSLATION) continue
            val text = span.textContent ?: continue
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(text)
        }
        val text = builder.toString().replace(WHITESPACE, " ").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun roleOf(element: Element): String {
        element.getAttributeNS(TTML_METADATA_NS, "role").takeIf { it.isNotEmpty() }?.let { return it }
        element.getAttribute("role").takeIf { it.isNotEmpty() }?.let { return it }
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if (attribute.localName == "role") return attribute.nodeValue.orEmpty()
        }
        return ""
    }

    private fun lyricOffsetMillis(document: Document): Long {
        val audioNodes = document.getElementsByTagNameNS("*", "audio")
        for (index in 0 until audioNodes.length) {
            val audio = audioNodes.item(index) as? Element ?: continue
            val seconds = audio.getAttribute("lyricOffset").toDoubleOrNull() ?: continue
            return (seconds * 1_000.0).toLong()
        }
        return 0L
    }

    private fun applyOffset(timeMs: Long, offsetMs: Long): Long {
        if (timeMs < 0L) return -1L
        return (timeMs + offsetMs).coerceAtLeast(0L)
    }

    private fun parseTime(time: String): Long = runCatching {
        val value = time.trim()
        if (value.isEmpty()) return@runCatching -1L
        if (value.endsWith("ms", ignoreCase = true)) return@runCatching value.dropLast(2).toDouble().toLong()
        if (!value.contains(':')) {
            val multiplier = when {
                value.endsWith("h", ignoreCase = true) -> 3_600_000.0
                value.endsWith("m", ignoreCase = true) -> 60_000.0
                else -> 1_000.0
            }
            val numeric = if (value.lastOrNull()?.isLetter() == true) value.dropLast(1) else value
            return@runCatching (numeric.toDouble() * multiplier).toLong()
        }
        val parts = value.split(":")
        when (parts.size) {
            3 -> {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].toDouble()
                hours * 3_600_000L + minutes * 60_000L + (seconds * 1_000.0).toLong()
            }

            2 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toDouble()
                minutes * 60_000L + (seconds * 1_000.0).toLong()
            }

            1 -> (parts[0].toDouble() * 1_000.0).toLong()
            else -> -1L
        }
    }.getOrDefault(-1L)
}
