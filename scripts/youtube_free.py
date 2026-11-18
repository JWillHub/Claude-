#!/usr/bin/env python3
"""Free YouTube rows for Rolling Credits, from a fixed list of official channels (scripts/yt_channels.json).

Uses the YouTube Data API (key in the YOUTUBE_API_KEY secret). Keeps only public, embeddable, non-live videos
that play in the US and fit each row's length (so trailers and clips drop out). Writes OUT/yt/rows.json:
  { "built": "YYYY-MM-DD", "rows": [ { "key", "title", "sub", "items": [[videoId, title, channel, seconds], ...] } ] }
Usage: youtube_free.py OUT_DIR
"""
import json, os, re, sys, time, urllib.parse, urllib.request

KEY = os.environ['YOUTUBE_API_KEY']
OUT = sys.argv[1]
CFG = json.load(open(os.path.join(os.path.dirname(__file__), 'yt_channels.json')))
API = 'https://www.googleapis.com/youtube/v3/'
SKIP = re.compile(r'trailer|teaser|\bclip\b|#shorts|behind the scenes|\bpromo\b|sneak peek|live ?stream|\blive\b', re.I)
used = 0

def api(path, **p):
    global used
    p['key'] = KEY
    for k in range(4):
        try:
            with urllib.request.urlopen(API + path + '?' + urllib.parse.urlencode(p), timeout=30) as r:
                used += 1
                return json.load(r)
        except urllib.error.HTTPError as e:
            body = e.read().decode()[:300]
            if e.code in (400, 403, 404): print('  API error', e.code, path, body); return {}
            time.sleep(2 + 3 * k)
        except Exception as e:
            time.sleep(2 + 3 * k)
    return {}

def secs(iso):
    m = re.match(r'P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?', iso or '')
    if not m: return 0
    d, h, mi, s = (int(x or 0) for x in m.groups())
    return d * 86400 + h * 3600 + mi * 60 + s

def playlist_ids(pl, pages):
    ids, tok = [], None
    for _ in range(pages):
        p = dict(part='contentDetails', playlistId=pl, maxResults=50)
        if tok: p['pageToken'] = tok
        d = api('playlistItems', **p)
        ids += [i['contentDetails']['videoId'] for i in d.get('items', [])]
        tok = d.get('nextPageToken')
        if not tok: break
    return ids

def channel_videos(src, lo, hi):
    if isinstance(src, str): src = {'handle': src}
    if src.get('search'):        # find the channel by name when its handle isn't known
        hits = api('search', part='snippet', q=src['search'], type='channel', maxResults=10).get('items', [])
        hits = [h for h in hits if h['snippet']['title'].strip().lower() == src['expect'].lower()]
        if hits: src['id'] = hits[0]['snippet']['channelId']
        src.setdefault('handle', src['search'])
    if src.get('id'): ch = api('channels', part='snippet,contentDetails,statistics', id=src['id']).get('items', [])
    else: ch = api('channels', part='snippet,contentDetails,statistics', forHandle=src['handle']).get('items', [])
    if not ch: print(f'  {src["handle"]}: not found'); return []
    ch = ch[0]
    name = ch['snippet']['title']
    print(f'  {src["handle"]}: "{name}", {int(ch["statistics"].get("subscriberCount", 0)):,} subscribers, {ch["statistics"].get("videoCount")} videos')
    pls = []
    if src.get('playlist'):      # only the channel's playlists whose name matches, e.g. Warner Bros' "Full Movies"
        tok = None
        while True:
            p = dict(part='snippet,contentDetails', channelId=ch['id'], maxResults=50)
            if tok: p['pageToken'] = tok
            d = api('playlists', **p)
            for it in d.get('items', []):
                if src.get('show_playlists'): print('    (playlist)', it['snippet']['title'], it['contentDetails']['itemCount'] if 'contentDetails' in it else '')
                if re.search(src['playlist'], it['snippet']['title'], re.I) and not re.search('preview|trailer|clip', it['snippet']['title'], re.I):
                    pls.append(it['id']); print('    playlist:', it['snippet']['title'])
            tok = d.get('nextPageToken')
            if not tok: break
    else:
        pls = [ch['contentDetails']['relatedPlaylists']['uploads']]
    ids = []
    for pl in pls: ids += playlist_ids(pl, 10)
    ids = list(dict.fromkeys(ids))
    keep = []
    for i in range(0, len(ids), 50):
        d = api('videos', part='snippet,contentDetails,status,statistics', id=','.join(ids[i:i + 50]))
        for v in d.get('items', []):
            st, cd, sn = v['status'], v['contentDetails'], v['snippet']
            if st.get('privacyStatus') != 'public' or not st.get('embeddable') or sn.get('liveBroadcastContent') != 'none': continue
            rr = cd.get('regionRestriction', {})
            if ('allowed' in rr and 'US' not in rr['allowed']) or 'US' in rr.get('blocked', []): continue
            t = secs(cd.get('duration'))
            if not (lo * 60 <= t <= hi * 60) or SKIP.search(sn['title']): continue
            keep.append((int(v.get('statistics', {}).get('viewCount', 0)), [v['id'], sn['title'], name, t]))
    keep.sort(key=lambda x: -x[0])
    print(f'    {len(ids)} videos checked, {len(keep)} kept')
    return [x[1] for x in keep]

rows = []
for r in CFG['rows']:
    print('Row:', r['title'])
    per = [channel_videos(c, r['min'], r['max']) for c in r['channels']]
    items = []                      # take turns between channels, most-watched first, so one channel can't fill the row
    for k in range(max([len(p) for p in per] + [0])):
        for p in per:
            if k < len(p): items.append(p[k])
    rows.append({'key': r['key'], 'title': r['title'], 'sub': r['sub'], 'items': items[:80]})
    print(f'  row total: {len(items)} (kept 80)')

os.makedirs(os.path.join(OUT, 'yt'), exist_ok=True)
json.dump({'built': time.strftime('%Y-%m-%d'), 'rows': rows}, open(os.path.join(OUT, 'yt', 'rows.json'), 'w'), separators=(',', ':'))
print('API requests used:', used)
for r in rows: print(r['key'], [x[1][:50] for x in r['items'][:6]])
