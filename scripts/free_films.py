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
        if sep in t: out += [t.split(sep)[0], t.split(sep)[-1]]
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
    files = [f for f in d['files'] if f.get('name', '').lower().endswith('.mp4') and not SKIP_WORDS.search(f.get('name', ''))]
    if not files: return None
    def rank(f):
        fmt = f.get('format', '')
        return (PREF.index(fmt) if fmt in PREF else len(PREF), -int(f.get('size') or 0))
    f = min(files, key=rank)
    if int(f.get('size') or 0) < 5_000_000: return None      # too small to be the real thing
    return 'https://archive.org/download/%s/%s' % (urllib.parse.quote(ident), urllib.parse.quote(f['name']))

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
            m = match(it, idx, kind)
            if not m: continue
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
