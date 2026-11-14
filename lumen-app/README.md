# Lumen for Android

The Android app of Lumen. Its screens are the published Lumen page (`lumen/`), copied in at build time,
so the app and the web version always match. Inside the app, Real-Debrid is called directly and Play opens
a native player (ExoPlayer with an FFmpeg audio decoder), so 4K HEVC, MKV and DTS/TrueHD play in the app.

- `native/` holds the player, the bridge the page uses to open it, and app updates (shared design with the
  Rolling Credits app, but its own package: `io.github.jwillhub.lumen`, so the two install side by side).
- `scripts/patch-android.sh` adds those to the Android project Capacitor generates.
- `.github/workflows/lumen-app.yml` builds the APK and publishes it as the `lumen-latest` release:
  https://github.com/JWillHub/Claude-/releases/download/lumen-latest/lumen.apk
- `debug.keystore` signs every build with the same key, so new versions install over old ones without losing data.
