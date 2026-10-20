# OrbitPlayer

**Open-source Android music and video player** powered by AndroidX Media3, written in Java. Apache-2.0 license.

> Pre-release / unverified: a successful APK build and Android playback test have not yet been completed.

## Features in the prepared source package
- Local music and video library, searching, favorites and playlists
- Audio queue, shuffle/repeat, background MediaSession playback
- Video controls, subtitle file selection, picture-in-picture
- Progressive HTTP(S), HLS, MPEG-DASH and RTSP stream playback
- Android 6.0+ target (codec support varies by device)

## Source upload
The complete 23-file Android Studio project is distributed as **OrbitPlayer-open-source.zip** from the project creator's ChatGPT conversation. Upload **OrbitPlayer-open-source.zip** to the **root** of this repository with GitHub **Add file → Upload files** and commit it. The GitHub Actions workflow can then extract and build it.

For fully browsable source on GitHub, extract the ZIP locally and upload the contents of the inner `OrbitPlayer/` directory (including hidden `.github`) to the repository root as a subsequent commit. The source package includes the complete Apache-2.0 LICENSE and Android project.

## Get the debug APK
1. Add the source ZIP to the root, named exactly `OrbitPlayer-open-source.zip`.
2. Go to **Actions → Build OrbitPlayer APK → Run workflow**, or push the ZIP.
3. If the Android job succeeds, download the **OrbitPlayer-debug-apk** workflow artifact.
4. Extract the artifact ZIP and install `app-debug.apk` on an Android phone.

The APK is not yet available, and builds may require fixes after the first run. Do not distribute it as stable until it is tested.

## Known limitations
No built-in VLC software decoding, DVD playback, SMB browser, DRM support or advanced equalizer. You must provide direct media URLs for streaming. Regular video website landing pages are not stream URLs.