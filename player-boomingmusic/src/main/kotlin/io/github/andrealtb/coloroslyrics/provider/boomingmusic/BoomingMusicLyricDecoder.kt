/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.model.LyricTimingType
import io.github.andrealtb.coloroslyrics.provider.core.policy.LyricLaneAlignmentPolicy
import io.github.andrealtb.coloroslyrics.provider.core.policy.LyricTimingClassifier
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.EnhanceLrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.LrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.ttml.TtmlParser

/** One raw lyric payload read from a host source, still in its original text form. */
data class BoomingRawLyric(
    val text: String,
    val sourceLabel: String,
    val providerLabel: String? = null
)

/** A decoded, lane-aligned lyric document ready for `lyricInfo` publication. */
data class BoomingLyricDocument(
    val lines: List<RichLyricLine>,
    val timing: LyricTimingType,
    val sourceLabel: String,
    val providerLabel: String? = null
) {
    val hasTimedLines: Boolean
        get() = timing == LyricTimingType.LINE || timing == LyricTimingType.WORD
}

/**
 * Neutral decoding of a host lyric payload into [RichLyricLine].
 *
 * The host owns two formats: TTML (its preferred format, carrying word timing and translation) and
 * LRC, which may be the enhanced word-timed variant or plain. Untimed text is rejected instead of
 * being published, because `lyricInfo` without a timeline cannot drive the lock screen.
 */
object BoomingMusicLyricDecoder {

    private val TTML_ROOT_REGEX = Regex("""<(?:[A-Za-z0-9_.-]+:)?(?:tt|ttml)[\s>:]""")

    fun decode(raw: BoomingRawLyric, trackLengthMs: Long): BoomingLyricDocument? {
        val text = raw.text
        if (text.isBlank()) return null

        val candidates = when {
            looksLikeTtml(text) -> listOfNotNull(parseTtml(text, attachTranslations = true)) + decodeLrc(text, trackLengthMs)
            else -> decodeLrc(text, trackLengthMs) + listOfNotNull(parseTtml(text))
        }

        for (lines in candidates) {
            val aligned = alignLanes(lines)
            if (aligned.isEmpty()) continue
            val timing = LyricTimingClassifier.classify(text, aligned)
            if (timing != LyricTimingType.LINE && timing != LyricTimingType.WORD) continue
            return BoomingLyricDocument(
                lines = aligned,
                timing = timing,
                sourceLabel = raw.sourceLabel,
                providerLabel = raw.providerLabel
            )
        }
        return null
    }

    private fun parseTtml(text: String, attachTranslations: Boolean = false): List<RichLyricLine>? =
        runCatching {
            val lines = TtmlParser.parse(text)?.lines?.takeIf { it.isNotEmpty() } ?: return@runCatching null
            if (attachTranslations) attachTtmlTranslations(lines, text) else lines
        }.getOrNull()

    /** Moves `x-translation` spans onto the paragraph they annotate as the secondary lane. */
    private fun attachTtmlTranslations(lines: List<RichLyricLine>, ttml: String): List<RichLyricLine> {
        val translations = BoomingMusicTtmlTranslationExtractor.extract(ttml)
        if (translations.isEmpty()) return lines
        return lines.map { line ->
            if (!line.secondary.isNullOrBlank()) {
                line
            } else {
                translations[line.begin]?.let { translation -> line.copy(secondary = translation) } ?: line
            }
        }
    }

    fun looksLikeTtml(text: String): Boolean {
        val head = text.trimStart()
        if (head.startsWith("<?xml")) return true
        return TTML_ROOT_REGEX.containsMatchIn(head.take(TTML_SNIFF_CHARS))
    }

    private fun decodeLrc(text: String, trackLengthMs: Long): List<List<RichLyricLine>> = listOfNotNull(
        runCatching { EnhanceLrcParser.parse(text, trackLengthMs).lines.takeIf { it.isNotEmpty() } }.getOrNull(),
        runCatching {
            LrcParser.parse(text, trackLengthMs).lines
                .map { line ->
                    RichLyricLine(
                        begin = line.begin,
                        end = line.end,
                        duration = line.duration,
                        text = line.text
                    )
                }
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    )

    /**
     * Normalizes the decoded lines.
     *
     * The shared lane policy is used when the payload has no translation lane of its own: it drops
     * promotional rows and enforces monotonic timing. A payload that already carries translations
     * keeps its lanes untouched instead, because the policy matches a translation to the nearest
     * line start within its tolerance and would move a single translated line onto a neighbouring
     * untranslated one.
     */
    private fun alignLanes(lines: List<RichLyricLine>): List<RichLyricLine> {
        if (lines.isEmpty()) return lines
        if (lines.none { !it.secondary.isNullOrBlank() }) {
            return LyricLaneAlignmentPolicy.align(lines, emptyList(), filterPromo = true)
        }
        var lastBegin = 0L
        return lines.sortedBy { it.begin }.map { line ->
            val begin = line.begin.coerceAtLeast(lastBegin)
            lastBegin = begin
            val end = line.end.coerceAtLeast(begin)
            line.copy(begin = begin, end = end, duration = end - begin)
        }
    }

    private const val TTML_SNIFF_CHARS = 2_048
}
