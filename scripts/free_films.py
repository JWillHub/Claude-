#!/usr/bin/env python3
"""Free, legal films for Rolling Credits.

Pulls the Internet Archive's curated public-domain collections (feature films and classic cartoons)
plus the Blender Foundation's Creative Commons short films, matches each item to its IMDb title
(title + year), picks a playable MP4 and writes:

  out/free/map.json       { "tt0063350": ["https://archive.org/download/.../file.mp4", "archive-id", "films"], ... }
  out/free/films.json     ["tt...", ...]   feature films, most-voted first
  out/free/cartoons.json  ["tt...", ...]   cartoons and animated shorts, most-voted first
  out/free/meta.json      { "built": "...", "films": n, "cartoons": n }

Only confident matches are kept, which also drops most uploads that are not really the film they claim to be.
Usage: free_films.py DATA_DIR OUT_DIR   (DATA_DIR holds IMDb's title.basics / title.ratings .tsv.gz)
"""
import concurrent.futures as cf, csv, gzip, json, os, re, sys, time, unicodedata, urllib.parse, urllib.request
from collections import defaultdict

DATA, OUT = sys.argv[1], sys.argv[2]
UA = {'User-Agent': 'RollingCredits-free-films/1.0 (github.com/JWillHub/Claude-)'}
csv.field_size_limit(10**9)

def get_json(url, tries=4):
    for k in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return json.load(r)
        except Exception as e:
            if k == tries - 1: print('  failed', url[:120], e); return None
            time.sleep(2 + 3 * k)

# ---------- Internet Archive ----------
def scrape(q):
    items, cursor = [], None
    while True:
        p = {'q': q, 'fields': 'identifier,title,year,date,downloads,subject', 'count': 10000}
        if cursor: p['cursor'] = cursor
        d = get_json('https://archive.org/services/search/v1/scrape?' + urllib.parse.urlencode(p))
        if not d: break
        items += d.get('items', [])
        cursor = d.get('cursor')
        if not cursor: break
    return items

# Disc and web rips: often a modern restoration with a new (copyrighted) score, and usually x265 that browsers can't play
RIP = re.compile(r'bd-?rip|blu-?ray|br-?rip|web-?dl|webrip|remux|x\.?265|hevc|yify|yts|dts|\[[a-z]+\]', re.I)
SKIP_WORDS = re.compile(r'censored|propaganda|racis|stereotyp|blackface|trailer|preview|teaser|colorized|colourized|clip|excerpt', re.I)

def as_text(v):
    if isinstance(v, list): return ' '.join(map(str, v))
    return str(v or '')

def year_of(it):
    for k in ('year', 'date'):
        m = re.search(r'(18[89]\d|19\d\d|20[0-2]\d)', as_text(it.get(k)))
        if m: return int(m.group(1))
    m = re.search(r'\((18[89]\d|19\d\d|20[0-2]\d)\)|\b(19\d\d|20[0-2]\d)\s*$', as_text(it.get('title')))
    return int(m.group(1) or m.group(2)) if m else None

# ---------- titles ----------
def norm(t):
    t = unicodedata.normalize('NFKD', t).encode('ascii', 'ignore').decode().lower()
    t = t.replace('&', ' and ')
    t = re.sub(r"[’'`]", '', t)
    t = re.sub(r'[^a-z0-9]+', ' ', t).strip()
    t = re.sub(r'^(the|a|an) ', '', t)
    return t

def variants(title):
    """Archive titles are messy: 'His Girl Friday (1940)', 'Charade - Cary Grant', 'Popeye: Little Swee'Pea'."""
    t = re.sub(r'\s*[\(\[][^\)\]]*[\)\]]', ' ', title)          # bracketed bits
    t = re.sub(r'\b(18[89]\d|19\d\d|20[0-2]\d)\s*$', '', t.strip())  # trailing year
    t = re.sub(r'(?i)\b(hd|1080p|720p|full movie|feature film|public domain)\b', '', t)
    out = [t]
    for sep in (' - ', ' | ', ' -- ', ': ', ' / '):
        if sep in t: out += [t.split(sep)[-1], t.split(sep)[0]]   # 'Superman - The Mechanical Monsters': the episode name first
    seen, res = set(), []
    for v in out:
        n = norm(v)
        if len(n) >= 2 and n not in seen: seen.add(n); res.append(n)
    return res

def load_imdb():
    votes = {}
    with gzip.open(os.path.join(DATA, 'title.ratings.tsv.gz'), 'rt', encoding='utf-8') as f:
        next(f)
        for line in f:
            tt, _, v = line.rstrip('\n').split('\t'); votes[tt] = int(v)
    idx = defaultdict(list)    # normalized title -> [(tt, year, kind, votes, animated)]
    with gzip.open(os.path.join(DATA, 'title.basics.tsv.gz'), 'rt', encoding='utf-8') as f:
        next(f)
        for line in f:
            p = line.rstrip('\n').split('\t')
            tt, kind, prim, orig, adult, yr, genres = p[0], p[1], p[2], p[3], p[4], p[5], p[8]
            if kind not in ('movie', 'tvMovie', 'short') or adult == '1' or yr == '\\N': continue
            v = votes.get(tt, 0)
            if v < 20: continue
            rec = (tt, int(yr), kind, v, 'Animation' in genres)
            for t in {norm(prim), norm(orig)}:
                if t: idx[t].append(rec)
    print('IMDb titles indexed:', len(idx))
    return idx

def match(it, idx, want):
    """want: 'films' (features) or 'cartoons' (shorts and animation)"""
    yr = year_of(it)
    for v in variants(as_text(it.get('title'))):
        cands = idx.get(v, [])
        if want == 'films': cands = [c for c in cands if c[2] in ('movie', 'tvMovie') and c[3] >= 150]
        else: cands = [c for c in cands if (c[2] == 'short' or c[4]) and c[3] >= 20]
        if not cands: continue
        if yr:
            near = [c for c in cands if abs(c[1] - yr) <= 1]
            if near: return max(near, key=lambda c: c[3])
            continue
        # no year on the item: only take it when one title clearly stands out
        cands.sort(key=lambda c: -c[3])
        if len(cands) == 1 or cands[0][3] >= 8 * cands[1][3]: return cands[0]
    return None

# ---------- playable file ----------
PREF = ['h.264 HD', 'h.264', 'h.264 IA', 'MPEG4', '512Kb MPEG4']
def best_file(ident):
    d = get_json('https://archive.org/metadata/' + urllib.parse.quote(ident))
    if not d or d.get('is_dark') or not d.get('files'): return None
    files = [f for f in d['files'] if f.get('name', '').lower().endswith('.mp4') and not SKIP_WORDS.search(f.get('name', '')) and not RIP.search(f.get('name', ''))]
    if not files: return None
    def rank(f):
        fmt = f.get('format', '')
        return (PREF.index(fmt) if fmt in PREF else len(PREF), -int(f.get('size') or 0))
    f = min(files, key=rank)
    if int(f.get('size') or 0) < 5_000_000: return None      # too small to be the real thing
    return 'https://archive.org/download/%s/%s' % (urllib.parse.quote(ident), urllib.parse.quote(f['name']))

# ---------- what counts as free ----------
# US copyright: everything released in 1930 or earlier is public domain (as of 2026).
# Later films only when they're well known to be public domain (copyright never renewed or never filed),
# or released by their makers under a free license (Blender's open movies). Everything else the Archive has
# under "feature films" is left out, because that collection also holds plenty of uploads that aren't free.
PD_YEAR = int(time.strftime('%Y')) - 96
ALLOW = [
    # features
    ('Night of the Living Dead', 1968), ('His Girl Friday', 1940), ('Charade', 1963), ('Detour', 1945), ('D.O.A.', 1949),
    ('The Little Shop of Horrors', 1960), ('Carnival of Souls', 1962), ('Plan 9 from Outer Space', 1957), ('McLintock!', 1963),
    ('The Stranger', 1946), ('Scarlet Street', 1945), ('Kansas City Confidential', 1952), ('The Hitch-Hiker', 1953),
    ('Meet John Doe', 1941), ('The Man with the Golden Arm', 1955), ('Royal Wedding', 1951), ("Father's Little Dividend", 1950),
    ('Penny Serenade', 1941), ('The Last Man on Earth', 1964), ('House on Haunted Hill', 1959), ("The Brain That Wouldn't Die", 1962),
    ('Dementia 13', 1963), ('Santa Claus Conquers the Martians', 1964), ('Angel and the Badman', 1947), ('Topper Returns', 1941),
    ('Beat the Devil', 1953), ('Suddenly', 1954), ('The Terror', 1963), ('A Bucket of Blood', 1959), ('Reefer Madness', 1936),
    ('Jungle Book', 1942), ('Rain', 1932), ('Love Affair', 1939), ('Of Human Bondage', 1934), ('The Inspector General', 1949),
    ('Africa Screams', 1949), ('Gulliver\'s Travels', 1939), ('The Kid', 1921), ('Teenagers from Outer Space', 1959),
    ('The Amazing Transparent Man', 1960), ('Indestructible Man', 1956), ('The Snow Creature', 1954), ('Bride of the Monster', 1955),
    ('Life with Father', 1947),
    # cartoons
    ('Superman', 1941), ('The Mechanical Monsters', 1941), ('Billion Dollar Limited', 1942), ('The Arctic Giant', 1942),
    ('The Bulleteers', 1942), ('The Magnetic Telescope', 1942), ('Electric Earthquake', 1942), ('Volcano', 1942),
    ('Terror on the Midway', 1942), ('Showdown', 1942), ('Destruction, Inc.', 1942), ('The Mummy Strikes', 1943),
    ('The Underground World', 1943), ('Secret Agent', 1943),
    ('Popeye the Sailor Meets Sindbad the Sailor', 1936), ("Popeye the Sailor Meets Ali Baba's Forty Thieves", 1937),
    ('Aladdin and His Wonderful Lamp', 1939),
    # Blender Foundation open movies (Creative Commons)
    ('Big Buck Bunny', 2008), ('Elephants Dream', 2006), ('Sintel', 2010), ('Tears of Steel', 2012), ('Cosmos Laundromat', 2015),
    ('Caminandes: Llama Drama', 2013), ('Caminandes 2: Gran Dillama', 2013), ('Caminandes 3: Llamigos', 2016),
    ('Agent 327: Operation Barbershop', 2017), ('Spring', 2019), ('Sprite Fright', 2021), ('Charge', 2022), ('Wing It!', 2023),
]
ALLOWED = {(norm(t), y) for t, y in ALLOW if y}
def is_free(m, archive_title):
    tt, yr = m[0], m[1]
    if yr <= PD_YEAR: return True
    return any((v, yr) in ALLOWED for v in variants(archive_title))

def main():
    idx = load_imdb()
    sources = [
        ('films', 'collection:feature_films AND mediatype:movies'),
        ('cartoons', 'collection:classic_cartoons AND mediatype:movies'),
        ('cartoons', 'mediatype:movies AND title:("Big Buck Bunny" OR "Sintel" OR "Elephants Dream" OR "Tears of Steel" OR "Cosmos Laundromat" OR "Caminandes" OR "Spring" OR "Agent 327" OR "Charge" OR "Wing It")'),
    ]
    best = {}   # tt -> (votes, kind, [(downloads, ident)])
    for kind, q in sources:
        items = scrape(q)
        hit = 0
        for it in items:
            if SKIP_WORDS.search(as_text(it.get('title'))) or SKIP_WORDS.search(as_text(it.get('subject'))): continue
            if RIP.search(it['identifier']) or RIP.search(as_text(it.get('title'))): continue
            m = match(it, idx, kind)
            if not m or not is_free(m, as_text(it.get('title'))): continue
            hit += 1
            tt = m[0]
            b = best.setdefault(tt, [m[3], kind, []])
            b[2].append((int(it.get('downloads') or 0), it['identifier']))
        print(f'{kind}: {len(items)} archive items, {hit} matched, {len(best)} titles so far')

    def resolve(tt):
        for _, ident in sorted(best[tt][2], reverse=True)[:3]:   # most-watched copy first
            u = best_file(ident)
            if u: return tt, u, ident
        return tt, None, None

    out = {}
    with cf.ThreadPoolExecutor(12) as ex:
        for n, (tt, u, ident) in enumerate(ex.map(resolve, list(best))):
            if u: out[tt] = [u, ident, best[tt][1]]
            if n % 500 == 0: print('  files checked:', n)
    films = sorted([t for t in out if out[t][2] == 'films'], key=lambda t: -best[t][0])
    toons = sorted([t for t in out if out[t][2] == 'cartoons'], key=lambda t: -best[t][0])
    os.makedirs(os.path.join(OUT, 'free'), exist_ok=True)
    w = lambda name, obj: json.dump(obj, open(os.path.join(OUT, 'free', name), 'w'), separators=(',', ':'))
    w('map.json', out); w('films.json', films); w('cartoons.json', toons)
    w('meta.json', {'built': time.strftime('%Y-%m-%d'), 'films': len(films), 'cartoons': len(toons)})
    print('done:', len(films), 'films,', len(toons), 'cartoons')
    print('top films:', films[:15]); print('top cartoons:', toons[:15])

main()
