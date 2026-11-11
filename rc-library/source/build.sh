set -e
cd /home/claude && T=$(mktemp -d) && cp claude-/reel-log/index.html $T/ && cd $T && python3 /home/claude/apply.py >/dev/null
python3 - <<'PY'
s=open('index.html').read()
s=s.replace("'reellog.' + k","@@MAINKEY@@")      # the one-time import reads the main app's keys
s=s.replace('reellog.','rclib.')                  # everything else: this app's own storage names
s=s.replace("@@MAINKEY@@","'reellog.' + k")
s=s.replace('#ffb547','#b8242c').replace('rgba(255,181,71,','rgba(184,36,44,').replace('rgba(255, 181, 71,','rgba(184,36,44,')
s=s.replace('color:#1a1206','color:#fff')
import re
# all text black or white: accent/green/coral/grey text becomes white; colored badges get black or white text
s=re.sub(r'(?<![-\w])color:var\(--(accent|accent2|good|muted)\)', 'color:#fff', s)
s=s.replace('--muted:#9c93a9','--muted:#fff').replace('--text:#f4efe9','--text:#fff')
for c in ('.c8{background:#3dbb6f;color:#0b1a10}','.c7{background:#e6c52f;color:#1c1600}','.c6{background:#ef8b2c;color:#1f0f00}'):
    s=s.replace(c, c.split('color:')[0]+'color:#000}')
s=s.replace('</style>', open('/tmp/theme.css').read() + '</style>', 1)
open('index.html','w').write(s)
PY
cp index.html /home/claude/claude-/rc-library/index.html && python3 /home/claude/apply_native.py >/dev/null && cp index.html /home/claude/claude-/android-app/www/index.html && echo built
