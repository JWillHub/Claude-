"""Builds Final Panel's curated data once a night: every hand-picked series, landmark issue and story arc,
already matched to Comic Vine, so the app shows those rows from one small file instead of hundreds of lookups.
Reads the lists straight from long-box/index.html so the app and this script never drift apart.
Uses the CV_KEY repository secret if there is one; otherwise it goes through the shared server, which holds the key."""
import ast, json, os, re, sys, time, urllib.parse, urllib.request

KEY = os.environ.get('CV_KEY', '')
SHARED = 'https://long-box19c4.jwill-personalacc.workers.dev/'
SRC = open('long-box/index.html', encoding='utf-8').read()

def js_list(name):
    i = SRC.index('const ' + name + ' = [') + len('const ' + name + ' = ')
    depth, j = 0, i
    while True:
        c = SRC[j]
        if c == '[': depth += 1
        elif c == ']':
            depth -= 1
            if depth == 0: break
        elif c in '\'"':
            q = c; j += 1
            while SRC[j] != q: j += 2 if SRC[j] == '\\' else 1
        j += 1
    return ast.literal_eval(SRC[i:j + 1])

ENG = js_list('ENG')
eng_pub = lambda n: not n or any(k in n.lower() for k in ENG)
norm = lambda s: re.sub(r'[^a-z0-9]+', ' ', re.sub(r'^the\s+', '', (s or '').lower())).strip()

class Limited(Exception): pass
PREV = {}
try:
    with urllib.request.urlopen('https://raw.githubusercontent.com/JWillHub/Claude-/comics-data/curated.json', timeout=30) as r: PREV = json.load(r)
except Exception as e: print('no previous build', e)
limited = set()   # Comic Vine resources that hit the hourly limit this run

last = [0.0]
def cv(path, **q):
    res = path.split('/')[0]
    if res in limited: raise Limited(res)
    for attempt in range(4):
        wait = last[0] + 1.2 - time.time()
        if wait > 0: time.sleep(wait)
        last[0] = time.time()
        url = ('https://comicvine.gamespot.com/api/' + path + '?' + urllib.parse.urlencode({**q, 'api_key': KEY, 'format': 'json'})) if KEY \
            else SHARED + path + '?' + urllib.parse.urlencode(q)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'FinalPanel/1.0 (nightly curated build)'}), timeout=40) as r:
                d = json.load(r)
        except Exception as e:
            print('retry', path, e); time.sleep(10 * (attempt + 1)); continue
        if d.get('status_code') == 107:
            if attempt >= 1: limited.add(res); raise Limited(res)
            print('rate limited, waiting'); time.sleep(90); continue
        if d.get('status_code') != 1: raise SystemExit(f'Comic Vine error {d.get("status_code")} {d.get("error")}')
        return d
    raise SystemExit('Comic Vine unreachable')

def img(im, size):
    u = im and (im.get(size) or im.get('medium_url') or im.get('small_url') or im.get('original_url'))
    return u if u and not re.search(r'blank|default\.(png|jpg|gif)', u.split('/')[-1], re.I) else None
ISSUE_F = 'id,name,issue_number,volume,cover_date,store_date,image'
VOL_F = 'id,name,start_year,count_of_issues,publisher,image,deck'
def slim_issue(x):
    v = x.get('volume') or {}
    num = x.get('issue_number')
    title = (v.get('name') or x.get('name') or 'Untitled') + (f' #{num}' if num not in (None, '') else '')
    date = x.get('cover_date') or x.get('store_date') or ''
    return {'id': x['id'], 'vol': {'id': v.get('id'), 'name': v.get('name')} if v else None, 'num': num or '', 'name': x.get('name') or '',
            'title': title, 'year': date[:4], 'date': date, 'store': x.get('store_date') or '',
            'poster': img(x.get('image'), 'medium_url'), 'big': img(x.get('image'), 'super_url')}
def slim_vol(v):
    return {'id': v['id'], 'name': v.get('name') or 'Untitled', 'start': v.get('start_year') or '', 'count': int(v.get('count_of_issues') or 0),
            'pub': (v.get('publisher') or {}).get('name', ''), 'poster': img(v.get('image'), 'medium_url'), 'big': img(v.get('image'), 'super_url'), 'deck': v.get('deck') or ''}
def num_of(n):
    m = re.sub(r'[^\d.]', '', str(n))
    try: return float(m)
    except ValueError: return 1e9

vols, issues, arcs, pubs = {}, {}, {}, {}
def resolve_vol(name, year):
    k = f'{name}:{year}'
    if k in vols: return vols[k]
    if (PREV.get('vols') or {}).get(k) and not k.startswith('The Saga of the Swamp Thing'):
        vols[k] = PREV['vols'][k]; pubs[vols[k]['id']] = vols[k]['pub']; return vols[k]
    d = cv('search/', query=name, resources='volume', limit=10, field_list=VOL_F)
    cand = [slim_vol(v) for v in d.get('results') or [] if eng_pub((v.get('publisher') or {}).get('name'))]
    def score(v):
        n = norm(v['name']); w = norm(name)
        return (1000 if n == w else 200 if w in n else 0) - abs(int(v['start'] or 0) - year) * 10 + min(v['count'], 100) / 10
    best = max(cand, key=score, default=None)
    if not best or abs(int(best['start'] or 0) - year) > 1:
        q = re.sub(r'^the\s+', '', name, flags=re.I).replace(',', '')
        more = cv('volumes/', filter='name:' + q, field_list=VOL_F, limit=100).get('results') or []
        cand += [slim_vol(v) for v in more if eng_pub((v.get('publisher') or {}).get('name'))]
        best = max(cand, key=score, default=None)
    vols[k] = best if best and score(best) > 100 else None
    if vols[k]: pubs[vols[k]['id']] = vols[k]['pub']
    print('series', k, '->', vols[k] and vols[k]['id'])
    return vols[k]
def resolve_issue(name, year, num):
    v = resolve_vol(name, year)
    k = f'{name}:{year}:{num}'
    if not v: issues[k] = None; return
    if (PREV.get('issues') or {}).get(k) and ((PREV['issues'][k].get('vol') or {}).get('id') == v['id']): issues[k] = PREV['issues'][k]; return
    d = cv('issues/', filter=f'volume:{v["id"]},issue_number:{num}', field_list=ISSUE_F, limit=5)
    r = d.get('results') or []
    issues[k] = slim_issue(r[0]) if r else None
    print('issue', k, '->', issues[k] and issues[k]['id'])
def resolve_arc(name, pub):
    if (PREV.get('arcs') or {}).get(name) and PREV.get('arcfix') == 2: arcs[name] = PREV['arcs'][name]; return
    q = re.sub(r'^the\s+', '', name, flags=re.I).replace(',', '')
    d = cv('story_arcs/', filter='name:' + q, field_list='id,name,publisher,count_of_isssue_appearances,count_of_issue_appearances', limit=50)
    cnt = lambda a: int(a.get('count_of_isssue_appearances') or a.get('count_of_issue_appearances') or 0)
    def score(a):
        return (1000 if norm(a['name']) == norm(name) else 300 if norm(name) in norm(a['name']) else 0) \
            + (200 if pub.lower() in ((a.get('publisher') or {}).get('name') or '').lower() else 0) + min(cnt(a), 150)
    best = max(d.get('results') or [], key=score, default=None)
    if not best or score(best) < 300:
        word = max(re.findall(r"[A-Za-z']+", q), key=len)
        d = cv('story_arcs/', filter='name:' + word, field_list='id,name,publisher,count_of_isssue_appearances,count_of_issue_appearances', limit=100)
        best = max(d.get('results') or [], key=score, default=None)
    if not best or score(best) < 300: arcs[name] = None; print('arc', name, '-> not found'); return
    det = cv(f'story_arc/4045-{best["id"]}/', field_list='id,name,issues')
    ids = [x['id'] for x in (det.get('results') or {}).get('issues') or []][:300]
    out = []
    for i in range(0, len(ids), 100):
        out += [slim_issue(x) for x in cv('issues/', filter='id:' + '|'.join(map(str, ids[i:i + 100])), field_list=ISSUE_F, limit=100).get('results') or []]
    need = sorted({x['vol']['id'] for x in out if x['vol'] and x['vol']['id'] not in pubs})
    for i in range(0, len(need), 100):
        for v in cv('volumes/', filter='id:' + '|'.join(map(str, need[i:i + 100])), field_list='id,publisher', limit=100).get('results') or []:
            pubs[v['id']] = (v.get('publisher') or {}).get('name', '')
    out = [x for x in out if not x['vol'] or eng_pub(pubs.get(x['vol']['id'], ''))]
    out = [x for x in out if not re.search(r'deluxe|anniversary|omnibus|from the dc vault|facsimile|giant|treasury|collect|tpb|hc\b|edition', (x['vol'] or {}).get('name') or '', re.I)]
    years = sorted(int(x['date'][:4]) for x in out if x['date'][:4].isdigit())
    if years:
        mid = years[len(years) // 2]
        out = [x for x in out if not x['date'][:4].isdigit() or abs(int(x['date'][:4]) - mid) <= 3]
    out.sort(key=lambda x: (x['date'] or '9999', num_of(x['num'])))
    arcs[name] = {'id': best['id'], 'issues': out}
    print('arc', name, '->', best['id'], len(out), 'issues')

def safe(fn, kind, k, *a):
    try: fn(*a)
    except Limited as e:
        prev = (PREV.get(kind) or {}).get(k)
        {'vols': vols, 'issues': issues, 'arcs': arcs}[kind][k] = prev
        print('limit reached for', e, '- kept last build for', k)
for n in ['CLASSIC_RUNS', 'MODERN_RUNS', 'EVENTS', 'DC_ABSOLUTE', 'DC_RUNS']:
    for name, year in js_list(n): safe(resolve_vol, 'vols', f'{name}:{year}', name, year)
for n in ['LANDMARKS', 'DC_LANDMARKS']:
    for name, year, num in js_list(n):
        if f'{name}:{year}:{num}' not in issues: safe(resolve_issue, 'issues', f'{name}:{year}:{num}', name, year, num)
for k, name, pub, g in js_list('ARCS'): safe(resolve_arc, 'arcs', name, name, pub)
for k, v in (PREV.get('pubs') or {}).items(): pubs.setdefault(int(k), v)

chars, fresh = dict(PREV.get('chars') or {}), {}
for name in dict.fromkeys(js_list('CHARS') + js_list('DC_CHARS')):
    try:
        out, total = [], 1
        for page in (1, 2):
            d = cv('search/', query=name, resources='volume', limit=10, page=page, field_list=VOL_F)
            total = max(1, min(10, -(-int(d.get('number_of_total_results') or 0) // 10)))
            out += [slim_vol(v) for v in d.get('results') or [] if eng_pub((v.get('publisher') or {}).get('name')) and int(v.get('count_of_issues') or 0) > 0]
            if page >= total: break
        seen = set(); out = [v for v in out if not (v['id'] in seen or seen.add(v['id']))]
        for v in out: pubs[v['id']] = v['pub']
        chars[name] = {'pages': min(2, total), 'total': total, 'vols': out}
        print('character', name, len(out))
    except Limited: print('limit reached - kept last build for', name)
def new_issues(filt):
    d = cv('issues/', filter=filt, sort='store_date:desc', field_list=ISSUE_F, limit=100)
    out = [slim_issue(x) for x in d.get('results') or []]
    need = sorted({x['vol']['id'] for x in out if x['vol'] and x['vol']['id'] not in pubs})
    for i in range(0, len(need), 100):
        for v in cv('volumes/', filter='id:' + '|'.join(map(str, need[i:i + 100])), field_list='id,publisher', limit=100).get('results') or []:
            pubs[v['id']] = (v.get('publisher') or {}).get('name', '')
    return [x for x in out if not x['vol'] or eng_pub(pubs.get(x['vol']['id'], ''))]
day = lambda n: time.strftime('%Y-%m-%d', time.gmtime(time.time() - n * 86400))
for key, filt in (('week', f'store_date:{day(7)}|{day(-1)}'), ('ones', f'store_date:{day(30)}|{day(-1)},issue_number:1')):
    try: fresh[key] = new_issues(filt); print(key, len(fresh[key]))
    except Limited: print('limit reached - no', key)

os.makedirs(sys.argv[1], exist_ok=True)
with open(os.path.join(sys.argv[1], 'curated.json'), 'w') as f:
    json.dump({'built': time.strftime('%Y-%m-%d'), 'arcfix': 2, 'vols': vols, 'issues': issues, 'arcs': arcs, 'chars': chars, 'fresh': fresh,
               'pubs': {str(k): v for k, v in pubs.items()}}, f, separators=(',', ':'))
print('done', len(vols), 'series', len(issues), 'issues', len(arcs), 'arcs')
