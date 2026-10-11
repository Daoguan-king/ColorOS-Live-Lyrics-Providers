/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.boomingmusic

import java.io.File
import java.lang.reflect.Method

/**
 * Second host source: lyrics embedded in the audio tags.
 *
 * The host reads them through its own tag library, which is a minified release dependency. It also
 * ships jaudiotagger with `-keep class org.jaudiotagger.** { *; }`, so that reader is the only
 * embedded-lyrics path whose names survive the host's release build; it is resolved lazily and the
 * source fails open when the host does not ship it.
 */
class BoomingMusicEmbeddedLyricSource(private val classLoader: ClassLoader) {

    private val lock = Any()

    @Volatile
    private var resolved = false

    @Volatile
    private var readMethod: Method? = null

    fun read(filePath: String): BoomingRawLyric? {
        val read = readMethod() ?: return null
        val audioFile = runCatching { read.invoke(null, File(filePath)) }.getOrNull() ?: return null
        val tag = runCatching { audioFile.javaClass.getMethod("getTag").invoke(audioFile) }.getOrNull() ?: return null
        val lyrics = runCatching {
            tag.javaClass
                .getMethod("getFirst", String::class.java)
                .invoke(tag, BoomingMusicPlayerConstants.JAUDIOTAGGER_LYRICS_FIELD_KEY) as? String
        }.getOrNull()
        return lyrics?.takeIf { it.isNotBlank() }?.let {
            BoomingRawLyric(
                text = it,
                sourceLabel = BoomingMusicPlayerConstants.SOURCE_EMBEDDED,
                providerLabel = TAGGER_PROVIDER
            )
        }
    }

    private fun readMethod(): Method? {
        readMethod?.let { return it }
        if (resolved) return null
        synchronized(lock) {
            readMethod?.let { return it }
            if (resolved) return null
            val method = runCatching {
                Class.forName(BoomingMusicPlayerConstants.JAUDIOTAGGER_AUDIO_FILE_IO, false, classLoader)
                    .getMethod("read", File::class.java)
            }.getOrNull()
            readMethod = method
            resolved = true
            return method
        }
    }

    private companion object {
        const val TAGGER_PROVIDER = "jaudiotagger"
    }
}
