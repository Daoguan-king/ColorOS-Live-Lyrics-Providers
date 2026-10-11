/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

/**
 * BoomingMusic host contract.
 *
 * Every name below is a public, stable Android/AOSP-facing contract or the host's own on-disk
 * schema read through framework APIs; no obfuscated host class or member is referenced, because a
 * release BoomingMusic APK is R8-minified.
 */
object BoomingMusicPlayerConstants {
    const val MODULE_PACKAGE = "io.github.andrealtb.coloroslyrics.provider.boomingmusic"

    /** Release (github/fdroid/playstore) applicationId. */
    const val HOST_PACKAGE = "com.mardous.booming"

    /** Debug build applicationId; the player honours the same in-process contract. */
    const val HOST_DEBUG_PACKAGE = "com.mardous.booming.debug"

    const val DIAGNOSTICS_COMPONENT = "provider/boomingmusic"

    val QUALIFIED_HOST_PACKAGES = arrayOf(HOST_PACKAGE, HOST_DEBUG_PACKAGE)

    // Host storage contract: BoomingDatabase name, LyricsDao entity and columns. The entity is
    // kept unobfuscated by the host's own R8 rules, and the table/columns come from Room's
    // documented default naming, so this stays readable in a minified release build.
    const val HOST_DATABASE_NAME = "music_database.db"
    const val HOST_LYRICS_TABLE = "LyricsEntity"
    const val HOST_LYRICS_COLUMN_ID = "id"
    const val HOST_LYRICS_COLUMN_LYRICS = "lyrics"
    const val HOST_LYRICS_COLUMN_PROVIDER = "provider"
    const val HOST_LYRICS_COLUMN_INSTRUMENTAL = "is_instrumental"

    // Host preference keys, read from the same default preference file the host writes.
    const val PREF_PREFERRED_LYRICS_FORMAT = "preferred_lyrics_file_format"
    const val PREF_FORCE_UTF8_ENCODING = "force_utf8_encoding_for_lyrics"
    const val PREF_IGNORE_BLANK_LINES = "ignore_blank_lines_in_lyrics"
    const val DEFAULT_PREFERRED_LYRICS_FORMAT = "ttml"

    const val FORMAT_LRC = "lrc"
    const val FORMAT_TTML = "ttml"

    /** Lyrics sources in the host's own priority order (RealLyricsRepository/lyrics screen). */
    const val SOURCE_SIDECAR = "sidecar"
    const val SOURCE_EMBEDDED = "embedded"
    const val SOURCE_STORED = "stored"

    /** jaudiotagger is kept by name in the host's release rules; its lyrics field key is stable. */
    const val JAUDIOTAGGER_AUDIO_FILE_IO = "org.jaudiotagger.audio.AudioFileIO"
    const val JAUDIOTAGGER_LYRICS_FIELD_KEY = "LYRICS"
}
