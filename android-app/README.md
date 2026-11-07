# Rolling Credits for Android

A separate Android build of the Rolling Credits library copy (`rc-library`). It plays files with a native
player (ExoPlayer with an FFmpeg audio decoder), so 4K HEVC, MKV and DTS/TrueHD audio play inside the app.
The published web apps (`reel-log`, `rc-library`) are not changed by anything in this folder.

- `www/` is the app's screens: a copy of the rc-library page, made with the same build script.
- `native/` holds the player and the bridge the page uses to open it.
- `scripts/patch-android.sh` adds those to the Android project that Capacitor generates.
- `.github/workflows/android-app.yml` builds the APK and publishes it as the `android-latest` release:
  https://github.com/JWillHub/Claude-/releases/download/android-latest/rolling-credits.apk
- `debug.keystore` signs every build with the same key, so new versions install over old ones without losing data.
