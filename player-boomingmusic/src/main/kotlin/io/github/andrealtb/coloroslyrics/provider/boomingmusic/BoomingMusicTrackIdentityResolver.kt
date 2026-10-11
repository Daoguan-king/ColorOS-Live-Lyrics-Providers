/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity

/** Platform metadata fields this Provider reads; kept free of android types so it stays testable. */
data class BoomingMetadataSnapshot(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val mediaUri: String? = null
)

/**
 * One playing track of the host.
 *
 * [identity] is the Provider-wide track identity; [songId] and [mediaUri] are the host's own anchors
 * for the same song (MediaStore row id and content URI built from it).
 */
data class BoomingTrack(
    val identity: TrackIdentity,
    val songId: Long?,
    val mediaUri: String?
) {
    val stableKey: String
        get() = identity.buildStableKey()
}

object BoomingMusicTrackIdentityResolver {

    /**
     * Builds the host identity from a platform metadata snapshot, or null when nothing usable is
     * published yet. BoomingMusic publishes the MediaStore row id as the media id, which is the
     * strongest anchor available and is also the lyrics-database primary key.
     */
    fun trackFrom(snapshot: BoomingMetadataSnapshot): BoomingTrack? {
        val identity = TrackIdentity(
            id = snapshot.mediaId?.trim()?.takeIf { it.isNotEmpty() },
            title = snapshot.title?.trim()?.takeIf { it.isNotEmpty() },
            artist = snapshot.artist?.trim()?.takeIf { it.isNotEmpty() },
            album = snapshot.album?.trim()?.takeIf { it.isNotEmpty() },
            durationMs = snapshot.durationMs.coerceAtLeast(0L)
        )
        if (identity.isBlank) return null
        return BoomingTrack(
            identity = identity,
            songId = songIdOf(snapshot.mediaId),
            mediaUri = snapshot.mediaUri?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /** The host publishes `song.id.toString()`, so only a positive decimal id is a real row id. */
    fun songIdOf(mediaId: String?): Long? {
        val raw = mediaId?.trim() ?: return null
        if (raw.isEmpty() || raw.length > 18) return null
        if (!raw.all { it in '0'..'9' }) return null
        return raw.toLongOrNull()?.takeIf { it > 0L }
    }
}
