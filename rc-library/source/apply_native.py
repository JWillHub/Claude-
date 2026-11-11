# Android-app-only tweaks on top of the rc-library build (run in the folder holding index.html)
p = 'index.html'; s = open(p).read()
def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:60], s.count(old))
    s = s.replace(old, new)
# no service worker inside the app (the files are already on the phone)
rep("if('serviceWorker' in navigator && location.protocol === 'https:')", "if(!NATIVE && 'serviceWorker' in navigator && location.protocol === 'https:')")
rep('<link rel="manifest" href="manifest.json">', '<script src="capacitor.js"></script>')   # Capacitor's own bridge script (copied in at build time)
# build stamp in Settings, filled in by the GitHub build, so it's easy to tell which version is installed
rep('.hero h2{font-size:31px}', '.hero h2{font-size:31px}\n#setTab{position:relative}#setTab.upd::after{content:"";position:absolute;top:6px;right:calc(50% - 16px);width:9px;height:9px;border-radius:50%;background:var(--good);box-shadow:0 0 0 2px var(--bg)}')
open(p, 'w').write(s)
print('applied native')
