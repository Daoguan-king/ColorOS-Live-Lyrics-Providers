/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.settings102.ProviderDebugSettingsActivity

class BoomingMusicDebugSettingsActivity : ProviderDebugSettingsActivity() {
    override val providerId: ProviderId = ProviderId.BOOMINGMUSIC
    override val providerDisplayName: String = "BoomingMusic 歌词 Provider"
    override val targetPackageDescription: String =
        "目标播放器：com.mardous.booming（发布版/F-Droid/Play 版）与 com.mardous.booming.debug（调试版）"
}
