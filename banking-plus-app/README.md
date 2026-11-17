# Banking+ for Android

The Android app of Banking+. Its screens are the published Banking+ page (`banking-plus/`), copied in at
build time, so the app and the web version always match.

- Package `io.github.jwillhub.bankingplus`, so it installs alongside the other apps.
- Everything stays on the phone: the page's security policy blocks every network connection, the same as on the web.
- `native/` adds `window.BPNative` to the page: saving backups and CSV files through Android's "Save to" screen,
  handing calendar reminders to the calendar app, matching the status bar to the light or dark look,
  and closing pop-ups with Android's back swipe.
- `scripts/patch-android.sh` adds those to the Android project Capacitor generates.
- `.github/workflows/banking-plus-app.yml` builds the APK and publishes it as the `banking-plus-latest` release:
  https://github.com/JWillHub/Claude-/releases/download/banking-plus-latest/banking-plus.apk
- Signed with the same private key as the other apps (`signing.keystore.enc`), so updates install over the old version and keep its data.

Data in the app is separate from data in the browser. To move it: Settings → Backup and export → Save backup
in the browser, then Restore backup in the app.
