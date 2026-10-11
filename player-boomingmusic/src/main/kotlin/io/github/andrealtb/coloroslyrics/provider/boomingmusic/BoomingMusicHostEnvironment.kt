/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Host-owned state this Provider reads but never writes: the default preference file the host
 * lyrics pipeline is configured through, and the Room database that stores downloaded lyrics.
 *
 * The provider bootstrap runs inside the `Application#attach` hook, before `attachBaseContext` has
 * bound the base context, so `applicationContext` is still null at construction time. The passed
 * host context (the running Application) is therefore used directly and every base-context call is
 * made lazily, once the host process is up.
 */
class BoomingMusicHostEnvironment(private val hostContext: Context) {

    /** The framework's default preference file name, which is the file the host itself writes. */
    private val preferences: SharedPreferences by lazy {
        hostContext.getSharedPreferences("${hostContext.packageName}_preferences", Context.MODE_PRIVATE)
    }

    /** `preferred_lyrics_file_format` (host default `ttml`). */
    fun preferredLyricsFormat(): String? = readString(BoomingMusicPlayerConstants.PREF_PREFERRED_LYRICS_FORMAT)
        ?: BoomingMusicPlayerConstants.DEFAULT_PREFERRED_LYRICS_FORMAT

    /** `force_utf8_encoding_for_lyrics` (host default on). */
    fun forceUtf8Encoding(): Boolean =
        readBoolean(BoomingMusicPlayerConstants.PREF_FORCE_UTF8_ENCODING, defaultValue = true)

    /** `ignore_blank_lines_in_lyrics` (host default off). */
    fun ignoreBlankLines(): Boolean =
        readBoolean(BoomingMusicPlayerConstants.PREF_IGNORE_BLANK_LINES, defaultValue = false)

    fun lyricsDatabaseFile(): File? = runCatching {
        hostContext.getDatabasePath(BoomingMusicPlayerConstants.HOST_DATABASE_NAME)
    }.getOrNull()?.takeIf { it.isFile }

    /**
     * A private copy of the host database together with its journal sidecars, for the case where the
     * live file cannot be opened read-only while the host holds it. The caller discards it.
     */
    fun snapshotLyricsDatabase(): File? {
        val source = lyricsDatabaseFile() ?: return null
        return runCatching {
            val directory = File(hostContext.cacheDir, SNAPSHOT_DIRECTORY).apply { mkdirs() }
            val target = File(directory, "${System.nanoTime()}-${BoomingMusicPlayerConstants.HOST_DATABASE_NAME}")
            source.copyTo(target, overwrite = true)
            JOURNAL_SUFFIXES.forEach { suffix ->
                File(source.path + suffix).takeIf { it.isFile }?.copyTo(File(target.path + suffix), overwrite = true)
            }
            target
        }.getOrNull()
    }

    fun discardLyricsDatabaseSnapshot(snapshot: File) {
        runCatching {
            (listOf("") + JOURNAL_SUFFIXES).forEach { suffix -> File(snapshot.path + suffix).delete() }
        }
    }

    private fun readString(key: String): String? =
        runCatching { preferences.getString(key, null) }.getOrNull()

    private fun readBoolean(key: String, defaultValue: Boolean): Boolean =
        runCatching { preferences.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)

    private companion object {
        const val SNAPSHOT_DIRECTORY = "host-lyrics-db"
        val JOURNAL_SUFFIXES = listOf("-wal", "-shm")
    }
}
