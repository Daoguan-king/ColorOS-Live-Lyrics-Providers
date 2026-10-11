/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Third host source: lyrics the user downloaded in-app, stored by Room in the host database.
 *
 * The row is keyed by the host's own song id, which is what the player publishes as the media id.
 * The database is opened read-only and never mutated; when the live file cannot be opened (a
 * foreign journal mode, a busy database), a private copy is queried instead.
 */
class BoomingMusicStoredLyricSource(private val environment: BoomingMusicHostEnvironment) {

    fun read(songId: Long?): BoomingRawLyric? {
        val id = songId?.takeIf { it > 0L } ?: return null
        val databaseFile = environment.lyricsDatabaseFile() ?: return null

        when (val outcome = query(databaseFile, id)) {
            is Outcome.Failed -> Unit
            else -> return outcome.lyricOrNull()
        }

        val snapshot = environment.snapshotLyricsDatabase() ?: return null
        return try {
            when (val outcome = query(snapshot, id)) {
                is Outcome.Failed -> null
                else -> outcome.lyricOrNull()
            }
        } finally {
            environment.discardLyricsDatabaseSnapshot(snapshot)
        }
    }

    private sealed interface Outcome {
        data class Row(val lyric: BoomingRawLyric?) : Outcome
        data object NoRow : Outcome
        data object Failed : Outcome

        fun lyricOrNull(): BoomingRawLyric? = (this as? Row)?.lyric
    }

    private fun query(databaseFile: File, songId: Long): Outcome = runCatching {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery(SELECT_LYRICS, arrayOf(songId.toString())).use { cursor ->
                if (!cursor.moveToFirst()) return@use Outcome.NoRow
                val instrumental = cursor.getInt(INSTRUMENTAL_INDEX) != 0
                if (instrumental) return@use Outcome.Row(null)
                val lyrics = cursor.getString(LYRICS_INDEX)
                if (lyrics.isNullOrBlank()) {
                    Outcome.Row(null)
                } else {
                    Outcome.Row(
                        BoomingRawLyric(
                            text = lyrics,
                            sourceLabel = BoomingMusicPlayerConstants.SOURCE_STORED,
                            providerLabel = cursor.getString(PROVIDER_INDEX)
                        )
                    )
                }
            }
        }
    }.getOrElse { Outcome.Failed }

    private companion object {
        const val LYRICS_INDEX = 0
        const val PROVIDER_INDEX = 1
        const val INSTRUMENTAL_INDEX = 2

        val SELECT_LYRICS = buildString {
            append("SELECT ")
            append(BoomingMusicPlayerConstants.HOST_LYRICS_COLUMN_LYRICS).append(", ")
            append(BoomingMusicPlayerConstants.HOST_LYRICS_COLUMN_PROVIDER).append(", ")
            append(BoomingMusicPlayerConstants.HOST_LYRICS_COLUMN_INSTRUMENTAL)
            append(" FROM ").append(BoomingMusicPlayerConstants.HOST_LYRICS_TABLE)
            append(" WHERE ").append(BoomingMusicPlayerConstants.HOST_LYRICS_COLUMN_ID).append(" = ? LIMIT 1")
        }
    }
}
