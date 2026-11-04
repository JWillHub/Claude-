"""Build per-show IMDb episode rating files (and film scores) for Reel Log.

Reads IMDb's non-commercial datasets (https://developer.imdb.com/non-commercial-datasets/):
  title.ratings.tsv.gz, title.episode.tsv.gz, title.basics.tsv.gz
and writes one small JSON file per TV show to <out>/tv/<last two digits>/<tconst>.json

Usage: python scripts/imdb_tv.py <dataset dir> <out dir>
Information courtesy of IMDb (https://www.imdb.com). Used with permission. Personal, non-commercial use.
"""
import gzip, json, os, sys, datetime
from collections import defaultdict

src, out = sys.argv[1], sys.argv[2]
MIN_VOTES = int(os.environ.get('MIN_VOTES', '1500'))   # only chart shows with at least this many IMDb votes
MOVIE_MIN = int(os.environ.get('MOVIE_MIN', '50'))     # film scores: films with at least this many votes
NA = '\\N'

def rows(name):
    with gzip.open(os.path.join(src, name), 'rt', encoding='utf-8', newline='') as f:
        f.readline()                                     # header
        for line in f:
            yield line.rstrip('\n').split('\t')

ratings = {}
for row in rows('title.ratings.tsv.gz'):
    try: ratings[row[0]] = (float(row[1]), int(row[2]))
    except (ValueError, IndexError): pass
print('ratings', len(ratings), flush=True)

shows, eps = defaultdict(list), set()
for row in rows('title.episode.tsv.gz'):
    if len(row) < 4: continue
    t, parent, s, e = row[:4]
    pr = ratings.get(parent)
    if not pr or pr[1] < MIN_VOTES or s == NA or e == NA: continue
    try: s, e = int(s), int(e)
    except ValueError: continue
    if s < 1 or s > 300: continue                        # skip specials (season 0) and junk
    shows[parent].append((s, e, t)); eps.add(t)
print('shows', len(shows), 'episodes', len(eps), flush=True)

need, names, films = eps | set(shows), {}, defaultdict(dict)
for row in rows('title.basics.tsv.gz'):
    if len(row) > 1 and row[1] in ('movie', 'tvMovie'):
        r = ratings.get(row[0])
        if r and r[1] >= MOVIE_MIN: films[row[0][-3:]][row[0]] = [r[0], r[1]]
    if row[0] in need:
        names[row[0]] = (row[2] if len(row) > 2 else '', row[5] if len(row) > 5 and row[5] != NA else '',
                         row[6] if len(row) > 6 and row[6] != NA else '')

count = 0
for parent, lst in shows.items():
    seasons, rated = defaultdict(list), 0
    for s, e, t in sorted(set(lst)):
        r = ratings.get(t)
        if r: rated += 1
        seasons[str(s)].append([e, r[0] if r else None, r[1] if r else 0, names.get(t, ('',))[0]])
    if not rated: continue
    pr, nm = ratings[parent], names.get(parent, ('', '', ''))
    data = {'id': parent, 'title': nm[0], 'year': nm[1], 'end': nm[2], 'r': pr[0], 'v': pr[1], 's': seasons}
    d = os.path.join(out, 'tv', parent[-2:])
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, parent + '.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, separators=(',', ':'), ensure_ascii=False)
    count += 1

os.makedirs(os.path.join(out, 'm'), exist_ok=True)
nfilms = 0
for shard, d in films.items():
    with open(os.path.join(out, 'm', shard + '.json'), 'w') as f:
        json.dump(d, f, separators=(',', ':'))
    nfilms += len(d)
print('wrote', nfilms, 'film scores', flush=True)

with open(os.path.join(out, 'meta.json'), 'w') as f:
    json.dump({'films': nfilms, 'built': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%d'), 'shows': count, 'minVotes': MIN_VOTES}, f)
with open(os.path.join(out, 'README.md'), 'w') as f:
    f.write('IMDb episode ratings for Reel Log, rebuilt nightly by .github/workflows/imdb-tv.yml on main.\n\n'
            'Information courtesy of IMDb (https://www.imdb.com). Used with permission. Personal, non-commercial use only.\n')
print('wrote', count, 'shows', flush=True)
