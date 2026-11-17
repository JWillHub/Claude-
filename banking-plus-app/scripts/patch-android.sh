#!/usr/bin/env bash
# Adds Banking+'s Android pieces to the project Capacitor generates (run from banking-plus-app/ after `npx cap add android`).
set -euo pipefail
PKG=io/github/jwillhub/bankingplus
SRC=android/app/src/main/java/$PKG
mkdir -p "$SRC"
cp native/*.java "$SRC/"
RES=android/app/src/main/res
mkdir -p "$RES/xml"
cp native/res/xml/bp_files.xml "$RES/xml/"

# Sign every build with the same private key (unlocked in the workflow from signing.keystore.enc), so a new version installs over the old one
# and the app's data is kept. Each build also gets a higher version number.
cat >> android/app/build.gradle <<'GRADLE'

android {
    defaultConfig {
        versionCode((System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInteger())
        versionName("1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0"))
    }
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

# Manifest: the calendar-file sharer and vibration for the PAID stamp.
# (The page itself blocks every network connection with its security policy, the same as on the web.)
MANIFEST=android/app/src/main/AndroidManifest.xml
python3 - "$MANIFEST" <<'PY'
import re, sys
p = sys.argv[1]; s = open(p).read()
prov = ('        <provider android:name="androidx.core.content.FileProvider"\n'
        '            android:authorities="io.github.jwillhub.bankingplus.files"\n'
        '            android:exported="false" android:grantUriPermissions="true">\n'
        '            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/bp_files" />\n'
        '        </provider>\n')
assert '</application>' in s and '<application' in s
s = s.replace('</application>', prov + '    </application>', 1)
s = s.replace('<application', '<uses-permission android:name="android.permission.VIBRATE" />\n    <application', 1)
s = s.replace('<application', '<application android:allowBackup="false"', 1) if 'allowBackup' not in s else s
open(p, 'w').write(s)
PY

# App icon: plain picture for old Android versions, adaptive icon (with a one-colour layer for themed icons) for Android 8+
rm -rf "$RES"/mipmap-anydpi-v26
for d in "$RES"/mipmap-*; do
  cp www/bp-icon-192.png "$d/ic_launcher.png"
  cp www/bp-icon-192.png "$d/ic_launcher_round.png"
  rm -f "$d/ic_launcher_foreground.png"
done
cp -r native/res/mipmap-* "$RES"/
echo "patched"
