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
    s = s.replace('Rolling Credits pulls every film', 'Lunar pulls every film')
    s = s.replace("function poster(p, cls = '', size = 'w185'", "function poster(p, cls = '', size = 'w500'")   # sharp posters from the start
    s = s.replace('style="background-image:url(${IMG}w780${m.backdrop})"', 'style="background-image:url(${IMG}original${m.backdrop})"')   # full-size Trending backdrop
    s = s.replace('</head>', FONTS + '\n<style id="lunar">' + css + '</style>\n</head>', 1)
    s = s.replace('</body>', '<script>' + js + '</script>\n</body>', 1)
    return s

for src, dst in (('android-app/www/index.html', 'lunar-app/www/index.html'), ('rc-library/index.html', 'lunar/index.html')):
    s = open(os.path.join(REPO, src)).read()
    open(os.path.join(REPO, dst), 'w').write(lunarize(s))
print('lunar built')
