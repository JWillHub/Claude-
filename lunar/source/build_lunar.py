#!/usr/bin/env python3
"""Builds Lunar from the freshly built Rolling Credits pages.
Lunar reuses everything that works in Rolling Credits (TMDB, Real-Debrid, player, title pages, ratings) and
changes the look and some screens. Its own saved data uses lunar.* names, so it never touches Rolling Credits'.
  lunar-app/www/index.html   the Android app   (from android-app/www/index.html)
  lunar/index.html           the web copy       (from rc-library/index.html)
"""
import os, re
SRC = os.path.dirname(os.path.abspath(__file__)); REPO = os.path.abspath(os.path.join(SRC, '..', '..'))
css = open(os.path.join(SRC, 'lunar.css')).read()
js = open(os.path.join(SRC, 'lunar.js')).read()
FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">')

def lunarize(s):
    s = s.replace('rclib.', 'lunar.').replace("'reellog.' + k", "'lunar-none.' + k")       # own storage; no import from the RC web page
    s = s.replace("if(localStorage.getItem('lunar.imported')) return;", "return;")
    s = s.replace('<title>Rolling Credits</title>', '<title>Lunar</title>')
    s = s.replace('content="Rolling Credits"', 'content="Lunar"')
    s = s.replace("releases/download/android-latest/", "releases/download/lunar-latest/")
    s = s.replace("rolling-credits.apk", "lunar.apk")
    # data saver: posters and backdrops come from size variables chosen once at start-up
    s = s.replace("${IMG}w500", "${IMG}${LPS}").replace("${IMG}original", "${IMG}${LBS}").replace("${IMG}w1280", "${IMG}${LBS}")
    assert 'src="${IMG}${size}${p}"' in s
    s = s.replace('src="${IMG}${size}${p}"', 'src="${IMG}${size === \'w500\' ? LPS : size}${p}"')
    # the tab bar follows the finger instead of hiding by itself (lunar.js drives it)
    assert "document.body.classList.toggle('tbhide', d > 0 && y > 120 && !document.body.classList.contains('locked'));" in s
    s = s.replace("document.body.classList.toggle('tbhide', d > 0 && y > 120 && !document.body.classList.contains('locked'));", "")
    # no pop-up rating card: holding a poster no longer starts a timer, so it acts like a normal tap
    assert "lp.timer = setTimeout(() => {" in s
    s = s.replace("lp.timer = setTimeout(() => {", "lp.timer = 0 && setTimeout(() => {")
    # hold opens the rating card; rating takes a separate tap (no rating while still holding)
    assert "openRatepad(pid(pc.dataset.id), true, lp.x, lp.y);" in s
    s = s.replace("openRatepad(pid(pc.dataset.id), true, lp.x, lp.y);", "openRatepad(pid(pc.dataset.id), false);")
    # search bars and headers stay put: no sliding away on scroll
    s = s.replace("const bars=()=>document.querySelectorAll('header.top,body.libv .ltop,#v-genres:not([hidden]) .gtop');", "const bars=()=>[];")
    # ask for sharp images up front so nothing gets swapped (and redrawn) after it loads
    s = s.replace("w185',", "w500',").replace("'w342'", "'w500'").replace("w185${p.profile}", "w500${p.profile}").replace("}w342${f0.profile}", "}w500${f0.profile}").replace("}w342${p.profile}", "}w500${p.profile}")
    s = s.replace("let c = store.get(K_ACCENT, '#b8242c'),", "let c = '#b8242c',")
    s = s.replace('Rolling Credits pulls every film', 'Lunar pulls every film')
    s = s.replace("function poster(p, cls = '', size = 'w185'", "function poster(p, cls = '', size = 'w500'")   # sharp posters from the start
    s = s.replace('style="background-image:url(${IMG}w780${m.backdrop})"', 'style="background-image:url(${IMG}${LBS}${m.backdrop})"')   # full-size Trending backdrop
    s = s.replace('<head>', '<head>\n<script>var LDS = false; try{ LDS = JSON.parse(localStorage.getItem("lunar.f.saver") || "false") === true; }catch(e){} var LPS = LDS ? "w342" : "w500", LBS = LDS ? "w780" : "original";</script>', 1)
    s = s.replace('</head>', FONTS + '\n<style id="lunar">' + css + '</style>\n</head>', 1)
    s = s.replace('</body>', '<script>' + js + '</script>\n</body>', 1)
    return s

for src, dst in (('android-app/www/index.html', 'lunar-app/www/index.html'), ('rc-library/index.html', 'lunar/index.html')):
    s = open(os.path.join(REPO, src)).read()
    open(os.path.join(REPO, dst), 'w').write(lunarize(s))
print('lunar built')
