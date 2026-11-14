#!/usr/bin/env bash
# Adds the native player to the Android project Capacitor generates (run from android-app/ after `npx cap add android`).
set -euo pipefail
PKG=io/github/jwillhub/rollingcredits
SRC=android/app/src/main/java/$PKG
mkdir -p "$SRC"
cp native/*.java "$SRC/"
mkdir -p android/app/src/main/res/xml
cp native/res/xml/update_paths.xml android/app/src/main/res/xml/

# Player libraries: Media3 ExoPlayer + UI, and Jellyfin's prebuilt FFmpeg audio decoder for DTS / TrueHD
cat >> android/app/build.gradle <<'GRADLE'

dependencies {
    implementation "androidx.media3:media3-exoplayer:1.9.0"
    implementation "androidx.media3:media3-exoplayer-hls:1.9.0"
    implementation "androidx.media3:media3-ui:1.9.0"
    implementation "org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1"
}
GRADLE

# Sign every build with the same private key (unlocked in the workflow from signing.keystore.enc), so a new version installs over the old one
cat >> android/app/build.gradle <<'GRADLE'

android {
    signingConfigs {
        debug {
            storeFile file("../../debug.keystore")
            storePassword(System.getenv("KS_PASS") ?: "android")
            keyAlias "androiddebugkey"
            keyPassword(System.getenv("KS_PASS") ?: "android")
        }
    }
    buildTypes {
        debug { signingConfig signingConfigs.debug }
    }
}
GRADLE

# Register the player screen
MANIFEST=android/app/src/main/AndroidManifest.xml
python3 - "$MANIFEST" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
act = ('        <activity android:name="io.github.jwillhub.rollingcredits.PlayerActivity"\n'
       '            android:theme="@style/AppTheme.NoActionBar"\n'
       '            android:supportsPictureInPicture="true"\n'
       '            android:resizeableActivity="true"\n'
       '            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"\n'
       '            android:screenOrientation="sensorLandscape"\n'
       '            android:exported="false" />\n')
prov = ('        <provider android:name="io.github.jwillhub.rollingcredits.UpdateFileProvider"\n'
        '            android:authorities="io.github.jwillhub.rollingcredits.updates"\n'
        '            android:exported="false" android:grantUriPermissions="true">\n'
        '            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/update_paths" />\n'
        '        </provider>\n')
assert '</application>' in s and '<application' in s
s = s.replace('</application>', act + prov + '    </application>', 1)
s = s.replace('<application', '<application android:largeHeap="true"', 1) if 'largeHeap' not in s else s
s = s.replace('<application', '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n    <uses-permission android:name="android.permission.VIBRATE" />\n    <application', 1)
open(p, 'w').write(s)
PY

# App icon: plain picture for old Android versions (the generated adaptive icon is replaced below)
RES=android/app/src/main/res
rm -rf "$RES"/mipmap-anydpi-v26
for d in "$RES"/mipmap-*; do
  cp www/icon-192.png "$d/ic_launcher.png"
  cp www/icon-192.png "$d/ic_launcher_round.png"
  rm -f "$d/ic_launcher_foreground.png"
done
# Adaptive icon (Android 8+): glass background + logo, plus a one-color layer so "Themed icons" (Android 13+) can tint it
cp -r native/res/mipmap-* "$RES"/
echo "patched"
