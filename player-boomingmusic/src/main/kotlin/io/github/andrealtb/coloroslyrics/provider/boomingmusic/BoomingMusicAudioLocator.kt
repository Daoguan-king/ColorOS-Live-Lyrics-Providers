/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlin.math.abs

/** Where the playing song lives on disk, plus the MediaStore row it was resolved from. */
data class BoomingLocatedAudio(
    val filePath: String?,
    val contentUri: Uri?,
    val songId: Long?
)

/**
 * Resolves the audio file of the playing track with framework APIs only.
 *
 * BoomingMusic indexes its library through MediaStore and stores a real file path per song, so a
 * MediaStore lookup returns the same path the host itself uses for sidecar lyric discovery. The
 * host never exposes its path through the platform media session, which is why this lookup exists.
 */
object BoomingMusicAudioLocator {

    private const val DURATION_TOLERANCE_MS = 3_000L

    fun locate(context: Context, track: BoomingTrack): BoomingLocatedAudio? {
        val resolver = context.contentResolver
        val mediaUri = parseMediaUri(track.mediaUri)
        if (mediaUri?.scheme.equals("file", ignoreCase = true)) {
            mediaUri?.path?.let { return BoomingLocatedAudio(it, null, track.songId) }
        }

        val rowId = track.songId ?: mediaUri?.lastPathSegment?.toLongOrNull()
        if (rowId != null) {
            queryPath(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "$COLUMN_ID = ?", arrayOf(rowId.toString()))
                ?.let { return BoomingLocatedAudio(it, mediaUri, rowId) }
        }

        if (mediaUri != null && mediaUri.scheme.equals("content", ignoreCase = true)) {
            queryPath(resolver, mediaUri, null, null)?.let { return BoomingLocatedAudio(it, mediaUri, rowId) }
        }

        locateByMetadata(resolver, track)?.let { return it }
        return mediaUri?.let { BoomingLocatedAudio(null, it, rowId) }
    }

    private fun parseMediaUri(value: String?): Uri? =
        value?.let { runCatching { it.toUri() }.getOrNull() }

    private fun locateByMetadata(resolver: ContentResolver, track: BoomingTrack): BoomingLocatedAudio? {
        val title = track.identity.title ?: return null
        val selection = StringBuilder("$COLUMN_TITLE = ?")
        val arguments = mutableListOf(title)
        track.identity.artist?.let {
            selection.append(" AND $COLUMN_ARTIST = ?")
            arguments += it
        }
        val rows = queryRows(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection.toString(), arguments.toTypedArray())
        if (rows.isEmpty()) return null
        val expected = track.identity.durationMs
        val best = rows.firstOrNull { expected > 0L && abs(it.durationMs - expected) <= DURATION_TOLERANCE_MS }
            ?: rows.firstOrNull()
        return best?.let { BoomingLocatedAudio(it.filePath, null, it.id) }
    }

    private fun queryPath(
        resolver: ContentResolver,
        uri: Uri,
        selection: String?,
        arguments: Array<String>?
    ): String? = queryRows(resolver, uri, selection, arguments).firstOrNull()?.filePath

    private data class AudioRow(val id: Long, val filePath: String?, val durationMs: Long)

    private fun queryRows(
        resolver: ContentResolver,
        uri: Uri,
        selection: String?,
        arguments: Array<String>?
    ): List<AudioRow> = runCatching {
        resolver.query(uri, PROJECTION, selection, arguments, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(COLUMN_ID)
            val pathIndex = cursor.getColumnIndex(COLUMN_DATA)
            val durationIndex = cursor.getColumnIndex(COLUMN_DURATION)
            val rows = mutableListOf<AudioRow>()
            while (cursor.moveToNext() && rows.size < MAX_ROWS) {
                val path = if (pathIndex >= 0) cursor.getString(pathIndex) else null
                rows += AudioRow(
                    id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                    filePath = path?.takeIf { it.isNotBlank() },
                    durationMs = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L
                )
            }
            rows
        }.orEmpty()
    }.getOrDefault(emptyList())

    private const val MAX_ROWS = 8
    private const val COLUMN_ID = MediaStore.Audio.Media._ID
    private const val COLUMN_DATA = MediaStore.Audio.Media.DATA
    private const val COLUMN_TITLE = MediaStore.Audio.Media.TITLE
    private const val COLUMN_ARTIST = MediaStore.Audio.Media.ARTIST
    private const val COLUMN_DURATION = MediaStore.Audio.Media.DURATION

    private val PROJECTION = arrayOf(COLUMN_ID, COLUMN_DATA, COLUMN_DURATION)
}
