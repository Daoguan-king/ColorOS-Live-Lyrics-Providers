/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.hook102.ExplicitProcessPolicy
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookInstaller
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderModuleEntry
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderProcessPolicy

/**
 * libxposed API 102 entry for BoomingMusic.
 *
 * The playback session and its `MediaLibraryService` live in the main process (`PlaybackService`
 * declares no `android:process`), so only the main process of each host package is accepted;
 * push/cast/helper processes are rejected before any business hook is installed.
 */
class BoomingMusicModuleEntry : ProviderModuleEntry() {

    override val providerId: ProviderId = ProviderId.BOOMINGMUSIC

    override val diagnosticsComponent: String = BoomingMusicPlayerConstants.DIAGNOSTICS_COMPONENT

    override val processPolicy: ProviderProcessPolicy = ExplicitProcessPolicy(
        packages = BoomingMusicPlayerConstants.QUALIFIED_HOST_PACKAGES.toSet(),
        acceptedProcessNames = BoomingMusicPlayerConstants.QUALIFIED_HOST_PACKAGES.toSet()
    )

    override fun createHookInstaller(): ProviderHookInstaller =
        ProviderHookInstaller { context -> BoomingMusicSessionHooker(context).onHook() }
}
