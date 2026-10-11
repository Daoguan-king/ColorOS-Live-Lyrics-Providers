# BoomingMusic Provider

Adapter for the local music player BoomingMusic (`com.mardous.booming`, and its debug build
`com.mardous.booming.debug`). Requires LSPosed modern API 102; the optional Bridge adds rendering,
AOD and compatibility enhancements on the SystemUI side.

## Why a dedicated adapter

The Universal Player Provider observes sessions in `system_server` and has to fetch lyrics from
online sources. A local library has no reliable online identity, and the lyrics usually already
exist on the device, so the generic route cannot see them. BoomingMusic itself decodes lyrics only
while its lyrics/now-playing screen is open, and every host class that owns that pipeline is
minified in a release APK.

This module therefore hooks only framework classes and rebuilds the host's own lyric-source order
from host-visible storage, so lyrics exist while the player is in the background:

```text
BoomingMusic audio file (MediaStore row id / file path)
        ↓  framework MediaStore lookup
sidecar .lrc/.ttml → embedded tag → downloaded lyrics in the host Room database
        ↓  shared parser-lrc / parser-ttml decoding
RichLyricLine lanes (line, word, translation)
        ↓  provider-core NativeLyricInfoPublisher (append-only)
MediaMetadata["lyricInfo"] on the host's own platform MediaSession
```

Hooked surface, all framework/platform API:

- `android.media.session.MediaSession` constructors, `setMetadata`, `setPlaybackState`, `setActive`,
  `release`;
- `android.app.Application#onTerminate` (cancels the loader scope);
- `MediaStore.Audio.Media` queries, the host's default preference file, and the host's Room database
  through `SQLiteDatabase` (read-only).

No host class or member name is referenced, because a release BoomingMusic APK is R8-minified. No
network permission is requested and no lyric is ever fetched remotely.

## Behaviour

- **Identity** follows the host's own anchors: `MediaMetadata.METADATA_KEY_MEDIA_ID` is the
  MediaStore row id the host publishes (`song.id`), with title/artist/album/duration as fallback.
  Real track changes mint exactly one generation; metadata completion for the same track only
  merges.
- **Source order** mirrors the host lyrics screen: sidecar file (host naming pattern
  `.*<base>.*\.(lrc|ttml)`, host `preferred_lyrics_file_format`, default `ttml`), then the embedded
  tag, then the downloaded row keyed by the host song id. The first *timed* payload wins; untimed
  text never stops the chain and is never published, because `lyricInfo` without a timeline cannot
  drive the lock screen.
- **Formats**: TTML (word timing plus `x-translation` spans, Apple `lyricOffset` honoured) and LRC
  (plain and enhanced/word-timed, with paired same-time translation lines).
- **Publication** is append-only: `lyricInfo` is written into the very `MediaMetadata` object the
  host published, so artwork lanes, URIs, ratings and unknown keys stay byte-identical and SystemUI
  keeps the official cover. A payload that finishes loading after the host's last metadata update is
  committed through the session's live metadata instead of a rebuilt copy.
- **Untouched host behaviour**: playback state, position, speed, actions, notification layout and
  the host's artwork/notification pipeline are never modified or fabricated.

## Bridge companion change

`ColorOS-Live-Lyrics-Bridge` adds `com.mardous.booming` to `PlayerSystemUiPolicy` so the package is
admitted to the OPlus media whitelist / media-history pipeline, the same package-only compatibility
Metrolist and Readify need. That admission is a prediction based on the host's standard Media3
`MediaSession` profile; it is the single change to revert if a device test shows vendor SystemUI
admits the package without it. The release package only is admitted — a debug host build
(`com.mardous.booming.debug`) is not.

## Privacy

Diagnostics contain event names, generations, hashed track keys and source labels only. Full
lyrics, file paths, database contents and raw media ids are never logged. Debug logging is opt-in
through LSPosed Remote Preferences and requires a full player restart.

## Validation and limitations

Device-verified on OnePlus 13 (PJZ110) / ColorOS 16 (Android 16, API 36) / BoomingMusic 1.4.2 /
LSPosed 2.2.1 API 102, with the Bridge rendering on the lock screen:

- `MODULE_LOADED` → `PROCESS_ACCEPTED` → `SESSION_HOOK_INSTALLED` (8 hooks) with no host
  disturbance: playback, controls, notification and artwork stayed native;
- identity/generation from the host's platform metadata (`TRACK_BOUND`), MediaStore file
  resolution in 3-6 ms;
- lyric sources verified on device: `.lrc`/`.ttml` sidecar **miss**, embedded FLAC tag **hit**
  (125 ms), host `LyricsEntity` row **hit** (147 ms);
- `BOOMINGMUSIC_RESULT_PUBLISHED` and `dumpsys media_session` metadata key count 13 → 14, i.e.
  `lyricInfo` is present in the session metadata SystemUI consumes;
- lock screen rendering confirmed with the Bridge: line list, current-line emphasis, translated
  line under each line, native media card and artwork untouched.

Device findings that shape the implementation:

- **Sidecars are usually unreachable.** On Android 11+ the FUSE/scoped-storage layer hides
  non-media files from the app UID: on the test device root saw 640 files in the music folder while
  the app saw 637 (exactly the three `.lrc` files). The host's own sidecar feature has the same
  limit; the sources that actually work for a local library are embedded tags and lyrics downloaded
  by the app. The sidecar source stays for hosts that hold all-files access.
- **The bootstrap runs before the base context exists.** `Application#attach` is hooked before
  `attachBaseContext`, so `context.applicationContext` is null there; the provider therefore uses
  the passed host context and touches base-context APIs lazily only.
- **Never post to the host main looper.** On this ROM a `Handler(Looper.getMainLooper())` message
  posted from the loader thread never ran; publication now happens on the loader thread (the
  platform session API is thread-safe), which removed the dependency on host scheduling.
- Lyrics that finish loading late are committed through the session's live metadata, and a payload
  for a track that already changed is dropped by the generation gate.

Still open before an upstream release: the exact APK/version matrix, AOD-specific geometry, rapid
track skipping and screen-off behaviour on other ROM versions. Sidecar support also depends on the
host's storage access, which differs per install.

Build and test with JDK 21 and the repository SDK/toolchain:

```sh
./gradlew :player-boomingmusic:testDebugUnitTest :player-boomingmusic:assembleDebug
./gradlew :player-boomingmusic:lintDebug :player-boomingmusic:assembleRelease
```
