/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.media.MediaMetadata

/**
 * Reads the platform metadata lanes the host published. Every value is read defensively: a lane can
 * carry another type after a host-side update, and a read failure must not disturb the session.
 */
object BoomingMusicMetadataReader {

    fun snapshotOf(metadata: MediaMetadata): BoomingMetadataSnapshot = BoomingMetadataSnapshot(
        mediaId = stringOf(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID),
        title = stringOf(metadata, MediaMetadata.METADATA_KEY_TITLE),
        artist = stringOf(metadata, MediaMetadata.METADATA_KEY_ARTIST),
        album = stringOf(metadata, MediaMetadata.METADATA_KEY_ALBUM),
        durationMs = runCatching { metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) }
            .getOrDefault(0L),
        mediaUri = stringOf(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI)
    )

    private fun stringOf(metadata: MediaMetadata, key: String): String? =
        runCatching { metadata.getString(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
