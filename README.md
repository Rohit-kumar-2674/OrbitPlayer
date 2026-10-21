# ◈ OrbitPlayer

**A local-first, open-source Android music and video player with an intentionally ergonomic interface.**

> **v0.2.0 preview** — Builds through GitHub Actions. The preview installs next to the older Orbit Player prototype and does **not** replace or delete it. Phone playback/compatibility testing is ongoing.

## Download and install

1. Open [Build OrbitPlayer APK](https://github.com/Rohit-kumar-2674/OrbitPlayer/actions/workflows/build.yml).
2. Select the newest successful run (green check).
3. Scroll to **Artifacts** and download **OrbitPlayer-debug-apk**.
4. Unzip, then install `app-debug.apk`. On Android, allow installation from your file manager when prompted.

Or download the APK if a direct link has been provided in the ChatGPT project conversation.

Package: `com.orbit.player.preview`. Version: 0.2.0. Min API 23 (Android 6). Debug builds are experimental and are not signed as production releases.

## New in v0.2.0

- **Home:** curated dashboard, quick library shortcuts, media counts and recently added content
- **Music:** songs, artists, albums, persistent personal playlists
- **Videos:** dedicated library and full-screen controls
- **Folders:** real folder paths, searchable groups, recursively scan entire folders using Storage Access Framework
- **Multiple storage locations:** add an entire Music folder, Downloads folder, or SD-card subfolder. Manage access under More
- **Artwork:** embedded music art and video thumbnails decoded off the UI thread
- **Navigation:** five thumb-friendly bottom tabs, minimum 48dp touch targets, mini-player
- **Playback:** dedicated Now Playing and Video screens, seek, speed, queues, repeat/shuffle
- **Streams:** HTTP(S), direct-file, HLS, MPEG-DASH and RTSP support via AndroidX Media3
- **Extras:** background MediaSession playback, favorites, subtitles (SRT/VTT/ASS), picture-in-picture, rotate and aspect mode

## First-time setup

On Android 13+, approve Music and Videos access when prompted. Older Android versions use the Storage permission. In **Folders**, tap **Add whole folder** and select a Music, Movies or Download folder. Orbit will index subfolders automatically; you do *not* need to pick each media file. Repeat for an SD-card folder if desired. Due to Android restrictions, selecting the top-level internal-storage root may be prohibited; choose allowed media subfolders.

If media remains missing, select its directory using Add whole folder, then Rescan. Only read permissions are used. Android may only expose some videos when media access is limited by the OS. Artwork availability depends on the media file.

## Known limitations

- This is a real native APK, **not yet a replacement for full MX Player Pro or libVLC**.
- File codec support is limited to codecs supported by Android and Media3 on the device. No software-decoder integration is included.
- No SMB/SFTP/FTP directory browser, Chromecast, equalizer, or DRM management yet.
- SAF directories may duplicate some MediaStore entries when providers report different folder paths. Folder access is granted by the user.
- More than 80 items are shown via incremental **Show more** loading (to avoid rendering thousands of rows at once).
- The UI uses native Android views, not Material3 Compose, to maintain Android 6 support.
- The preview has a separate package ID; its preferences and playlists do not automatically migrate to the older version.
- Each Actions debug build may use a different signing key. To replace an earlier preview debug build, you may need to uninstall the prior preview. This deletes *preview* app settings.

## Build locally

Requires JDK 17 and Android SDK 35:

```sh
gradle --no-daemon :app:assembleDebug
```

Generated APK: `app/build/outputs/apk/debug/app-debug.apk`

## Contribute

Pull requests and issues are welcome. See [LICENSE](LICENSE) for Apache License 2.0.

Media3 source and licensing: https://github.com/androidx/media
