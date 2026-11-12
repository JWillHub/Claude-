#!/bin/bash
# Builds all three pages from base.html (the original Rolling Credits page):
#   reel-log/index.html          the published web app (keeps its own saved data: reellog.* names)
#   rc-library/index.html        the web copy (rclib.* names)
#   android-app/www/index.html   the Android app (rclib.* names + app-only changes)
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"; REPO="$(cd "$SRC/../.." && pwd)"
build(){   # $1 = copy (rename storage to rclib.*) or main
  T=$(mktemp -d); cp "$SRC/base.html" "$T/index.html"; cd "$T"
  python3 "$SRC/apply.py" >/dev/null
  MODE="$1" SRC="$SRC" python3 - <<'PY'
import os, re
s = open('index.html').read()
if os.environ['MODE'] == 'copy':
    s = s.replace("'reellog.' + k", "@@MAINKEY@@")      # the one-time import reads the main app's keys
    s = s.replace('reellog.', 'rclib.')                  # everything else: the copy's own storage names
    s = s.replace("@@MAINKEY@@", "'reellog.' + k")
else:
    s = s.replace("if(localStorage.getItem('rclib.imported')) return;", "return;")   # the main page already has its data
# dark red accent
s = s.replace('#ffb547', '#b8242c').replace('rgba(255,181,71,', 'rgba(184,36,44,').replace('rgba(255, 181, 71,', 'rgba(184,36,44,')
s = s.replace('color:#1a1206', 'color:#fff')
# the accent can be changed in Settings: everything red follows one variable
s = s.replace('fill="#b8242c"', 'style="fill:var(--accent)"')
s = re.sub(r'rgba\(184,36,44,(\.?\d*\.?\d+)\)', lambda m: 'color-mix(in srgb, var(--accent) %d%%, transparent)' % round(float(m.group(1)) * 100), s)
# all text black or white
s = re.sub(r'(?<![-\w])color:var\(--(accent|accent2|good|muted)\)', 'color:#fff', s)
s = s.replace('--muted:#9c93a9', '--muted:#fff').replace('--text:#f4efe9', '--text:#fff')
for c in ('.c8{background:#3dbb6f;color:#0b1a10}', '.c7{background:#e6c52f;color:#1c1600}', '.c6{background:#ef8b2c;color:#1f0f00}'):
    s = s.replace(c, c.split('color:')[0] + 'color:#000}')
s = s.replace('</style>', open(os.path.join(os.environ['SRC'], 'theme.css')).read() + '</style>', 1)
import sys; sys.path.insert(0, os.environ['SRC']); import light_gen; s = light_gen.gen(s)
open('index.html', 'w').write(s)
PY
}
build main; cp index.html "$REPO/reel-log/index.html"
build copy; cp index.html "$REPO/rc-library/index.html"
python3 "$SRC/apply_native.py" >/dev/null; cp index.html "$REPO/android-app/www/index.html"
echo built
