/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import android.media.MediaMetadata
import android.media.session.MediaSession
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackGenerationPolicy
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import io.github.andrealtb.coloroslyrics.provider.core.publisher.NativeLyricInfoPublisher

/**
 * Publishes `lyricInfo` through the shared publisher.
 *
 * Both entry points write the single key into a [MediaMetadata] the host itself built
 * (`HostMetadataOverlay`), so artwork lanes, URIs and unknown host keys are never copied or
 * rebuilt. BoomingMusic publishes artwork as a URI and may never publish a bitmap lane, so unlike
 * URI-based hosts with bitmap artwork there is no bitmap-readiness gate here: gating on a bitmap
 * that the host does not produce would silently drop every lyric payload.
 */
object BoomingMusicLyricPublisher {

    /** Appends into metadata the host is currently publishing; the host commits it. */
    fun attachInPlace(
        metadata: MediaMetadata,
        track: TrackIdentity,
        document: BoomingLyricDocument,
        generation: Long,
        generationPolicy: TrackGenerationPolicy,
        hostPackage: String
    ): NativeLyricInfoPublisher.Result = NativeLyricInfoPublisher.publishToHostMetadata(
        metadata = metadata,
        track = track,
        lines = document.lines,
        trackGeneration = generation,
        generationPolicy = generationPolicy,
        playerPackage = hostPackage,
        hostPackage = hostPackage
    )

    /**
     * Appends into the session's live metadata and re-sends that same object, which is how a payload
     * that finished loading after the host's last metadata update reaches SystemUI.
     */
    fun publishLive(
        session: MediaSession,
        track: TrackIdentity,
        document: BoomingLyricDocument,
        generation: Long,
        generationPolicy: TrackGenerationPolicy,
        registry: BoomingMusicMediaSessionRegistry,
        hostPackage: String
    ): NativeLyricInfoPublisher.Result {
        val live = runCatching { session.controller.metadata }.getOrNull()
            ?: return NativeLyricInfoPublisher.Result.INVALID_INPUT
        val hostTrack = BoomingMusicTrackIdentityResolver
            .trackFrom(BoomingMusicMetadataReader.snapshotOf(live))
            ?.identity
        if (hostTrack != null && !TrackIdentityPolicy.isSameTrack(hostTrack, track)) {
            return NativeLyricInfoPublisher.Result.STALE_GENERATION
        }

        val result = attachInPlace(live, track, document, generation, generationPolicy, hostPackage)
        if (!result.isPublished) return result

        val committed = runCatching {
            registry.withModuleWrite { session.setMetadata(live) }
        }.isSuccess
        return classifyCommit(result, committed)
    }

    internal fun classifyCommit(
        publicationResult: NativeLyricInfoPublisher.Result,
        sessionCommitSucceeded: Boolean
    ): NativeLyricInfoPublisher.Result = if (
        publicationResult.isPublished && !sessionCommitSucceeded
    ) NativeLyricInfoPublisher.Result.COMMIT_FAILED else publicationResult
}
