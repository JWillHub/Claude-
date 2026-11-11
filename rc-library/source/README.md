# Rolling Credits Library: source

The library app is the main Rolling Credits page (`reel-log/index.html`) with these changes applied on top:

- `rd_module.js`: the Library tab, Real-Debrid, playback, Continue watching, title pages
- `settings_new.js`: the Settings tab
- `apply.py`: patches `reel-log/index.html` (adds the two files above, Library tab, styles)
- `apply_native.py`: extra changes for the Android app only
- `theme.css` + `build.sh`: black and white text, see-through outlined pills, dark red accent; build.sh runs everything

Output: `rc-library/index.html` (web) and `android-app/www/index.html` (Android app).
The Android player itself is in `android-app/native/` (PlayerActivity.java and the plugins).
