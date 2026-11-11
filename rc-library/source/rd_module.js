/* ================= Real-Debrid library (this copy only) ================= */
// Lists what is already in your own Real-Debrid account, matches each item to a TMDB title,
// and adds a Play button to that title's card. It never searches for or adds anything new.
const K_RD = 'rclib.rdkey', K_LIB = 'rclib.rdlib';
// Running inside the Android app? There, requests go straight to Real-Debrid and Play opens the native player.
const NATIVE = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
// The native player, found when it's needed (the app's bridge may finish setting up after this page loads)
function callNative(plugin, method, opts){
  const C = window.Capacitor || {};
  if(C.Plugins && C.Plugins[plugin] && typeof C.Plugins[plugin][method] === 'function') return C.Plugins[plugin][method](opts);
  if(typeof C.registerPlugin === 'function') return C.registerPlugin(plugin)[method](opts);
  if(typeof C.nativePromise === 'function') return C.nativePromise(plugin, method, opts);
  return Promise.reject(new Error('this part of the app is not available'));
}
// your Player settings ride along with every video
const playerPrefs = () => ({ autoSkip:!!store.get(K_PL_SKIP, false), midnightDefault:!!store.get(K_PL_MID, false),
  audioLang:store.get(K_PL_AUD, ''), textLang:store.get(K_PL_TXT, ''), subSize:store.get(K_PL_SIZE, 'm'), subStyle:store.get(K_PL_STYLE, 'shadow') });
const playerPlay = opts => callNative('RCPlayer', 'play', { ...playerPrefs(), ...opts });

// ---- App updates (Android app only): a dot on Settings when a newer build is out, and an Update button there ----
const APP_BUILD = parseInt('__BUILDNUM__', 10);
const REL = 'https://github.com/JWillHub/Claude-/releases/download/android-latest/';
let latestBuild = 0;
async function checkForUpdate(){
  if(!NATIVE || !APP_BUILD) return;
  try{
    const r = await fetch(REL + 'version.json?t=' + Date.now());
    const j = JSON.parse(await r.text());
    latestBuild = +j.build || 0;
  }catch(e){ return; }
  $('#setTab')?.classList.toggle('upd', latestBuild > APP_BUILD);
  const box = document.getElementById('appupd'); if(box) fillUpdate(box.closest('.sbody') || document);
}
let updating = false;   // while a download runs, don't redraw the version row (that would wipe its progress)
function fillUpdate(b){
  const box = $('#appupd', b); if(!box || !NATIVE || updating) return;
  const newer = latestBuild > APP_BUILD;
  box.innerHTML = `<span class="sic"><svg viewBox="0 0 24 24"><rect x="6.5" y="2.5" width="11" height="19" rx="2.5"/><path d="M10.5 18.5h3"/></svg></span>
    <span class="slab">Version ${APP_BUILD || '?'}</span>
    ${newer ? `<button class="updbtn" id="appupdbtn">Update to ${latestBuild}</button>` : ''}`;
  const btn = $('#appupdbtn', box); if(!btn) return;
  btn.onclick = async () => {
    btn.disabled = true; btn.textContent = 'Downloading…'; updating = true;
    const url = REL + 'rolling-credits.apk?v=' + latestBuild;
    const C = window.Capacitor || {}, P = (C.Plugins && C.Plugins.RCUpdate) || (C.registerPlugin && C.registerPlugin('RCUpdate'));
    let handle = null, last = Date.now(), dog = null, gone = false;
    const fallback = why => {
      if(gone) return; gone = true; updating = false; clearInterval(dog); handle && handle.remove && handle.remove();
      btn.disabled = false; btn.textContent = 'Update';
      box.insertAdjacentHTML('beforeend', `<div class="updfail"><span>${esc(why)}</span><button id="updweb">Download in browser instead</button></div>`);
      $('#updweb', box).onclick = () => callNative('RCUpdate', 'openInBrowser', { url }).catch(() => { location.href = url; });
    };
    try{ if(P && P.addListener) handle = await P.addListener('progress', e => { last = Date.now(); btn.textContent = e.pct >= 0 ? `Downloading… ${e.pct}%` : `Downloading… ${e.mb} MB`; }); }catch(e){}
    dog = setInterval(() => { if(Date.now() - last > 25000) fallback('The download stalled.'); }, 3000);   // nothing for 25 s: offer the browser route
    try{
      const r = await callNative('RCUpdate', 'install', { url });
      if(gone) return;
      updating = false; clearInterval(dog); handle && handle.remove && handle.remove();
      if(r && r.needsPermission){ btn.disabled = false; btn.textContent = 'Update'; toast('Turn on "Allow from this source", come back, then tap Update again'); return; }
      btn.textContent = 'Installing…';
    }catch(e){ fallback(e.message || String(e)); }
  };
}
if(NATIVE){ setTimeout(checkForUpdate, 2500); document.addEventListener('visibilitychange', () => { if(!document.hidden) checkForUpdate(); }); }
// In a browser, Real-Debrid doesn't allow direct calls, so requests go through your small rc-relay Cloudflare worker
const RD_API = NATIVE ? 'https://api.real-debrid.com/rest/1.0' : 'https://rc-relay-3cfe.jwill-personalacc.workers.dev/rd';
let rdLib = store.get(K_LIB, null) || { synced:0, items:{} };
const saveLib = () => { if(!store.set(K_LIB, rdLib)) toast('Could not save the library list — storage is full.'); };
const VIDEO = /\.(mkv|mp4|m4v|avi|mov|webm|ts|wmv)$/i;
// Resume points: { "torrentId|file path": { t:seconds, d:duration, at, tid, id, title, label, link } } and finished files
const K_PROG = 'rclib.progress', K_DONE = 'rclib.done';
let prog = store.get(K_PROG, {}), done = store.get(K_DONE, {});
function saveProg(){
  const ks = Object.keys(prog).sort((a, c) => prog[c].at - prog[a].at);
  ks.slice(60).forEach(k => delete prog[k]);          // keep the 60 most recent
  store.set(K_PROG, prog); store.set(K_DONE, done);
}
const clock = s => { s = Math.floor(s); const h = Math.floor(s / 3600), mm = Math.floor(s % 3600 / 60), ss = s % 60; return (h ? h + ':' + String(mm).padStart(2, '0') : mm) + ':' + String(ss).padStart(2, '0'); };
// The library's own match for a resume point (title, poster), not whatever happens to be loaded
const progMeta = meta => { const it = rdLib.items[meta.tid]; const mm = it && it.m || {}; return { name:mm.title || '', poster:mm.poster || null, year:mm.year || '' }; };
const progOfTorrent = tid => Object.values(prog).filter(p => p.tid === tid).sort((a, c) => c.at - a.at)[0] || null;

// The token goes in the address so the browser can send each request straight away (a custom header would make it
// ask the relay for permission first, an extra round trip). If the relay is an older copy that only reads the header, fall back.
let rdHeaderMode = false;
async function rd(path, opt = {}){
  const key = store.get(K_RD, ''); if(!key) throw new Error('rdnokey');
  const base = new URL(RD_API + path);
  Object.entries(opt.q || {}).forEach(([k, v]) => base.searchParams.set(k, v));
  const send = hdr => {
    const u = new URL(base), init = {};
    if(hdr) init.headers = { Authorization:'Bearer ' + key }; else u.searchParams.set('auth_token', key);
    if(opt.form){ init.method = 'POST'; init.body = new URLSearchParams(opt.form).toString(); init.headers = { ...(init.headers || {}), 'Content-Type':'application/x-www-form-urlencoded' }; }
    return fetch(u, init);
  };
  let r;
  try{
    r = await send(rdHeaderMode);
    if(!rdHeaderMode && r.status === 401){
      const j = await r.clone().json().catch(() => ({}));
      if(j.error === 'No token'){ rdHeaderMode = true; r = await send(true); }
    }
  }catch(e){ throw new Error('rdblocked'); }
  if(r.status === 401) throw new Error('rdbadkey');
  if(r.status === 403) throw new Error('rddenied');
  if(r.status === 204) return null;
  if(!r.ok){ let msg = 'http ' + r.status; try{ const j = await r.json(); if(j && j.error) msg = String(j.error).replace(/_/g, ' '); }catch(e){} throw new Error(msg); }
  return r.json();
}
const rdErr = e => e.message === 'rdnokey' ? 'Add your Real-Debrid API token in Settings.'
  : e.message === 'rdbadkey' ? 'Real-Debrid rejected the token. Check it in Settings.'
  : e.message === 'rddenied' ? 'Real-Debrid refused the request. Check that your subscription is active.'
  : e.message === 'rdblocked' ? 'Could not reach Real-Debrid. Check your connection and try again.'
  : ['nokey', 'badkey', 'blocked'].includes(e.message) ? errText(e)
  : 'Real-Debrid returned an error (' + esc(e.message) + '). Try again in a minute.';

// "The.Matrix.1999.1080p.BluRay.x264" → { title:'The Matrix', year:'1999', tv:false }
const CUT = /\b(S\d{1,2}(E\d{1,3})?|Season\s*\d+|\d{1,2}x\d{2}|2160p|1080p|720p|480p|4k|uhd|blu ?ray|brrip|bdrip|web ?dl|webrip|web|hdtv|dvdrip|remux|x26[45]|h ?26[45]|hevc|10bit|complete|proper|repack|extended|unrated|imax|hdr|multi)\b/i;
function parseName(n){
  let t = String(n || '').replace(VIDEO, '').replace(/^\s*(\[[^\]]*\]|www\.\S+)\s*[-–]?\s*/i, '').replace(/[._]+/g, ' ').replace(/\s+/g, ' ').trim();
  const tv = /\bS\d{1,2}(E\d{1,3})?\b|\bSeason\s*\d+|\b\d{1,2}x\d{2}\b|\bComplete Series\b/i.test(t);
  const c = t.search(CUT); let head = c > 0 ? t.slice(0, c) : t;
  let year = null, at = -1;
  for(const m of head.matchAll(/(?:^|[\s(\[])((?:19|20)\d{2})(?=[\s)\]]|$)/g)) if(m.index > 0){ year = m[1]; at = m.index; }
  if(at > 0) head = head.slice(0, at);
  const title = head.replace(/[\[(][^\])]*[\])]/g, ' ').replace(/[\s\-–:(\[]+$/, '').replace(/\s+/g, ' ').trim() || t;
  return { title, year, tv };
}
const epOf = p => { const m = String(p).match(/S(\d{1,2})\s?E(\d{1,3})/i) || String(p).match(/\b(\d{1,2})x(\d{2})\b/); return m ? { s:+m[1], e:+m[2] } : null; };
const seasonOf = n => { const m = String(n).match(/\bS(\d{1,2})(?!\d|\s?E\d)/i) || String(n).match(/Season\s*(\d+)/i); return m ? 'Season ' + +m[1] : ''; };
const qualOf = n => { const m = String(n).match(/2160p|1080p|720p|480p|\b4k\b/i); return m ? m[0].toLowerCase().replace('4k', '2160p') : ''; };
const fmtSize = x => x ? (x >= 1e9 ? (x / 1e9).toFixed(1) + ' GB' : Math.round(x / 1e6) + ' MB') : '';
const baseName = p => String(p).split('/').pop();

const libMeta = m => ({ id:m.id, title:m.title, year:m.year, poster:m.poster, genres:m.genres || [], vote:m.vote || 0, votes:m.votes || 0, date:m.date || '' });
async function matchItem(it){
  const q = parseName(it.name);
  const path = q.tv ? '/search/tv' : '/search/movie', yk = q.tv ? 'first_air_date_year' : 'year';
  const tries = q.year ? [{ query:q.title, [yk]:q.year }, { query:q.title }] : [{ query:q.title }];
  for(const params of tries){
    const d = await tmdb(path, { include_adult:'false', ...params });
    const r = (d.results || [])[0];
    if(r) return libMeta(q.tv ? slimTV(r) : slim(r));
  }
  return null;
}

let syncing = null;
function syncLib(){
  if(syncing) return syncing;
  syncing = (async () => {
    const all = [];
    for(let page = 1; page <= 40; page++){
      const list = await rd('/torrents', { q:{ page, limit:500 } }) || [];
      all.push(...list);
      if(list.length < 500) break;
    }
    const items = {};
    for(const t of all){
      if(t.status !== 'downloaded') continue;
      const old = rdLib.items[t.id];
      items[t.id] = old && old.name === t.filename ? old : { name:t.filename, bytes:t.bytes, added:t.added };
    }
    rdLib.items = items;
    Object.keys(infoCache).forEach(t => { if(!items[t]) delete infoCache[t]; }); store.set(K_TINFO, infoCache);
    // things removed from Real-Debrid also leave Continue watching
    const goneLinks = new Set();
    Object.keys(prog).forEach(k => { const p = prog[k]; if(p.tid && !items[p.tid]){ if(p.link) goneLinks.add(p.link); delete prog[k]; } });
    goneLinks.forEach(l => delete linkCache[l]); store.set(K_LINKS, linkCache); saveProg();
    let fatal = null;
    await Promise.all(Object.values(items).filter(it => it.m === undefined).map(async it => {
      if(fatal) return;
      try{ it.m = await matchItem(it); }catch(e){ if(['nokey', 'badkey', 'blocked'].includes(e.message)) fatal = e; }
    }));
    rdLib.synced = Date.now(); saveLib();
    if(fatal) throw fatal;
    return rdLib;
  })();
  syncing.finally(() => { syncing = null; }).catch(() => {});
  return syncing;
}
// Matches saved before genres were stored get them filled in from TMDB (cached), so filters work
let filling = null;
function backfillMeta(){
  if(filling) return filling;
  const need = Object.values(rdLib.items).filter(it => it.m && !it.m.genres);
  if(!need.length) return Promise.resolve(false);
  filling = Promise.all(need.map(async it => { try{ const d = await detailsFor(it.m.id); it.m = { ...libMeta(d), poster:it.m.poster || d.poster }; }catch(e){} }))
    .then(() => { saveLib(); filling = null; return true; });
  return filling;
}
// Every matched title once (a film with two copies shows once)
function libTitles(){
  const at = new Map();
  Object.values(rdLib.items).forEach(it => { if(it.m && !at.has(it.m.id)) at.set(it.m.id, typeof it.m.id === 'string' ? { ...it.m, tv:true, tid:+String(it.m.id).slice(2) } : it.m); });
  const list = [...at.values()]; remember(list); return list;
}
const libSort = (list, sort) => list.sort(sort === 'rated' ? (a, c) => (c.vote || 0) - (a.vote || 0)
  : sort === 'newest' ? (a, c) => (c.date || '').localeCompare(a.date || '')
  : sort === 'popular' ? (a, c) => (c.votes || 0) - (a.votes || 0)
  : (a, c) => a.title.localeCompare(c.title, undefined, { numeric:true }));
// Remembered file lists (a day) and stream links (3 hours), so replaying or resuming skips the slow lookups
const K_LINKS = 'rclib.links', K_TINFO = 'rclib.tinfo', LINK_TTL = 3 * 3600e3, INFO_TTL = 24 * 3600e3;
let linkCache = store.get(K_LINKS, {}), infoCache = store.get(K_TINFO, {});
function trimCache(c, ttl, max){
  const now = Date.now(); Object.keys(c).forEach(k => { if(!c[k] || now - c[k].at > ttl) delete c[k]; });
  Object.keys(c).sort((a, b) => c[b].at - c[a].at).slice(max).forEach(k => delete c[k]);
}
const inflightInfo = new Map();
function torrentFiles(tid, fresh){
  const c = infoCache[tid];
  if(!fresh && c && Date.now() - c.at < INFO_TTL) return Promise.resolve(c.files);
  if(inflightInfo.has(tid)) return inflightInfo.get(tid);
  const p = (async () => {
    const info = await rd('/torrents/info/' + encodeURIComponent(tid));
    const sel = (info.files || []).filter(f => f.selected), links = info.links || [];
    let files = sel.length === links.length ? sel.map((f, i) => ({ path:f.path, bytes:f.bytes, link:links[i] })) : links.length === 1 ? [{ path:info.filename, bytes:info.bytes, link:links[0] }] : [];
    files = files.filter(f => VIDEO.test(f.path) && !/(^|[\W_])sample([\W_]|$)/i.test(f.path));
    infoCache[tid] = { files, at:Date.now() }; trimCache(infoCache, INFO_TTL, 80); store.set(K_TINFO, infoCache);
    return files;
  })();
  inflightInfo.set(tid, p); p.finally(() => inflightInfo.delete(tid)).catch(() => {});
  return p;
}
const isDirect = n => /\.(mp4|m4v|webm)$/i.test(n || '');
const isH264 = n => /x\.?264|h\.?264|\bavc\b/i.test(n || '');
const inflightLinks = new Map();
async function addTranscode(s){
  s.tc = true;
  if(!(s.u.streamable && s.u.id)) return;
  try{ const t = await rd('/streaming/transcode/' + encodeURIComponent(s.u.id)); s.hls = pickUrl(t && t.apple); s.mp4 = pickUrl(t && t.liveMP4); }catch(e){}
}
function getStream(link, fresh){
  const c = linkCache[link];
  if(!fresh && c && Date.now() - c.at < LINK_TTL) return Promise.resolve(c);
  if(inflightLinks.has(link)) return inflightLinks.get(link);
  const p = (async () => {
    const u = await rd('/unrestrict/link', { form:{ link } });
    const s = { u:{ download:u.download, filename:u.filename, filesize:u.filesize, id:u.id, streamable:u.streamable }, hls:null, mp4:null, tc:false, at:Date.now() };
    // only ask for a converted stream up front when the original almost certainly won't play in the browser
    if(!NATIVE && !isDirect(s.u.filename) && !isH264(s.u.filename) && !needsVLC(s.u.filename)) await addTranscode(s);
    linkCache[link] = s; trimCache(linkCache, LINK_TTL, 60); store.set(K_LINKS, linkCache);
    return s;
  })();
  inflightLinks.set(link, p); p.finally(() => inflightLinks.delete(link)).catch(() => {});
  return p;
}
const pickFilm = files => files.slice().sort((x, y) => y.bytes - x.bytes)[0];
const isEpisodes = files => files.length > 1 && files.some(f => epOf(f.path));
// Opening a title's card warms up its stream in the background, so Play starts right away
function prefetchLib(list){
  const it = list.slice().sort((a, c) => (progOfTorrent(c.tid) ? 1 : 0) - (progOfTorrent(a.tid) ? 1 : 0) || (c.bytes || 0) - (a.bytes || 0))[0];
  if(!it || !store.get(K_RD, '')) return;
  torrentFiles(it.tid).then(files => {
    if(!files.length) return;
    if(!isEpisodes(files)) return getStream(pickFilm(files).link);
    const pr = progOfTorrent(it.tid); if(pr && pr.link) return getStream(pr.link);
  }).catch(() => {});
}

const libFor = id => Object.entries(rdLib.items).filter(([, it]) => it.m && it.m.id === id).map(([tid, it]) => ({ tid, ...it }));

// Detail card: "In your library" above the streaming logos (shown even if TMDB is slow or offline)
function refreshLibBlocks(){ $$('[data-lid]').forEach(bx => { const sb = bx.closest('.sbody'); if(sb) libBlock(sb, pid(bx.dataset.lid)); }); }
// Play button: a ring with a play mark, the action, and a short note under it (filled ring = the main action)
const PLAYSVG = '<svg viewBox="0 0 24 24"><path d="M8.5 4.5v15l12-7.5z" fill="currentColor"/></svg>';
const reelBtn = (attrs, label, sub, filled) => `<button class="rdot${filled ? ' fill' : ''}" ${attrs}><i>${PLAYSVG}</i><span><b>${esc(label)}</b>${sub ? `<small>${esc(sub)}</small>` : ''}</span></button>`;
function libBlock(b, id){
  const list = libFor(id), box = $('#mlib', b);
  if(!list.length || !box) return;
  box.dataset.lid = id;
  // rating cards: just a short note that you have it (playing lives in the Library tab)
  if(box.closest('.mvsheet')){
    const pr = list.map(it => progOfTorrent(it.tid)).filter(Boolean).sort((x, y) => y.at - x.at)[0];
    const extra = pr && pr.d ? `${pr.label ? pr.label + ' · ' : ''}${fmtDur(pr.d - pr.t)} left` : typeof id === 'string' ? (list.length > 1 ? `${list.length} sets` : seasonOf(list[0].name) || '') : '';
    box.innerHTML = `<div class="inlib fadein">${PLAYSVG}<span>In your library${extra ? ` <em>· ${esc(extra)}</em>` : ''}</span></div>`;
    return;
  }
  box.innerHTML = (`<div class="lib fadein"><div class="rdrow">${list
    .sort((x, y) => x.name.localeCompare(y.name, undefined, { numeric:true }))
    .map((it, i) => { const pr = progOfTorrent(it.tid), sea = seasonOf(it.name);
      return reelBtn(`data-rdt="${esc(it.tid)}"`, (pr ? 'Resume' : 'Play') + (sea ? ' ' + sea : ''), pr && pr.d ? `${fmtDur(pr.d - pr.t)} left` : list.length > 1 && !sea ? 'Copy ' + (i + 1) : '', i === 0); }).join('')}</div></div>`);
  $$('[data-rdt]', box).forEach(x => x.onclick = () => openTorrent(x.dataset.rdt, filmOf(id).title, id));
  prefetchLib(list);
}

async function openTorrent(tid, title, id){
  if(id == null) id = (rdLib.items[tid] && rdLib.items[tid].m || {}).id;
  if(NATIVE){
    let files;
    try{ files = await torrentFiles(tid); }catch(e){ toast(rdErr(e)); return; }
    if(files.length && !isEpisodes(files)){
      const f = pickFilm(files);
      const res = await launchNative(f.link, title, { key:tid + '|' + f.path, tid, id, label:'' });
      if(res && res.finished && typeof id === 'number' && !getR(id)) setTimeout(() => toast(`Finished · rate ${esc(title)} with the stars below`), 1200);
      return;
    }
  }
  const it = rdLib.items[tid] || {};
  const s = sheet(`<h2 class="stitle">${esc(title)}</h2><div class="small" style="word-break:break-all">${esc(it.name || '')}</div>
    <div id="lbox" style="margin-top:12px"><div class="msg">Loading files…</div></div>
    <button class="btn ghost" id="lfix">Wrong title? Fix the match</button>`, { tall:true });
  const b = s.body;
  $('#lfix', b).onclick = () => fixMatch(tid, () => s.close());
  let files;
  try{ files = await torrentFiles(tid); }
  catch(e){ $('#lbox', b).innerHTML = `<div class="msg">${rdErr(e)}</div>`; return; }
  if(!s.el.isConnected) return;
  if(!files.length){ $('#lbox', b).innerHTML = '<div class="msg">No playable video files in this item.</div>'; return; }
  if(!isEpisodes(files)){
    const f = pickFilm(files);   // a film with extras: the biggest file is the film
    return playInto($('#lbox', b), f.link, title, { key:tid + '|' + f.path, tid, id, label:'' });
  }
  renderEpisodes($('#lbox', b), files.map(f => ({ ...f, tid, ep:epOf(f.path) })), id, title);
}

const pickUrl = o => !o ? null : typeof o === 'string' ? o : o.full || Object.values(o).find(x => typeof x === 'string') || null;
const IS_IOS = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const IS_ANDROID = /Android/i.test(navigator.userAgent);
// 4K / HEVC files that aren't mp4: phone browsers show a black picture for these, so they go straight to VLC
const needsVLC = n => /2160p|\b4k\b|\buhd\b|hevc|[xh]\.?265|10.?bit|\bhdr\b|dolby.?vision|\bdv\b/i.test(n || '') && !/\.(mp4|m4v|webm)$/i.test(n || '');
function vlcLink(url, title, startSec){
  if(IS_IOS) return 'vlc-x-callback://x-callback-url/stream?url=' + encodeURIComponent(url);
  if(IS_ANDROID) return `intent://${url.replace(/^https?:\/\//, '')}#Intent;scheme=https;type=video/*;package=org.videolan.vlc;S.title=${encodeURIComponent(title)}${startSec > 20 ? ';l.position=' + Math.floor(startSec * 1000) : ''};end`;
  return url;
}
async function playInto(box, link, title, meta = {}, retry = false){
  box.innerHTML = '<div class="msg">Getting a stream link…</div>';
  let s;
  try{ s = await getStream(link, retry); }
  catch(e){ box.innerHTML = `<div class="msg">${rdErr(e)}</div>`; return; }
  if(!box.isConnected) return;
  const u = s.u, url = u.download, remembered = !retry && Date.now() - s.at > 10000;
  const startAt = meta.key && prog[meta.key] ? prog[meta.key].t : 0;
  const vlc = vlcLink(url, title, startAt);
  const copyBtn = () => { const c = $('#lcopy', box); if(c) c.onclick = async () => { try{ await navigator.clipboard.writeText(url); toast('Stream link copied'); }catch(e){ toast('Could not copy the link'); } }; };
  if(NATIVE) return nativePlay(box, s, link, title, meta, startAt, remembered);
  if((IS_ANDROID || IS_IOS) && needsVLC(u.filename)){
    box.innerHTML = `<div class="lplay"><div class="msg">This is a 4K file, which plays best in VLC. Opening VLC${startAt > 20 ? ` at ${clock(startAt)}` : ''}…</div>
      <div class="small" style="margin-top:4px">${esc(u.filename || '')}${u.filesize ? ' · ' + fmtSize(u.filesize) : ''}</div>
      <a class="btn" href="${esc(vlc)}" style="display:block;text-align:center;text-decoration:none">Open in VLC</a>
      <button class="btn ghost" id="lcopy">Copy stream link</button></div>`;
    copyBtn();
    location.href = vlc;
    return;
  }
  // Order: files a browser can usually play as-is (mp4, H.264) start with the original; others with Real-Debrid's converted stream
  const hlsOk = !!document.createElement('video').canPlayType('application/vnd.apple.mpegurl');
  const conv = () => [hlsOk ? s.hls : null, s.mp4];
  const tried = new Set();
  const srcs = [...new Set(((isDirect(u.filename) || isH264(u.filename)) ? [url, ...conv()] : [...conv(), url]).filter(Boolean))];
  const kind = x => x === url ? 'original file' : 'converted stream';
  const src = srcs.shift(); tried.add(src);
  box.innerHTML = `<div class="lplay"><video controls playsinline preload="metadata" src="${esc(src)}"></video>
    <div class="small" id="lperr" style="margin-top:6px"></div>
    <div class="small" style="margin-top:4px">${esc(u.filename || '')}${u.filesize ? ' · ' + fmtSize(u.filesize) : ''} · <span id="lkind">${kind(src)}</span></div>
    <a class="btn" href="${esc(vlc)}" style="display:block;text-align:center;text-decoration:none">Open in VLC</a>
    <button class="btn ghost" id="lcopy">Copy stream link</button></div>`;
  const v = $('video', box);
  if(meta.key) trackProgress(v, box, link, title, meta);
  let busy = false;
  const next = async why => {
    if(busy || !v.isConnected) return; busy = true;
    if(!srcs.length && !s.tc){   // the converted stream wasn't fetched up front: get it now
      await addTranscode(s); store.set(K_LINKS, linkCache);
      conv().filter(x => x && !tried.has(x)).forEach(x => srcs.push(x));
    }
    if(srcs.length){
      const at = v.currentTime; v.src = srcs.shift(); tried.add(v.src); $('#lkind', box).textContent = kind(v.src);
      if(at > 1) v.addEventListener('loadedmetadata', () => { try{ v.currentTime = at; }catch(e){} }, { once:true });
      v.play().catch(() => {}); busy = false; return;
    }
    if(remembered){ delete linkCache[link]; store.set(K_LINKS, linkCache); return playInto(box, link, title, meta, true); }   // the remembered link may have expired
    const e = $('#lperr', box); if(e) e.textContent = why;
    if(IS_ANDROID || IS_IOS){ v.pause(); location.href = vlcLink(url, title, v.currentTime || startAt); }   // hand off to VLC automatically
  };
  v.onerror = () => next("This file's format can't play inside the app. Opening VLC…");
  // Sound but a black picture means the browser can't decode this video (common with 4K HEVC). Try the next option.
  v.addEventListener('playing', () => setTimeout(() => {
    if(v.isConnected && !v.paused && v.readyState >= 2 && !v.videoWidth) next("This browser can't show this video's picture. Opening VLC…");
  }, 2500));
  copyBtn();
}

// Android app: the native player plays everything (4K HEVC, MKV, DTS); when it closes it reports where you stopped
async function nativePlay(box, s, link, title, meta, startAt, remembered){
  const u = s.u;
  box.innerHTML = `<div class="msg">Playing…</div><div class="small" style="margin-top:4px">${esc(u.filename || '')}${u.filesize ? ' · ' + fmtSize(u.filesize) : ''}</div>`;
  let r;
  try{ r = await playerPlay({ url:u.download, title, startMs:startAt > 20 ? Math.floor(startAt * 1000) : 0 }); }
  catch(e){ box.innerHTML = `<div class="msg">Could not open the player (${esc(e.message || e)}).</div>`; return; }
  if(r.error && remembered){ delete linkCache[link]; store.set(K_LINKS, linkCache); return playInto(box, link, title, meta, true); }   // the remembered link may have expired
  const t = (r.positionMs || 0) / 1000, d = (r.durationMs || 0) / 1000;
  const finished = !!(d > 0 && (r.ended || t / d >= 0.9));
  if(meta.key && d > 0){
    if(finished) finishWatch(meta.key, meta);
    else if(t > 5){ prog[meta.key] = { t, d, at:Date.now(), tid:meta.tid, id:meta.id, title, label:meta.label || '', link, ...progMeta(meta) }; saveProg(); afterChange(); }
  }
  if(!box.isConnected) return;
  afterPlay(box, { link, title, meta, t, d, finished, error:r.error });
}

// Android app: open the player straight from wherever you are; when it closes you're back on that same page
let launching = 0;
// After you finish an episode: "Next episode in 10s" (unless turned off in Settings), then straight into it
const K_AUTOPLAY = 'rclib.autoplay';
// Episodes: hand the player the rest of the show so it can go straight on to the next episode itself
// (it fetches each link from Real-Debrid on the phone), skip intros and count down near the credits.
async function launchNative(link, title, meta){
  let extra = null;
  if(typeof meta.id === 'string' && meta.key){
    try{
      const all = seasonsCache.get(meta.id) || await showEpisodes(meta.id); seasonsCache.set(meta.id, all);
      const flat = [...all.keys()].filter(Boolean).sort((a, c) => a - c).flatMap(sn => all.get(sn)).filter(x => x.ep);
      const i = flat.findIndex(x => x.tid + '|' + x.path === meta.key);
      if(i >= 0){
        const queue = flat.slice(i + 1, i + 41).map(x => {
          const k = x.tid + '|' + x.path, c = linkCache[x.link];
          return { link:x.link, key:k, tid:x.tid, s:x.ep.s, e:x.ep.e, label:`S${x.ep.s} E${x.ep.e}`,
            url:c && Date.now() - c.at < LINK_TTL - 600e3 ? c.u.download : '',
            startMs:prog[k] && prog[k].t > 20 ? Math.floor(prog[k].t * 1000) : 0 };
        });
        extra = { show:title, tmdbId:+String(meta.id).slice(2), season:flat[i].ep.s, episode:flat[i].ep.e,
          queue, rdToken:store.get(K_RD, ''), autoplay:store.get(K_AUTOPLAY, true) };
      }
    }catch(e){}
  }
  return launchOnce(link, title, meta, false, extra);
}
// What's in the player right now, so a resume point can be saved even if the player never answers
const K_INPLAYER = 'rclib.inplayer';
async function launchOnce(link, title, meta, retry = false, extra = null){
  if(!retry && Date.now() - launching < 4000) return null;   // ignore a double tap, but never stay stuck
  launching = Date.now();
  if(!retry) toast('Opening the player…');
  try{
    const s = await getStream(link, retry);
    const startAt = meta.key && prog[meta.key] ? prog[meta.key].t : 0;
    const remembered = !retry && Date.now() - s.at > 10000;
    let r;
    const opts = { url:s.u.download, title:title + (meta.label ? ' · ' + meta.label : ''), startMs:startAt > 20 ? Math.floor(startAt * 1000) : 0, key:meta.key || '' };
    if(extra) Object.assign(opts, extra, { queue:JSON.stringify(extra.queue) });
    store.set(K_INPLAYER, { link, title, meta, queue:(extra && extra.queue) || [], at:Date.now() });
    try{ r = await playerPlay(opts); }
    catch(e){ store.del(K_INPLAYER); toast('Could not open the player'); return null; }
    if(r.error && remembered && (!r.lastKey || r.lastKey === meta.key)){   // the remembered link may have expired
      delete linkCache[link]; store.set(K_LINKS, linkCache); return launchOnce(link, title, meta, true, extra);
    }
    if(r.error) toast(r.error === 'NEXT_EPISODE_UNAVAILABLE' ? "Couldn't load the next episode" : `The player couldn't play this file (${esc(r.error)})`);
    return applyPlayerResult(r, { link, title, meta, queue:(extra && extra.queue) || [] });
  }catch(e){ toast(rdErr(e)); return null; }
  finally{ launching = 0; }
}
// Save what the player reports: episodes it finished on its own, and where you stopped
function applyPlayerResult(r, ctx){
  let { link, title, meta } = ctx;
  const byKey = {}; (ctx.queue || []).forEach(q => byKey[q.key] = q);
  let played = r.played; if(typeof played === 'string'){ try{ played = JSON.parse(played); }catch(e){ played = []; } }
  (Array.isArray(played) ? played : []).forEach(p => {
    if(!p || !p.key || !p.finished || done[p.key]) return;
    const q = byKey[p.key];
    finishWatch(p.key, q ? { key:p.key, tid:q.tid, id:meta.id, label:q.label } : meta);
  });
  if(r.lastKey && r.lastKey !== meta.key && byKey[r.lastKey]){
    const q = byKey[r.lastKey];
    link = q.link; meta = { key:q.key, tid:q.tid, id:meta.id, label:q.label };
  }
  const t = (r.positionMs || 0) / 1000, d = (r.durationMs || 0) / 1000;
  const finished = !!(d > 0 && (r.finished || r.ended || t / d >= 0.9));
  if(meta.key && d > 0){
    if(finished){ if(!done[meta.key] || prog[meta.key]) finishWatch(meta.key, meta); }
    else if(t > 5){ prog[meta.key] = { t, d, at:Date.now(), tid:meta.tid, id:meta.id, title, label:meta.label || '', link, ...progMeta(meta) }; saveProg(); }
  }
  store.del(K_INPLAYER); callNative('RCPlayer', 'clearSaved', {}).catch(() => {});
  afterChange(); refreshLibBlocks();
  return { t, d, finished, error:r.error };
}
// The player wrote its spot down but never got to report it (app closed or restarted meanwhile)
async function recoverPlayerSpot(){
  if(!NATIVE || launching) return;
  const ctx = store.get(K_INPLAYER, null);
  if(!ctx || !ctx.meta) return;
  if(Date.now() - (ctx.at || 0) > 14 * 864e5){ store.del(K_INPLAYER); return; }
  let r; try{ r = await callNative('RCPlayer', 'saved', {}); }catch(e){ return; }
  if(launching) return;   // a new play started meanwhile
  if(!r || !r.lastKey || !r.durationMs){ store.del(K_INPLAYER); return; }
  if(r.at && r.at < ctx.at - 60000) return;   // an older spot from a previous session
  applyPlayerResult(r, ctx);
}
if(NATIVE){ setTimeout(recoverPlayerSpot, 1500); document.addEventListener('visibilitychange', () => { if(!document.hidden) setTimeout(recoverPlayerSpot, 1500); }); }

const fmtDur = sec => { sec = Math.max(0, Math.round(sec / 60)); const h = Math.floor(sec / 60), mm = sec % 60; return h ? `${h} hr${mm ? ' ' + mm + ' min' : ''}` : `${mm} min`; };
// TMDB episode names and stills for one season (for the screen after an episode)
// One season from TMDB: its poster and year, and every episode's name, still, summary, runtime and air date
const seasonInfo = (tid, sn) => cached(`tvsea2:${tid}:${sn}`, 72, () => tmdb(`/tv/${tid}/season/${sn}`).then(x => ({
  name:x.name, poster:x.poster_path, year:(x.air_date || '').slice(0, 4),
  eps:(x.episodes || []).map(e => ({ e:e.episode_number, name:e.name, still:e.still_path, overview:e.overview || '', runtime:e.runtime || 0, air:e.air_date || '' })) })));
const seasonEps = (tid, sn) => seasonInfo(tid, sn).then(x => x.eps);
// IMDb episode ratings (the same nightly data as the episode chart): season -> episode -> rating
async function imdbEpisodeRatings(tid){
  try{
    const d = await showDetails(tid); if(!d.imdb) return null;
    const res = await imdbFile(d.imdb); if(res.state !== 'ok') return null;
    const out = {}; Object.entries(res.d.s).forEach(([sn, list]) => { out[sn] = {}; list.forEach(e => { if(e[1] != null) out[sn][e[0]] = e[1]; }); });
    return out;
  }catch(e){ return null; }
}
const fmtAir = s => { if(!s) return ''; const d = new Date(s + 'T12:00:00'); return isNaN(d) ? '' : d.toLocaleDateString(undefined, { month:'short', day:'numeric', year:'numeric' }); };

// Episode cards: still, progress, title, runtime, air date, IMDb rating, summary. An "Up next" button on top.
function renderEpisodes(box, files, id, title, opt = {}){
  const tid = +String(id).slice(2);
  files = files.slice().sort((x, c) => { const a = x.ep || { s:999, e:999 }, d = c.ep || { s:999, e:999 }; return a.s - d.s || a.e - d.e || x.path.localeCompare(c.path); });
  const seasons = [...new Set(files.map(f => f.ep ? f.ep.s : 0))];
  const info = {}; let imdb = null;
  const key = f => f.tid + '|' + f.path;
  const play = f => {
    const label = f.ep ? `S${f.ep.s} E${f.ep.e}` : '';
    if(NATIVE){ launchNative(f.link, title, { key:key(f), tid:f.tid, id, label }).then(draw); return; }
    const p = sheet(`<h2 class="stitle">${esc(title)}${label ? ' · ' + label : ''}</h2><div id="lbox"></div>`, { tall:true });
    p.onClose = () => setTimeout(draw, 50);
    playInto($('#lbox', p.body), f.link, title, { key:key(f), tid:f.tid, id, label });
  };
  // the episode to offer first: the one you're partway through, else the one after the last you finished
  const upNext = () => {
    const started = files.filter(f => prog[key(f)] && prog[key(f)].d).sort((a, c) => prog[key(c)].at - prog[key(a)].at)[0];
    if(started) return { f:started, resume:prog[key(started)] };
    let last = -1; files.forEach((f, i) => { if(done[key(f)]) last = i; });
    const f = files[last + 1] || (last < 0 ? files[0] : null);
    return f ? { f } : null;
  };
  const draw = () => {
    if(!box.isConnected) return;
    const nx = upNext(), watched = files.filter(f => done[key(f)]).length;
    const head = opt.head ? opt.head(watched, files.length) : '';
    box.innerHTML = head + (nx ? `<div class="epnext">${reelBtn('id="epnext"', `${nx.resume ? 'Resume' : watched ? 'Play' : 'Start'} ${nx.f.ep ? `S${nx.f.ep.s} E${nx.f.ep.e}` : ''}`.trim(), nx.resume ? `${fmtDur(nx.resume.d - nx.resume.t)} left` : 'Up next', true)}</div>` : '')
      + seasons.map(sn => {
        const si = info[sn], list = files.filter(f => (f.ep ? f.ep.s : 0) === sn);
        return `${seasons.length > 1 ? `<div class="lbl lcl">${sn ? 'Season ' + sn : 'Extras'}${si && si.year ? ' · ' + si.year : ''}</div>` : ''}<div class="eplist">${list.map(f => {
          const i = files.indexOf(f), k = key(f), pr = prog[k], fin = done[k];
          const te = f.ep && si ? si.eps.find(x => x.e === f.ep.e) : null;
          const ir = f.ep && imdb && imdb[f.ep.s] ? imdb[f.ep.s][f.ep.e] : null;
          const meta = [te && te.runtime ? te.runtime + ' min' : '', te ? fmtAir(te.air) : '', !te ? fmtSize(f.bytes) : ''].filter(Boolean).join(' · ');
          return `<button class="epc${fin ? ' fin' : ''}" data-i="${i}">
            <span class="epth">${te && te.still ? `<img src="${IMG}w300${te.still}" alt="" loading="lazy">` : `<span class="epph">${f.ep ? 'E' + f.ep.e : '▶'}</span>`}
              ${pr && pr.d ? `<i class="lbar" style="width:${Math.min(100, pr.t / pr.d * 100).toFixed(1)}%"></i>` : ''}${fin ? '<span class="epchk">✓</span>' : ''}</span>
            <span class="eptx"><span class="ept">${f.ep ? `<b>${f.ep.e}</b> ` : ''}${esc(te && te.name ? te.name : f.ep ? 'Episode ' + f.ep.e : baseName(f.path))}</span>
              <span class="epm">${esc(meta)}${ir ? ` <span class="epir ${heat(ir)}">${ir.toFixed(1)}</span>` : ''}</span>
              ${te && te.overview ? `<span class="epo" data-o="${i}">${esc(te.overview)}</span>` : ''}</span></button>`;
        }).join('')}</div>`;
      }).join('');
    const nb = $('#epnext', box); if(nb){ const f = nx.f; nb.addEventListener('pointerdown', () => getStream(f.link).catch(() => {}), { once:true }); nb.onclick = () => play(f); }
    $$('.epc', box).forEach(btn => {
      const f = files[+btn.dataset.i];
      btn.addEventListener('pointerdown', () => { getStream(f.link).catch(() => {}); }, { once:true });   // start fetching the link before the finger lifts
      longPress(btn, () => { toggleWatched([key(f)], f.tid, id); draw(); });
      btn.onclick = e => { if(btn._held){ btn._held = false; return; } const o = e.target.closest('.epo'); if(o && !o.classList.contains('open')){ o.classList.add('open'); return; } play(f); };
    });
  };
  draw();
  // fill in TMDB details and IMDb ratings as they arrive
  seasons.filter(Boolean).forEach(sn => seasonInfo(tid, sn).then(x => { info[sn] = x; draw(); }).catch(() => {}));
  imdbEpisodeRatings(tid).then(x => { if(x){ imdb = x; draw(); } });
  return draw;
}
// The episode after this one in your library (same season, or the next season you have)
async function nextEpisode(id, key){
  try{
    const all = seasonsCache.get(id) || await showEpisodes(id); seasonsCache.set(id, all);
    const flat = [...all.keys()].filter(Boolean).sort((a, c) => a - c).flatMap(sn => all.get(sn));
    const i = flat.findIndex(x => x.tid + '|' + x.path === key);
    return i >= 0 ? flat[i + 1] || null : null;
  }catch(e){ return null; }
}
async function afterPlay(box, c){
  const { link, title, meta, t, d, finished } = c;
  const id = meta.id, isTV = typeof id === 'string';
  const lm = (rdLib.items[meta.tid] && rdLib.items[meta.tid].m) || {}, f0 = filmOf(id);
  const name = lm.title || (f0.title !== 'Untitled' ? f0.title : '') || title;
  const sheetBody = box.closest('.sbody'); if(sheetBody) sheetBody.classList.add('apmode');
  const ep = isTV && meta.label ? (meta.label.match(/S(\d+)\s*E(\d+)/) || []) : [];
  const resumeAt = prog[meta.key] ? prog[meta.key].t : 0;
  box.innerHTML = `<div class="ap fadein">
    <div class="apbd"><div class="apbdin">
      <div class="apposter">${poster(lm.poster || f0.poster, '', 'w185', name)}</div>
      <div class="aptxt"><div class="apname">${esc(name)}</div><div class="apsub" id="apsub">${esc([lm.year || f0.year, meta.label].filter(Boolean).join(' · '))}</div></div>
    </div></div>
    ${c.error ? `<div class="msg" style="padding:14px 0 0">The player couldn't play this file (${esc(c.error)}).</div>` : ''}
    <div class="approg"><i style="width:${d ? Math.min(100, (finished ? 1 : t / d) * 100).toFixed(1) : 0}%"></i></div>
    <div class="aptime">${finished ? (isTV ? 'Finished this episode' : 'Finished') : d && t > 5 ? `${fmtDur(t)} watched · ${fmtDur(d - t)} left` : d ? fmtDur(d) : ''}</div>
    <div id="apbtns"></div>
    <div id="aprate"></div>
  </div>`;
  // backdrop: the episode's still for shows, the film's backdrop for movies
  const setBd = path => { const el = $('.apbd', box); if(el && path) el.style.backgroundImage = `linear-gradient(180deg,rgba(12,10,15,.15),rgba(12,10,15,.92)),url(${IMG}w780${path})`; };
  detailsFor(id).then(dd => { if(!box.isConnected) return; if(!(isTV && ep[1])) setBd(dd.backdrop); else if(!$('.apbd', box).style.backgroundImage) setBd(dd.backdrop); }).catch(() => {});
  if(isTV && ep[1]) seasonEps(+String(id).slice(2), +ep[1]).then(list => {
    if(!box.isConnected) return;
    const e = list.find(x => x.e === +ep[2]); if(!e) return;
    const sub = $('#apsub', box); if(sub) sub.textContent = [meta.label, e.name].filter(Boolean).join(' · ');
    if(e.still) setBd(e.still);
  }).catch(() => {});
  // buttons
  const btns = $('#apbtns', box);
  const play = (lnk, m2) => { if(sheetBody) sheetBody.classList.remove('apmode'); playInto(box, lnk, title, m2); };
  const draw = next => {
    if(!box.isConnected) return;
    const out = [];
    if(!finished && resumeAt > 5) out.push(['ap-resume', 'btn', `▶ Resume from ${clock(resumeAt)}`]);
    if(next) out.push(['ap-next', finished || !(resumeAt > 5) ? 'btn' : 'btn ghost', `Next episode · S${next.ep.s} E${next.ep.e}`]);
    out.push(['ap-start', out.length ? 'btn ghost' : 'btn', finished ? (isTV ? 'Watch again' : 'Watch again') : 'Start over']);
    btns.innerHTML = out.map(([bid, cls, txt]) => `<button class="${cls}" id="${bid}">${esc(txt)}</button>`).join('');
    const rb = $('#ap-resume', btns); if(rb) rb.onclick = () => play(link, meta);
    const sb = $('#ap-start', btns); if(sb) sb.onclick = () => { delete prog[meta.key]; saveProg(); play(link, meta); };
    const nb = $('#ap-next', btns); if(nb) nb.onclick = () => play(next.link, { key:next.tid + '|' + next.path, tid:next.tid, id, label:`S${next.ep.s} E${next.ep.e}` });
  };
  draw(null);
  if(isTV) nextEpisode(id, meta.key).then(n => n && n.ep && draw(n));
  // finished a movie: rate it right here
  if(finished && !isTV){
    const fobj = { ...f0, id, title:name, year:lm.year || f0.year, poster:lm.poster || f0.poster };
    const rb = $('#aprate', box);
    rb.innerHTML = `<div class="aprate"><div class="lbl">${getR(id) ? 'Your rating' : 'How was it?'}</div>${starsHTML(36, 6)}<div class="rv2" id="aprv"></div>
      <div class="small" style="margin-top:8px">Logged in your diary for today.</div></div>`;
    const s5 = $('.s5', rb), rv = $('#aprv', rb);
    const paintR = r => { paintStars(s5, r); rv.textContent = r ? RWORDS[r] : ''; };
    paintR(getR(id));
    bindStars(s5, (r, fin) => { paintR(r); if(fin && r !== getR(id)) setRating(fobj, r); });
  }
}

function trackProgress(v, box, link, title, meta){
  const k = meta.key, start = prog[k] && prog[k].t > 20 ? prog[k].t : 0;
  let lastSave = 0, finished = false;
  if(start){
    const note = document.createElement('div'); note.className = 'small lres';
    note.innerHTML = `Resuming from ${clock(start)} · <button>Start over</button>`;
    v.after(note);
    $('button', note).onclick = () => { v.currentTime = 0; note.remove(); };
    v.addEventListener('loadedmetadata', () => { if(v.currentTime < 1) try{ v.currentTime = start; }catch(e){} }, { once:true });
  }
  const save = () => {
    if(finished || !v.duration || !isFinite(v.duration) || v.currentTime < 5) return;
    prog[k] = { t:v.currentTime, d:v.duration, at:Date.now(), tid:meta.tid, id:meta.id, title, label:meta.label || '', link, ...progMeta(meta) };
    saveProg();
  };
  v.addEventListener('timeupdate', () => {
    if(finished || !v.duration || !isFinite(v.duration)) return;
    if(v.currentTime / v.duration >= 0.9){ finished = true; finishWatch(k, meta); return; }
    if(Date.now() - lastSave > 5000){ lastSave = Date.now(); save(); }
  });
  v.addEventListener('pause', save);
  window.addEventListener('pagehide', save);
}
// Past 90%: clear the resume point, tick the file off, and log it in Rolling Credits
function finishWatch(k, meta){
  delete prog[k]; done[k] = Date.now(); saveProg();
  const id = meta.id; if(id == null) return;
  const f0 = filmOf(id), lm = (rdLib.items[meta.tid] && rdLib.items[meta.tid].m) || {};
  const f = { ...f0, title:f0.title !== 'Untitled' ? f0.title : (lm.title || f0.title), year:f0.year || lm.year || '', poster:f0.poster || lm.poster || null };
  if(typeof id === 'number' && !entries.some(e => e.tmdb === id && e.date === today())){
    entries.push({ id:uid(), added:Date.now(), tmdb:id, title:f.title, year:f.year, poster:f.poster, date:today(), rating:getR(id), rewatch:logCount(id) > 0, note:'' });
  }
  if(!ratings[id] && !seen[id]) seen[id] = { title:f.title, year:f.year, poster:f.poster, at:Date.now() };
  saveAll(); afterChange();
  toast(typeof id === 'number' ? `Logged · ${esc(f.title)}` : `Marked as watched · ${esc(f.title)}${meta.label ? ' ' + esc(meta.label) : ''}`);
}

function fixMatch(tid, after){
  const it = rdLib.items[tid]; if(!it) return;
  const s = sheet(`<h2 class="stitle">Fix the match</h2><div class="small" style="word-break:break-all">${esc(it.name)}</div>
    <input id="fq" type="search" value="${esc(parseName(it.name).title)}" enterkeyhint="search" autocomplete="off" style="margin-top:12px">
    <div class="grid" id="fres" style="margin-top:14px"></div>`, { tall:true });
  const b = s.body;
  const run = async () => {
    const g = $('#fres', b), q = $('#fq', b).value.trim(); if(!q) return;
    g.innerHTML = '<div class="msg" style="grid-column:1/-1">Searching…</div>';
    try{
      const d = await tmdb('/search/multi', { query:q, include_adult:'false' });
      const rows = (d.results || []).filter(m => m.media_type === 'movie' || m.media_type === 'tv').map(m => m.media_type === 'tv' ? slimTV(m) : slim(m)).slice(0, 18);
      g.innerHTML = rows.length ? rows.map(m => `<button class="fpick" data-fid="${esc(m.id)}" style="background:none;border:0;padding:0;color:inherit;text-align:left;cursor:pointer">
        ${poster(m.poster, '', 'w185', m.title)}<div class="small" style="margin-top:4px">${esc(m.title)} ${esc(m.year)}</div></button>`).join('')
        : '<div class="msg" style="grid-column:1/-1">No results. Try a shorter title.</div>';
      $$('.fpick', g).forEach(x => x.onclick = () => {
        const m = rows.find(r => String(r.id) === x.dataset.fid); if(!m) return;
        it.m = libMeta(m); it.fixed = 1; saveLib(); remember([m]);
        s.close(); toast('Matched to ' + esc(m.title)); after && after();
      });
    }catch(e){ g.innerHTML = `<div class="msg" style="grid-column:1/-1">${errText(e)}</div>`; }
  };
  $('#fq', b).onkeydown = e => { if(e.key === 'Enter'){ e.target.blur(); run(); } };
  run();
}

// Press and hold (about half a second) without moving: runs fn instead of the normal tap
// A choice list on frosted glass (instead of Android's gray pop-up): tap an option, or outside to close
function glassPick(opts, cur, onPick){
  const bk = document.createElement('div'); bk.className = 'gpk-bk';
  bk.innerHTML = `<div class="gpk">${opts.map(([v, l]) => `<button data-v="${esc(v)}" class="${String(v) === String(cur) ? 'on' : ''}"><span>${esc(l)}</span><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg></button>`).join('')}</div>`;
  document.body.append(bk);
  requestAnimationFrame(() => { bk.classList.add('in'); const on = $('.gpk .on', bk); if(on) on.scrollIntoView({ block:'center' }); });
  const close = () => { bk.classList.remove('in'); setTimeout(() => bk.remove(), 150); };
  bk.onclick = e => { const o = e.target.closest('[data-v]'); if(o){ hap(); onPick(o.dataset.v); } close(); };
}
// Every dropdown in the app (Genres filters and the rest) opens the glass list instead of Android's gray pop-up
document.addEventListener('mousedown', e => {
  const sel = e.target.closest && e.target.closest('select'); if(!sel || sel.multiple || sel.disabled) return;
  e.preventDefault(); sel.blur();
  const opts = [...sel.options].filter(o => !o.disabled && !o.hidden).map(o => [o.value, o.textContent]);
  glassPick(opts, sel.value, v => { if(sel.value === v) return; sel.value = v; sel.dispatchEvent(new Event('input', { bubbles:true })); sel.dispatchEvent(new Event('change', { bubbles:true })); });
}, true);
// Posters lean slightly toward your finger while pressed (starts a moment after the touch, so scrolling never tilts them)
(function posterTilt(){
  let el = null, tm = null, last = null;
  const go = () => { if(!el || !last) return; const r = el.getBoundingClientRect(), x = (last.clientX - r.left) / r.width - .5, y = (last.clientY - r.top) / r.height - .5;
    el.style.transform = `perspective(600px) rotateY(${(x * 12).toFixed(1)}deg) rotateX(${(-y * 12).toFixed(1)}deg) scale(.97)`; };
  const end = () => { clearTimeout(tm); tm = null; if(el){ el.style.transition = 'transform .25s ease-out'; el.style.transform = ''; } el = null; };
  document.addEventListener('pointerdown', e => {
    const t = e.target.closest && e.target.closest('.pw, .lshow, .lsea'); if(!t) return;
    const img = t.querySelector('img, .ph'); if(!img) return;
    last = e; end(); tm = setTimeout(() => { el = img; el.style.transition = 'transform .12s ease-out'; go(); }, 90);
  }, { passive:true });
  document.addEventListener('pointermove', e => { last = e; if(el) go(); }, { passive:true });
  ['pointerup', 'pointercancel', 'scroll'].forEach(t => document.addEventListener(t, end, { passive:true, capture:true }));
})();
// a light tap you can feel (Android app only)
const hap = (ms = 12) => { try{ NATIVE && navigator.vibrate && navigator.vibrate(ms); }catch(e){} };
function longPress(el, fn){
  let tmr = null, sx = 0, sy = 0;
  const cancel = () => { clearTimeout(tmr); tmr = null; };
  el.addEventListener('pointerdown', e => { el._held = false; sx = e.clientX; sy = e.clientY; cancel(); tmr = setTimeout(() => { el._held = true; tmr = null; navigator.vibrate && navigator.vibrate(12); fn(); }, 480); });
  el.addEventListener('pointermove', e => { if(tmr && Math.hypot(e.clientX - sx, e.clientY - sy) > 10) cancel(); });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach(t => el.addEventListener(t, cancel));
  el.addEventListener('contextmenu', e => e.preventDefault());
}
function confirmRemoveCont(k, after){
  const p = prog[k]; if(!p) return;
  const it = rdLib.items[p.tid], name = (it && it.m && it.m.title) || p.name || p.title || 'this';
  const s = sheet(`<h2 class="stitle">Remove from Continue watching?</h2>
    <p class="kswhat"><b>${esc(name)}</b>${p.label ? ' · ' + esc(p.label) : ''}. Your spot (${clock(p.t)}) will be forgotten. It stays in your library.</p>
    <button class="btn" id="cr-yes">Remove</button><button class="btn ghost" id="cr-no">Cancel</button>`);
  $('#cr-yes', s.body).onclick = () => { delete prog[k]; saveProg(); s.close(); after && after(); };
  $('#cr-no', s.body).onclick = () => s.close();
}

// ---- Library page: Netflix-style rows of only your own titles ----
// TMDB season list for a show (season posters, episode counts)
const showSeasons = tid => cached('tvs:' + tid, 72, () => tmdb('/tv/' + tid).then(x => (x.seasons || []).map(s => ({ n:s.season_number, poster:s.poster_path, eps:s.episode_count, name:s.name }))));
// Every episode file of a show in your library, across all its season packs, grouped by season number
async function showEpisodes(id){
  const tors = Object.entries(rdLib.items).filter(([, it]) => it.m && it.m.id === id);
  const bySeason = new Map();
  await Promise.all(tors.map(async ([tid, it]) => {
    let files = [];
    try{ files = await torrentFiles(tid); }catch(e){}
    const packSeason = (seasonOf(it.name).match(/\d+/) || [])[0];
    files.forEach(f => {
      const ep = epOf(f.path);
      const sn = ep ? ep.s : packSeason != null ? +packSeason : 0;
      if(!bySeason.has(sn)) bySeason.set(sn, []);
      bySeason.get(sn).push({ ...f, tid, ep });
    });
  }));
  bySeason.forEach(list => list.sort((a, c) => (a.ep ? a.ep.e : 999) - (c.ep ? c.ep.e : 999) || a.path.localeCompare(c.path)));
  return bySeason;
}
const seasonsCache = new Map();   // show id -> Map(season -> episodes), for this visit

function seasonCard(f, sn, count, posterPath){
  return `<button class="lsea" data-show="${esc(f.id)}" data-sn="${sn}">${poster(posterPath || f.poster, '', 'w185', f.title)}
    <span>${sn ? 'Season ' + sn : 'Extras'}</span><span class="small">${count ? count + ' episode' + (count === 1 ? '' : 's') : ''}</span></button>`;
}
let libType = 'all', libPick = 'all';   // Library dropdowns: All / Movies / Series, then All titles or one title
const K_LSORT = 'rclib.libsort', LSORTS = [['added', 'Recently added'], ['watched', 'Recently watched'], ['az', 'A–Z']];
// when a title was added to Real-Debrid, and when you last watched any of it
const addedAt = f => Math.max(0, ...Object.values(rdLib.items).filter(it => it.m && String(it.m.id) === String(f.id)).map(it => Date.parse(it.added) || 0));
function watchedAt(f){
  let t = 0;
  Object.values(prog).forEach(p => { if(String(p.id) === String(f.id)) t = Math.max(t, p.at || 0); });
  Object.entries(done).forEach(([k, at]) => { const it = rdLib.items[k.split('|')[0]]; if(it && it.m && String(it.m.id) === String(f.id)) t = Math.max(t, at || 0); });
  return t;
}
// Mark files watched (or back to unwatched). Watched ones leave Continue watching.
function toggleWatched(keys, tid, id, force){
  const on = force != null ? force : !keys.every(k => done[k]);
  keys.forEach(k => { if(on){ done[k] = Date.now(); delete prog[k]; } else delete done[k]; });
  if(on && typeof id === 'number' && !getR(id) && !seen[id]){ const f = filmOf(id); seen[id] = { title:f.title, year:f.year, poster:f.poster, at:Date.now() }; saveAll(); }
  saveProg(); afterChange(); hap(18);
  toast(on ? 'Marked as watched' : 'Marked as unwatched');
  return on;
}
// Long-press a poster in the Library: mark the whole title watched or unwatched
async function watchedMenu(id, after){
  const tors = Object.entries(rdLib.items).filter(([, it]) => it.m && String(it.m.id) === String(id));
  if(!tors.length) return;
  let keys = [];
  try{
    if(typeof id === 'string'){ const all = seasonsCache.get(id) || await showEpisodes(id); seasonsCache.set(id, all); all.forEach(l => l.forEach(f => keys.push(f.tid + '|' + f.path))); }
    else{ const files = await torrentFiles(tors[0][0]); const f = pickFilm(files); if(f) keys.push(tors[0][0] + '|' + f.path); }
  }catch(e){ toast(rdErr(e)); return; }
  if(!keys.length) return;
  const all = keys.every(k => done[k]), name = filmOf(id).title;
  const s = sheet(`<h2 class="stitle">${esc(name)}</h2>
    <button class="btn" id="wm1">${all ? 'Mark as unwatched' : typeof id === 'string' ? 'Mark every episode watched' : 'Mark as watched'}</button>
    <button class="btn ghost" id="wm0">Cancel</button>`);
  $('#wm1', s.body).onclick = () => { toggleWatched(keys, tors[0][0], id, !all); s.close(); after && after(); };
  $('#wm0', s.body).onclick = () => s.close();
}
function drawLibrary(b){
  b.innerHTML = `<div id="lmain"><div style="height:8px"></div>
    <div class="pad ltop"><div class="ltools"><label class="lsearch"><svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 20 20"/></svg><input type="search" id="lq" placeholder="Search your library" autocomplete="off" enterkeyhint="search"></label>
      <button class="lsortbtn" id="lsort" aria-label="Sort"><svg viewBox="0 0 24 24"><path d="M5 7h14M7.5 12h9M10 17h4"/></svg></button></div></div>
    <div id="lcont"></div><div class="pad" style="padding-top:8px">
    <div class="grid" id="lgrid"></div><div class="msg" id="lmsg"></div>
    <div id="lun" style="margin-top:18px"></div><div class="small" id="lsum" style="text-align:center;margin:26px 0 6px"></div></div></div><div id="lshowpg"></div>`;
  const paint = () => {
    if(!b.isConnected) return;
    const all = libSort(libTitles(), ''), has = !!store.get(K_RD, '');
    const shows = all.filter(f => f.tv), films = all.filter(f => !f.tv);
    const inType = all, picked = null;
    const un = Object.entries(rdLib.items).filter(([, it]) => !it.m);


    $('#lsum', b).textContent = !has ? 'Add your Real-Debrid API token in Settings to see your library.'
      : rdLib.synced ? `Synced ${new Date(rdLib.synced).toLocaleString(undefined, { month:'short', day:'numeric', hour:'numeric', minute:'2-digit' })}`
      : '';


    // Continue watching, above the posters (hidden while one title is picked)
    const cont = picked ? [] : Object.entries(prog).filter(([, p]) => p.link && p.d && (!p.tid || rdLib.items[p.tid]))
      .sort((a, c) => c[1].at - a[1].at).slice(0, 12);
    $('#lcont', b).innerHTML = cont.length ? `<section class="cwband"><div class="cwh"><span class="cwdot"></span>Continue watching</div><div class="cwtrack nodrag">${cont.map(([k, p]) => { const it = rdLib.items[p.tid], mm = it && it.m || {}, f0 = filmOf(p.id);
      const f = { title:mm.title || p.name || (f0.title !== 'Untitled' ? f0.title : '') || p.title || 'Untitled' };
      return `<button class="lcw cwc" data-ck="${esc(k)}" data-cid="${esc(p.id)}" data-cl="${esc(p.label || '')}"><span class="cwimg"><span class="cwph">${esc(f.title)}</span><i class="cwplay">${PLAYSVG}</i></span><i class="cwbar"><b style="width:${Math.min(100, p.t / p.d * 100).toFixed(1)}%"></b></i>
        <span class="cwt">${esc(f.title)}</span><span class="small">${p.label ? esc(p.label) + ' · ' : ''}${clock(p.d - p.t)} left</span></button>`; }).join('')}</div></section>
      <div class="cwlib">Your library</div>` : '';
    // wide pictures: the episode's still for shows, the backdrop for movies
    $$('.cwc', b).forEach(x => {
      const id = pid(x.dataset.cid), ep = (x.dataset.cl.match(/S(\d+)\s*E(\d+)/) || []);
      const put = path => { if(path && x.isConnected) $('.cwimg', x).innerHTML = `<img src="${IMG}w500${path}" alt="" loading="lazy"><i class="cwplay">${PLAYSVG}</i>`; };
      const bd = () => detailsFor(id).then(d => put(d.backdrop)).catch(() => {});
      if(typeof id === 'string' && ep[1]) seasonEps(+String(id).slice(2), +ep[1]).then(l => { const e = l.find(z => z.e === +ep[2]); e && e.still ? put(e.still) : bd(); }).catch(bd);
      else bd();
    });
    $$('[data-ck]', b).forEach(x => longPress(x, () => confirmRemoveCont(x.dataset.ck, paint)));
    $$('[data-ck]', b).forEach(x => x.onclick = e => {
      if(x._held){ x._held = false; e.preventDefault(); return; }
      const k = x.dataset.ck, p = prog[k]; if(!p) return;
      if(NATIVE){ launchNative(p.link, p.name || p.title || filmOf(p.id).title, { key:k, tid:p.tid, id:p.id, label:p.label }).then(paint); return; }
      const t = (p.name || p.title || filmOf(p.id).title) + (p.label ? ' · ' + p.label : '');
      const ps = sheet(`<h2 class="stitle">${esc(t)}</h2><div id="lbox"></div>`, { tall:true });
      playInto($('#lbox', ps.body), p.link, p.name || p.title || filmOf(p.id).title, { key:k, tid:p.tid, id:p.id, label:p.label });
    });

    // the posters: movies open their card; a series shows its seasons
    const grid = $('#lgrid', b);
    {
      const q = ($('#lq', b).value || '').trim().toLowerCase(), so = store.get(K_LSORT, 'added');
      $('#lsort', b).setAttribute('aria-label', 'Sort: ' + LSORTS.find(x => x[0] === so)[1]);
      let list = picked ? [picked] : inType.slice();
      if(q) list = list.filter(f => (f.title || '').toLowerCase().includes(q));
      if(so === 'added') list.sort((a, c) => addedAt(c) - addedAt(a));
      else if(so === 'watched') list.sort((a, c) => watchedAt(c) - watchedAt(a) || addedAt(c) - addedAt(a));
      grid.innerHTML = has && !rdLib.synced && !list.length ? '<i class="skel"></i>'.repeat(9) : list.map(f => f.tv
        ? `<button class="lshow" data-lshow="${esc(f.id)}">${poster(f.poster, '', 'w342', f.title)}</button>`
        : `<button class="lshow" data-lmov="${esc(f.id)}">${poster(f.poster, '', 'w342', f.title)}</button>`).join('');
    }
    $('#lmsg', b).textContent = has && rdLib.synced && !all.length ? 'Nothing in your Real-Debrid library yet.'
      : $('#lq', b).value.trim() && !grid.children.length ? 'No matches in your library.' : '';
    $$('.lshow', b).forEach(x => longPress(x, () => watchedMenu(pid(x.dataset.lmov || x.dataset.lshow), paint)));
    remember(all);
    setPageSource(picked && picked.tv ? null : { title:'My library', items:(picked ? [picked] : inType).filter(f => !f.tv) });

    $('#lun', b).innerHTML = un.length ? `<div class="lbl lcl">Not matched</div>` +
      un.map(([tid, it]) => `<div class="lunm"><span>${esc(it.name)}</span><button data-fix="${esc(tid)}">Match</button></div>`).join('') : '';
    $$('[data-fix]', b).forEach(x => x.onclick = () => fixMatch(x.dataset.fix, paint));
  };
  $('#lq', b).oninput = () => paint();
  $('#lsort', b).onclick = () => glassPick(LSORTS, store.get(K_LSORT, 'added'), v => { store.set(K_LSORT, v); paint(); });
  b.onclick = e => {
    const held = e.target.closest('.lshow'); if(held && held._held){ held._held = false; return; }
    const s = e.target.closest('.lsea'); if(s){ openSeason(pid(s.dataset.show), +s.dataset.sn); return; }
    const mv = e.target.closest('[data-lmov]'); if(mv){ openLibMovie(+mv.dataset.lmov); return; }
    const sh = e.target.closest('[data-lshow]'); if(sh) openShowPage(sh.dataset.lshow);
  };
  const sync = async () => {
    try{ await syncLib(); seasonsCache.clear(); await backfillMeta(); paint(); }catch(e){ if(b.isConnected) $('#lsum', b).innerHTML = rdErr(e); }
  };
  b._sync = sync;
  b._paint = paint;
  paint();
  if(store.get(K_RD, '') && Date.now() - rdLib.synced > 20e3) sync();   // every visit (one quick request), so removals and additions show right away
  else backfillMeta().then(ch => ch && paint());
  return paint;
}
// Pull down at the top of the Library tab to check Real-Debrid right away
(function pullToRefresh(){
  let y0 = null, pulled = 0, el = null;
  const ready = () => view === 'library' && !sheetStack.length && window.scrollY <= 0 && store.get(K_RD, '');
  const bar = () => el || (el = Object.assign(document.createElement('div'), { className:'ptr' }), document.body.appendChild(el), el);
  document.addEventListener('touchstart', e => { y0 = ready() && e.touches.length === 1 ? e.touches[0].clientY : null; pulled = 0; }, { passive:true });
  document.addEventListener('touchmove', e => {
    if(y0 == null) return;
    pulled = Math.max(0, e.touches[0].clientY - y0);
    if(pulled < 8){ bar().style.opacity = 0; return; }
    const p = Math.min(pulled / 90, 1), b = bar();
    b.textContent = p >= 1 ? 'Release to sync' : 'Pull to sync';
    b.style.opacity = p; b.style.transform = `translate(-50%, ${Math.min(pulled * 0.5, 50)}px)`;
  }, { passive:true });
  document.addEventListener('touchend', () => {
    if(y0 == null) return;
    const go = pulled >= 90; y0 = null;
    const b = bar();
    if(go){
      const lib = $('#v-library > div');
      b.textContent = 'Syncing…';
      Promise.resolve(lib && lib._sync && lib._sync()).finally(() => { b.style.opacity = 0; b.style.transform = 'translate(-50%, 0)'; });
    } else { b.style.opacity = 0; b.style.transform = 'translate(-50%, 0)'; }
  }, { passive:true });
})();
// ---- A series' own page in the Library tab: backdrop, details, season tabs, episodes ----
// A movie in your library: a simple card with just that movie (backdrop, title, details, story, play)
function openLibMovie(id){
  const f = libTitles().find(x => String(x.id) === String(id)) || filmOf(id);
  const s = sheet(`<div class="shwrap">
    <div class="shhero" id="mvhero"><div class="shin"><div class="shpo">${poster(f.poster, '', 'w342', f.title)}</div>
      <div class="shtx"><h1 class="shname">${esc(f.title)}</h1><div class="shmeta" id="mvmeta">${esc(f.year || '')}</div></div></div></div>
    <div class="pad" style="padding-top:6px"><p class="shov" id="mvov"></p><div id="mlib"></div>
      <div id="mvlike"></div></div></div>`, { tall:true, cls:'shcard' });
  s.el.insertAdjacentHTML('afterbegin', '<div class="shbg"></div>');
  const b = s.body;
  libBlock(b, id);
  moreLike($('#mvlike', b), id);
  const ov = $('#mvov', b); ov.onclick = () => ov.classList.toggle('open');
  detailsFor(id).then(d => {
    if(!b.isConnected) return;
    if(d.backdrop) $('.shbg', s.el).style.backgroundImage = `url(${IMG}w1280${d.backdrop})`;
    ov.textContent = d.overview || '';
    if(typeof id === 'string'){ $('#mvmeta', b).textContent = [d.year || f.year, d.vote ? '★ ' + (+d.vote).toFixed(1) : ''].filter(Boolean).join(' · '); return; }
    const rt = d.runtime ? (d.runtime >= 60 ? Math.floor(d.runtime / 60) + 'h ' + (d.runtime % 60) + 'm' : d.runtime + 'm') : '';
    $('#mvmeta', b).textContent = [d.year || f.year, rt, d.vote ? '★ ' + (+d.vote).toFixed(1) : ''].filter(Boolean).join(' · ');
  }).catch(() => {});
}
// "More like this" at the bottom of a library card: wide pictures like Continue watching.
// Titles you already have come first and open their library card; the rest open a simple card of their own.
const inLib = id => libTitles().some(x => String(x.id) === String(id));
function openLibCard(id){ typeof id === 'string' && inLib(id) ? openShowPage(id) : openLibMovie(id); }
async function moreLike(box, id){
  if(!box) return;
  const tv = typeof id === 'string', tid = tv ? +String(id).slice(2) : id;
  let list;
  try{
    list = await cached('like:' + id, 24, () => tmdb(`/${tv ? 'tv' : 'movie'}/${tid}/recommendations`)
      .then(d => (d.results || []).filter(m => m.title || m.name).slice(0, 20).map(tv ? slimTV : slim).filter(keep)));
  }catch(e){ return; }
  list = list.filter(m => String(m.id) !== String(id));
  list = [...list.filter(m => inLib(m.id)), ...list.filter(m => !inLib(m.id))].slice(0, 14);
  if(!list.length || !box.isConnected) return;
  remember(list);
  box.innerHTML = `<section class="cwband mlband"><div class="cwh"><span class="cwdot"></span>More like this</div><div class="cwtrack nodrag">${list.map(m => {
    const pic = m.backdrop ? `<img src="${IMG}w500${m.backdrop}" alt="" loading="lazy">` : `<span class="cwph">${esc(m.title)}</span>`;
    return `<button class="cwc" data-like="${esc(m.id)}"><span class="cwimg">${pic}</span><span class="cwt">${esc(m.title)}</span><span class="small">${inLib(m.id) ? '<b class="mlown">In your library</b>' : esc([m.year, m.tv ? 'Series' : 'Movie'].filter(Boolean).join(' · '))}</span></button>`; }).join('')}</div></section>`;
  $$('[data-like]', box).forEach(x => x.onclick = () => openLibCard(pid(x.dataset.like)));
}
let showSeason = null;
// A series opens as a card over the Library (swipe it down, tap outside or press Back to close)
function openShowPage(id){
  const f = libTitles().find(x => String(x.id) === String(id)); if(!f) return;
  showSeason = null;
  const s = sheet('<div class="shwrap"></div>', { tall:true, cls:'shcard' });
  s.el.insertAdjacentHTML('afterbegin', '<div class="shbg"></div>');
  showPage($('.shwrap', s.body), f, s);
}
async function showPage(pg, f, card){
  const id = f.id, tid = +String(id).slice(2);
  pg.innerHTML = `<div class="shp">
    <div class="shhero" id="shhero">
      <div class="shin"><div class="shpo">${poster(f.poster, '', 'w342', f.title)}</div>
        <div class="shtx"><h1 class="shname">${esc(f.title)}</h1><div class="shmeta"><span id="shmeta">${esc(f.year || '')}</span><span id="shcount"></span></div></div></div></div>
    <div class="pad" style="padding-top:6px">
      <p class="shov" id="shov"></p>
      <div class="shtabs" id="shtabs"></div>
      <div id="sheps"><div class="msg">Loading episodes…</div></div>
      <div id="shlike"></div>
    </div></div>`;
  moreLike($('#shlike', pg), id);
  const ov = $('#shov', pg); ov.onclick = () => ov.classList.toggle('open');
  // details: backdrop, overview, ratings
  detailsFor(id).then(d => {
    if(!pg.isConnected) return;
    if(d.backdrop){ const bg = card && $('.shbg', card.el); if(bg) bg.style.backgroundImage = `url(${IMG}w1280${d.backdrop})`; }
    ov.textContent = d.overview || '';
    const bits = [d.year || f.year, d.vote ? '★ ' + (+d.vote).toFixed(1) + ' TMDB' : ''].filter(Boolean);
    $('#shmeta', pg).textContent = bits.join(' · ');
    imdbEpisodeRatings(tid);   // warm the IMDb data for the episode cards
  }).catch(() => {});
  // seasons you have
  let all;
  try{ all = seasonsCache.get(id) || await showEpisodes(id); seasonsCache.set(id, all); }
  catch(e){ $('#sheps', pg).innerHTML = `<div class="msg">${rdErr(e)}</div>`; return; }
  if(!pg.isConnected) return;
  const sns = [...all.keys()].sort((a, c) => (a || 999) - (c || 999));
  const total = sns.reduce((n, sn) => n + all.get(sn).length, 0);
  $('#shcount', pg).textContent = ` · ${sns.filter(Boolean).length} season${sns.filter(Boolean).length === 1 ? '' : 's'} · ${total} episodes`;
  // start on the season you're in the middle of, else the first one not finished
  if(showSeason == null || !all.has(showSeason)){
    const key = x => x.tid + '|' + x.path;
    const recent = Object.entries(prog).filter(([k, p]) => p.id === id && p.d).sort((a, c) => c[1].at - a[1].at)[0];
    const fromProg = recent ? sns.find(sn => all.get(sn).some(x => key(x) === recent[0])) : null;
    showSeason = fromProg != null ? fromProg : (sns.find(sn => all.get(sn).some(x => !done[key(x)])) ?? sns[0]);
  }
  const drawTabs = () => {
    $('#shtabs', pg).innerHTML = sns.length > 1 ? sns.map(sn => `<button data-sn="${sn}" class="${sn === showSeason ? 'on' : ''}">${sn ? 'Season ' + sn : 'Extras'}</button>`).join('') : '';
    $$('[data-sn]', pg).forEach(x => x.onclick = () => { showSeason = +x.dataset.sn; drawTabs(); drawEps(); });
  };
  const drawEps = () => renderEpisodes($('#sheps', pg), all.get(showSeason) || [], id, f.title);
  drawTabs(); drawEps();
}
// One series picked: a poster for each season you have
function showSeasonCards(f, eps, posters){
  if(eps) return [...eps.keys()].sort((a, c) => (a || 999) - (c || 999)).map(sn => seasonCard(f, sn, eps.get(sn).length, posters && posters.get(sn))).join('');
  const packs = [...new Set(Object.values(rdLib.items).filter(it => it.m && it.m.id === f.id).map(it => +((seasonOf(it.name).match(/\d+/) || [0])[0])))];
  return packs.sort((a, c) => (a || 999) - (c || 999)).map(sn => seasonCard(f, sn, 0)).join('');
}
async function fillSeasonGrid(grid, f){
  try{
    const [eps, seas] = await Promise.all([seasonsCache.get(f.id) || showEpisodes(f.id), showSeasons(f.tid).catch(() => [])]);
    seasonsCache.set(f.id, eps);
    if(grid.isConnected && libPick === String(f.id)) grid.innerHTML = showSeasonCards(f, eps, new Map(seas.map(s => [s.n, s.poster])));
  }catch(e){}
}
// Work out a show's seasons from its files (and TMDB's season posters), then redraw its row
async function fillShowRow(b, f){
  try{
    const [eps, seas] = await Promise.all([seasonsCache.get(f.id) || showEpisodes(f.id), showSeasons(f.tid).catch(() => [])]);
    seasonsCache.set(f.id, eps);
    const row = b.querySelector(`[data-lshow="${CSS.escape(String(f.id))}"]`); if(!row) return;
    const posters = new Map(seas.map(s => [s.n, s.poster]));
    const sns = [...eps.keys()].sort((a, c) => (a || 999) - (c || 999));
    $('.track', row).innerHTML = sns.map(sn => seasonCard(f, sn, eps.get(sn).length, posters.get(sn))).join('');
    const total = sns.reduce((n, sn) => n + eps.get(sn).length, 0);
    $('.lsub', row).textContent = `${sns.filter(Boolean).length} season${sns.filter(Boolean).length === 1 ? '' : 's'} · ${total} episodes`;
  }catch(e){}
}
// A season's episodes, from whichever packs they came in
async function openSeason(id, sn){
  const f = filmOf(id), lm = libTitles().find(x => x.id === id) || {};
  const title = lm.title || f.title, tid = +String(id).slice(2);
  const s = sheet(`<div id="lbox"><div class="msg">Loading episodes…</div></div>`, { tall:true });
  const b = s.body;
  let eps;
  try{ const all = seasonsCache.get(id) || await showEpisodes(id); seasonsCache.set(id, all); eps = all.get(sn) || []; }
  catch(e){ $('#lbox', b).innerHTML = `<div class="msg">${rdErr(e)}</div>`; return; }
  if(!s.el.isConnected) return;
  if(!eps.length){ $('#lbox', b).innerHTML = '<div class="msg">No episodes found for this season.</div>'; return; }
  let si = null, bd = null;
  const head = (watched, total) => `<div class="sehd" style="${bd ? `background-image:linear-gradient(180deg,rgba(12,10,15,.1),rgba(12,10,15,.94)),url(${IMG}w780${bd})` : ''}">
      <div class="sehp">${poster((si && si.poster) || lm.poster || f.poster, '', 'w185', title)}</div>
      <div class="seht"><div class="apname">${esc(title)}</div><div class="apsub">${sn ? 'Season ' + sn : 'Extras'}${si && si.year ? ' · ' + si.year : ''}</div>
        <div class="apsub">${watched ? `${watched} of ${total} watched` : `${total} episode${total === 1 ? '' : 's'} in your library`}</div></div></div>`;
  const draw = renderEpisodes($('#lbox', b), eps, id, title, { head });
  if(sn) seasonInfo(tid, sn).then(x => { si = x; draw(); }).catch(() => {});
  detailsFor(id).then(d => { bd = d.backdrop; draw(); }).catch(() => {});
}
// Every movie in your library on one page (the Movies row's arrow), A to Z
function openLibMovies(){
  const films = libSort(libTitles().filter(f => !f.tv), '');
  const pg = openListPage('Your movies', `${films.length} in your library`);
  $('.grid', pg).innerHTML = films.map(f => cardHTML(f)).join('');
  setPageSource({ title:'Your movies', items:films });
}
function renderLibraryTab(){
  const v = $('#v-library');
  v.innerHTML = '<div style="padding-top:14px"></div>';
  drawLibrary(v.firstChild);
}
function openLibrary(){ setView('library'); }
