/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import java.io.File

/**
 * Highest-priority host source: a `.lrc`/`.ttml` file next to the audio file.
 *
 * File names are sorted before applying the host's matching rules so two extractions of the same
 * folder resolve the same sidecar (the host itself follows raw directory listing order, which the
 * filesystem does not guarantee).
 */
class BoomingMusicSidecarLyricSource(private val environment: BoomingMusicHostEnvironment) {

    fun read(filePath: String, track: BoomingTrack): BoomingRawLyric? {
        val audioFile = File(filePath)
        val parent = audioFile.parentFile ?: return null
        val entries = runCatching { parent.listFiles() }.getOrNull()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
        if (entries == null) {
            logProbe("directory-unreadable")
            return null
        }
        if (entries.isEmpty()) {
            logProbe("directory-empty")
            return null
        }

        val baseNames = BoomingMusicLyricFilePolicy.baseNames(
            audioFileNameWithoutExtension = audioFile.nameWithoutExtension,
            artist = track.identity.artist,
            title = track.identity.title
        )
        val candidates = BoomingMusicLyricFilePolicy.candidates(
            fileNames = entries.map { it.name },
            baseNames = baseNames,
            preferredFormat = environment.preferredLyricsFormat()
        )
        if (candidates.isEmpty()) {
            logProbe(
                "no-candidate files=${entries.size} bases=${baseNames.size} " +
                    "name=${DiagnosticHasher.sha256(audioFile.nameWithoutExtension)}"
            )
            return null
        }
        for (candidate in candidates) {
            val text = readText(File(parent, candidate.fileName))
            if (text.isNullOrBlank()) {
                logProbe("read-failed")
                continue
            }
            logProbe("hit")
            return BoomingRawLyric(
                text = text,
                sourceLabel = BoomingMusicPlayerConstants.SOURCE_SIDECAR,
                providerLabel = candidate.format
            )
        }
        return null
    }

    private fun logProbe(reason: String) = StructuredDiagnostics.logDebug(
        DiagnosticEvent(
            component = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT,
            area = "lyric",
            event = "LYRIC_SIDECAR_PROBE",
            reason = reason
        )
    )

    private fun readText(file: File): String? = runCatching {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_SIDECAR_BYTES) {
            null
        } else {
            BoomingMusicLyricTextDecoder.decode(file.readBytes(), environment.forceUtf8Encoding())
        }
    }.getOrNull()

    private companion object {
        const val MAX_SIDECAR_BYTES = 4L * 1024L * 1024L
    }
}
