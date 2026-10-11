/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import java.util.regex.Pattern

/**
 * Sidecar lyric discovery, mirroring the host's own rules (RealLyricsRepository.findLyricsFiles and
 * fileLyrics):
 *
 * - a lyrics file lives next to the audio file and matches `.*<base>.*\.(lrc|ttml)` case-insensitively;
 * - `<base>` is the audio file name without extension, or `artist - title`;
 * - the host's `preferred_lyrics_file_format` (default `ttml`) is returned first when present,
 *   otherwise directory listing order decides.
 */
object BoomingMusicLyricFilePolicy {

    data class Candidate(val fileName: String, val format: String)

    fun baseNames(audioFileNameWithoutExtension: String?, artist: String?, title: String?): List<String> {
        val names = mutableListOf<String>()
        audioFileNameWithoutExtension?.trim()?.takeIf { it.isNotEmpty() }?.let(names::add)
        val artistName = artist?.trim().orEmpty()
        val songTitle = title?.trim().orEmpty()
        if (artistName.isNotEmpty() && songTitle.isNotEmpty()) {
            names += "$artistName - $songTitle"
        }
        return names
    }

    fun formatOf(fileName: String, baseNames: List<String>): String? {
        if (baseNames.isEmpty()) return null
        val patterns = baseNames.map { base -> Regex(".*${Pattern.quote(base)}.*\\.(lrc|ttml)", RegexOption.IGNORE_CASE) }
        val matched = patterns.firstOrNull { it.matches(fileName) } ?: return null
        return matched.find(fileName)?.groupValues?.getOrNull(1)?.lowercase()
    }

    /**
     * Orders candidates the way the host resolves them: the first file that already matches the
     * preferred format wins, otherwise the first listed match does.
     */
    fun candidates(
        fileNames: List<String>,
        baseNames: List<String>,
        preferredFormat: String?
    ): List<Candidate> {
        val preferred = preferredFormat?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val ordered = mutableListOf<Candidate>()
        var preferredCandidate: Candidate? = null
        for (fileName in fileNames) {
            val format = formatOf(fileName, baseNames) ?: continue
            val candidate = Candidate(fileName, format)
            if (preferred != null && format == preferred) {
                if (preferredCandidate == null) preferredCandidate = candidate
            } else {
                ordered += candidate
            }
        }
        return when (val first = preferredCandidate) {
            null -> ordered
            else -> listOf(first) + ordered
        }
    }
}
