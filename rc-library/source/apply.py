import os
SRC = os.path.dirname(os.path.abspath(__file__))
p = 'index.html'; s = open(p).read()
def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:60], s.count(old))
    s = s.replace(old, new)

rep('''  <div class="hact">
    <button class="ib" id="hcar"''', '''  <div class="hact">
    <button class="ib" id="hlib" aria-label="My library"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z" fill="currentColor"/></svg></button>
    <button class="ib" id="hcar"''')
rep('--bg:#0c0a0f;--bg2:#141119;', '--bg:#000;--bg2:#000;')
rep('background:linear-gradient(180deg,#120f17,var(--bg))', 'background:#000')
rep('''const $ = (s, r = document) => r.querySelector(s);''', '''// First launch: copy the main Rolling Credits data into this app's own keys. The main app's keys are only read, never changed.
(function importMain(){ try{
  if(localStorage.getItem('rclib.imported')) return;
  ['entries','ratings','watch','seen','favpeople','tmdbkey','omdbkey','hiderated'].forEach(k => {
    const v = localStorage.getItem('reellog.' + k);
    if(v != null && localStorage.getItem('rclib.' + k) == null) store.set('rclib.' + k, JSON.parse(v));
  });
  if(localStorage.getItem('rclib.tmdbkey')) localStorage.setItem('rclib.imported', String(Date.now()));   // keep checking until there's something to copy
}catch(e){} })();
// TMDB responses are cached for this tab only (sessionStorage), so this copy never eats into the storage space the main app needs
const cstore = {
  get(k, d){ try{ const v = sessionStorage.getItem(k); return v == null ? d : JSON.parse(v); }catch(e){ return d; } },
  set(k, v){ try{ sessionStorage.setItem(k, JSON.stringify(v)); }catch(e){} }
};
const $ = (s, r = document) => r.querySelector(s);''')
rep("const isCached = (k, ttlH) => { if(mem.has(k)) return true; const c = store.get(CK + k, null);",
    "const isCached = (k, ttlH) => { if(mem.has(k)) return true; const c = cstore.get(CK + k, null);")
rep("  const c = store.get(CK + k, null);\n  if(c && Date.now()", "  const c = cstore.get(CK + k, null);\n  if(c && Date.now()")
rep("mem.set(k, d); store.set(CK + k, { t:Date.now(), d }); return d;", "mem.set(k, d); cstore.set(CK + k, { t:Date.now(), d }); return d;")
rep('<div id="mwatch"></div>', '<div id="mlib"></div><div id="mwatch"></div>', 2)
rep("  const b = s.body, s5 = $('.ratebar .s5', b);", "  const b = s.body, s5 = $('.ratebar .s5', b);\n  libBlock(b, id);", 2)
rep('''    <label class="toggle"><input type="checkbox" id="hide"''', '''    <label class="f">Real-Debrid API token (for your library)</label>
    <input id="rdkey" type="password" value="${esc(store.get(K_RD, ''))}" placeholder="Paste your Real-Debrid API token" autocomplete="off">
    <div class="small" style="margin-top:6px">From real-debrid.com/apitoken. Stored only on this device, in this app.</div>
    <button class="btn ghost" id="rdopen">Open my library</button>
    <label class="toggle"><input type="checkbox" id="hide"''')
rep('''  $('#okey', b).onchange = e =>''', '''  $('#rdkey', b).onchange = e => { const v = e.target.value.trim(); store.set(K_RD, v); if(!v){ rdLib = { synced:0, items:{} }; saveLib(); } toast(v ? 'Real-Debrid token saved' : 'Real-Debrid token removed'); };
  $('#rdopen', b).onclick = () => openLibrary();
  $('#okey', b).onchange = e =>''')
rep("$('#setTab').onclick = openSettings;", "$('#setTab').onclick = openSettings;\n$('#hlib').onclick = () => openLibrary();")
rep('.hero h2{font-size:31px}', '''.hero h2{font-size:31px}
.logo .libtag{font-style:normal;color:var(--good)}
.lib{margin:10px 0 4px}
.lib .lbl{font-size:10.5px;font-weight:800;letter-spacing:1.2px;text-transform:uppercase;color:var(--muted);margin:10px 0 7px}
.lib .lrow{display:flex;gap:8px;flex-wrap:wrap}
.lib .lrow button,.lfile{background:rgba(90,209,154,.12);border:1px solid rgba(90,209,154,.45);color:var(--text);border-radius:999px;padding:7px 13px;font-size:13px;font-weight:750;cursor:pointer;display:inline-flex;gap:6px;align-items:center}
.lib .lrow button b,.lfile b{color:var(--good)}
.lfiles{display:flex;flex-direction:column;gap:8px}
.lfile{border-radius:12px;justify-content:space-between;text-align:left;width:100%}
.lfile span.sz{color:var(--muted);font-weight:600;font-size:12px;flex:none}
.lunm{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--line);font-size:13px}
.lunm span{word-break:break-all;color:var(--muted)}
.lunm button{flex:none;background:var(--card2);border:1px solid var(--line);color:var(--text);border-radius:999px;padding:6px 11px;font-weight:700;font-size:12.5px;cursor:pointer}
.lplay video{width:100%;border-radius:12px;background:#000;max-height:60vh}
.lres{margin-top:6px}.lres button{background:none;border:0;color:var(--accent);font-weight:800;padding:0;cursor:pointer;font-size:inherit}
.lfile{position:relative;overflow:hidden}.lfile.fin{opacity:.6}
.lbar{position:absolute;left:0;bottom:0;height:3px;background:var(--good);border-radius:0 2px 2px 0}
.lcl{font-size:10.5px;font-weight:800;letter-spacing:1.2px;text-transform:uppercase;color:var(--muted);margin:16px 0 8px}
.lcrow{display:flex;gap:10px;overflow-x:auto;padding-bottom:4px;margin:0 -16px;padding-left:16px;padding-right:16px}
.lcw{flex:none;width:118px;background:none;border:0;padding:0;color:var(--text);text-align:left;cursor:pointer;position:relative;display:flex;flex-direction:column;gap:2px;font-size:12.5px;font-weight:700}
.lcw img,.lcw .ph{width:118px;height:177px;object-fit:cover;border-radius:10px;background:var(--card2)}
.lcw .lbar{top:173px;bottom:auto;left:0;height:4px}
.lcwrap{position:relative;flex:none}
.lcx{position:absolute;top:6px;right:6px;width:26px;height:26px;border-radius:50%;border:0;background:rgba(0,0,0,.6);color:#fff;font-size:13px;line-height:26px;padding:0;cursor:pointer}
.lcw{-webkit-touch-callout:none;user-select:none;-webkit-user-select:none}
.apmode>.stitle,.apmode>.small{display:none}
.apbd{border-radius:18px;overflow:hidden;background:var(--card2) center/cover no-repeat;min-height:190px;display:flex;align-items:flex-end;margin-top:4px}
.apbdin{display:flex;align-items:flex-end;gap:14px;padding:14px;width:100%}
.apposter{flex:none;width:78px}.apposter img,.apposter .ph{width:78px;aspect-ratio:2/3;height:auto;border-radius:10px;object-fit:cover;box-shadow:0 8px 20px rgba(0,0,0,.5);background:var(--card)}
.aptxt{min-width:0}.apname{font-family:var(--disp);font-weight:700;text-transform:uppercase;letter-spacing:.02em;font-size:26px;line-height:1.05}
.apsub{color:#d8d2e0;font-size:13.5px;margin-top:4px}
.approg{height:5px;border-radius:3px;background:var(--line);margin:16px 0 8px;overflow:hidden}.approg i{display:block;height:100%;background:var(--good);border-radius:3px}
.aptime{font-size:13.5px;color:var(--muted);font-weight:600}
.aprate{margin-top:20px;text-align:center;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px}
.aprate .lbl{font-size:11px;font-weight:800;letter-spacing:1.3px;text-transform:uppercase;color:var(--muted);margin-bottom:10px}
.aprate .s5{margin:0 auto}.aprate .rv2{margin-top:8px;min-height:18px;font-weight:700}
.sehd{border-radius:18px;overflow:hidden;background:var(--card2) center/cover no-repeat;min-height:170px;display:flex;align-items:flex-end;gap:14px;padding:14px;margin-top:4px}
.sehp{flex:none;width:74px}.sehp img,.sehp .ph{width:74px;aspect-ratio:2/3;height:auto;border-radius:10px;object-fit:cover;box-shadow:0 8px 20px rgba(0,0,0,.5);background:var(--card)}
.seht{min-width:0}
.eplist{display:flex;flex-direction:column;gap:10px;margin-top:10px}
.epc{display:flex;gap:12px;align-items:flex-start;width:100%;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px;color:var(--text);text-align:left;font:inherit;cursor:pointer}
.epc:active{background:var(--card2)}
.epc.fin .epth img,.epc.fin .epph{opacity:.55}
.epth{position:relative;flex:none;width:132px;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:var(--card2)}
.epth img{width:100%;height:100%;object-fit:cover;display:block}
.epph{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-weight:800;color:var(--muted);font-size:15px}
.epth .lbar{bottom:0;top:auto;height:4px}
.epchk{position:absolute;top:6px;right:6px;width:22px;height:22px;border-radius:50%;background:var(--good);color:#06150e;font-size:13px;font-weight:900;display:flex;align-items:center;justify-content:center}
.eptx{min-width:0;flex:1;display:flex;flex-direction:column;gap:3px}
.ept{font-weight:750;font-size:14.5px;line-height:1.25}.ept b{color:var(--accent);margin-right:2px}
.epm{font-size:12px;color:var(--muted);display:flex;flex-wrap:wrap;align-items:center;gap:6px}
.epir{font-size:11px;font-weight:800;border-radius:6px;padding:1px 6px}
.epo{font-size:12.5px;color:var(--muted);line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.epo.open{-webkit-line-clamp:unset;display:block}
.grid .lsea{width:auto}.grid .lsea img,.grid .lsea .ph{width:100%}
.lshow{background:none;border:0;padding:0;color:var(--text);text-align:left;cursor:pointer;display:flex;flex-direction:column;gap:5px;font:inherit}
.lshow img,.lshow .ph{width:100%;aspect-ratio:2/3;height:auto;object-fit:cover;border-radius:12px;background:var(--card2);box-shadow:0 8px 18px rgba(0,0,0,.35)}
.lshn{font-size:12.5px;font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#lpk{padding-top:12px}
/* see-through header buttons and dropdown pills: outline only */
.ib{background:transparent}
.pkp{background:transparent}
.upn{text-align:center;padding-top:6px}
.upnth{width:100%;aspect-ratio:16/9;border-radius:14px;overflow:hidden;background:var(--card2);display:flex;align-items:center;justify-content:center;font-weight:800;color:var(--muted);font-size:22px}
.upnth img{width:100%;height:100%;object-fit:cover}
.upnt{font-family:var(--disp);font-weight:700;text-transform:uppercase;font-size:24px;line-height:1.1}
.upnbar{height:5px;border-radius:3px;background:var(--line);margin:16px 0 4px;overflow:hidden}.upnbar i{display:block;height:100%;width:0;background:var(--accent);transition:width 1s linear}
.ptr{position:fixed;left:50%;top:calc(env(safe-area-inset-top,0px) + 70px);transform:translate(-50%,0);z-index:60;opacity:0;pointer-events:none;
  background:var(--card);border:1px solid rgba(255,181,71,.45);color:var(--text);font-weight:700;font-size:13px;padding:8px 14px;border-radius:999px;transition:opacity .15s;box-shadow:0 6px 18px rgba(0,0,0,.4)}
.shhero{position:relative;min-height:330px;background:var(--card2) center top/cover no-repeat;display:flex;align-items:flex-end;margin-top:-1px}
.shcard{overflow:hidden;background:#000}
.shcard .grab{position:absolute;left:0;right:0;top:0;z-index:4}
.shcard .grab i{background:rgba(255,255,255,.75)}
.shcard .sbody{position:relative;z-index:1;padding-top:0}
.shbg{position:absolute;left:0;right:0;top:0;height:360px;z-index:0;background:#000 center top/cover no-repeat;pointer-events:none}
.shbg::after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.15) 0%,rgba(0,0,0,.5) 55%,#000 96%)}
.shcard .shhero{background:transparent;min-height:340px}
.shcard .shin{padding-bottom:26px}
.shcard .shwrap .pad{position:relative;background:#000;margin:0 -16px;padding:6px 16px 0;box-shadow:0 -18px 18px #000;min-height:calc(100dvh - 380px)}
.shin{display:flex;align-items:flex-end;gap:14px;padding:16px;width:100%}
.shpo{flex:none;width:104px}.shpo img,.shpo .ph{width:104px;aspect-ratio:2/3;height:auto;border-radius:12px;object-fit:cover;box-shadow:0 10px 26px rgba(0,0,0,.6);background:var(--card)}
.shtx{min-width:0}.shname{margin:0;font-family:var(--disp);font-weight:700;text-transform:uppercase;letter-spacing:.02em;font-size:34px;line-height:1}
.shmeta{color:#ddd6e6;font-size:13px;font-weight:600;margin-top:6px}
.shov{color:var(--muted);font-size:14px;line-height:1.5;margin:4px 0 4px;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer}
.shov.open{-webkit-line-clamp:unset;display:block}
.shtabs{display:flex;gap:8px;overflow-x:auto;margin:14px 0 2px;padding-bottom:2px;scrollbar-width:none}
.shtabs button{flex:none;background:transparent;border:1px solid var(--line);color:var(--muted);border-radius:999px;padding:8px 14px;font-weight:800;font-size:13.5px;cursor:pointer}
.shtabs button.on{border-color:var(--accent);color:var(--text);background:rgba(255,181,71,.1)}
.cwband.mlband{margin:26px -16px 8px}.mlband .cwh{padding-left:16px}.mlband .cwc{width:180px}.mlown{color:var(--accent);font-weight:750}
.updfail{flex-basis:100%;display:flex;flex-direction:column;gap:8px;margin-top:10px;font-size:12.5px;color:var(--muted)}
.updfail button{align-self:flex-start;background:transparent;border:1px solid rgba(255,181,71,.5);color:var(--accent);border-radius:999px;padding:8px 14px;font-weight:800;font-size:13px;cursor:pointer}
#appupd{flex-wrap:wrap}
.cwband{margin:14px 0 4px;padding:0;background:transparent;border:0}
.cwh{display:flex;align-items:center;gap:8px;padding:0 20px 10px;font-weight:800;font-size:12px;letter-spacing:1.3px;text-transform:uppercase;color:var(--accent)}
.cwdot{width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 8px var(--accent)}
.cwtrack{display:flex;gap:12px;overflow-x:auto;padding:0 16px;scroll-padding:0 16px;scroll-snap-type:x mandatory;scrollbar-width:none}
.cwc{flex:none;width:208px;scroll-snap-align:start;background:none;border:0;padding:0;color:var(--text);text-align:left;cursor:pointer;display:flex;flex-direction:column;gap:3px;font:inherit}
.cwimg{display:block;width:100%;aspect-ratio:16/9;border-radius:12px;overflow:hidden;background:var(--card2);position:relative}
.cwimg img{width:100%;height:100%;object-fit:cover;display:block}
.cwph{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;padding:8px;text-align:center;color:var(--muted);font-weight:700;font-size:13px}
.cwbar{display:block;height:3px;border-radius:2px;background:var(--line);margin:6px 0 4px;overflow:hidden}.cwbar b{display:block;height:100%;background:var(--accent)}
.cwt{font-weight:750;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.cwlib{margin:18px 20px 0;font-weight:800;font-size:12px;letter-spacing:1.3px;text-transform:uppercase;color:var(--muted)}
.shwrap .shhero{margin:0 -16px;border-radius:0}
.shwrap .pad{padding-left:0;padding-right:0}
.rdrow{display:flex;flex-direction:column;gap:6px;margin:12px 0 4px}
.rdot{display:flex;align-items:center;gap:12px;background:none;border:0;padding:4px 0;color:var(--text);font:inherit;text-align:left;cursor:pointer;width:fit-content}
.rdot i{flex:none;width:44px;height:44px;border-radius:50%;border:1.5px solid var(--accent);color:var(--accent);display:flex;align-items:center;justify-content:center;transition:transform .12s}
.rdot i svg{width:20px;height:20px}
.rdot.fill i{background:var(--accent);color:#1a1206}
.rdot:active i{transform:scale(.94)}
.rdot b{display:block;font-size:16px;font-weight:700;line-height:1.2}
.rdot small{display:block;font-size:12.5px;color:var(--muted);margin-top:1px}
.epnext{margin:14px 0 6px}
.cwplay{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:38px;height:38px;border-radius:50%;border:1.5px solid #fff;color:#fff;background:rgba(12,10,15,.35);display:flex;align-items:center;justify-content:center}
.cwplay svg{width:17px;height:17px}
.lnk{display:block;margin:18px 0 4px;background:none;border:0;padding:0;color:var(--muted);font:inherit;font-size:13.5px;font-weight:700;text-decoration:underline;text-underline-offset:3px;cursor:pointer}
.lsea{flex:none;width:112px;background:none;border:0;padding:0;color:var(--text);text-align:left;cursor:pointer;display:flex;flex-direction:column;gap:2px;font-size:12.5px;font-weight:700;scroll-snap-align:start}
.lsea img,.lsea .ph{width:112px;aspect-ratio:2/3;height:auto;object-fit:cover;border-radius:12px;background:var(--card2);box-shadow:0 8px 18px rgba(0,0,0,.35);margin-bottom:4px}
.lcw{scroll-snap-align:start}''')
RD = open(os.path.join(SRC, 'rd_module.js')).read()
rep("/* ---------- Settings ---------- */", RD + "\n/* ---------- Settings ---------- */")
open(p, 'w').write(s)
print('applied')

# Storage on jwillhub.github.io is shared by every app there. When it's full, saves fail silently.
# Free room by dropping cached lookups (TMDB, Comic Vine), which the apps re-download when needed, then retry.
s2 = s
rep("""  set(k, v){ try{ localStorage.setItem(k, JSON.stringify(v)); return true; }catch(e){ return false; } },""",
"""  set(k, v){
    const j = JSON.stringify(v);
    try{ localStorage.setItem(k, j); return true; }catch(e){}
    try{
      for(let i = localStorage.length - 1; i >= 0; i--){ const x = localStorage.key(i); if(x && /^(reellog\\.c\\d?\\.|rclib\\.c\\d?\\.|longbox\\.c\\d+\\.)/.test(x)) localStorage.removeItem(x); }
      localStorage.setItem(k, j); return true;
    }catch(e){ return false; }
  },""")
rep("const k = $('#key', b).value.trim(); store.set(K_KEY, k);",
    "const k = $('#key', b).value.trim(); if(!store.set(K_KEY, k)){ $('#saveKey', b).textContent = 'Could not save — storage is full'; return; }")
rep("""(function importMain(){ try{""", """(function importMain(){ try{
  // the copy below can fail if this site's storage is full; the save helper further down clears cached data and retries""")
open(p, 'w').write(s)
print('applied storage fix')


# ---- Library tab + "In my library" filters ----
rep("""  <button class="tb" id="setTab">""", """  <button class="tb" data-v="library"><svg viewBox="0 0 24 24"><rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z"/></svg>Library</button>
  <button class="tb" id="setTab">""")
rep("""  <section id="v-list" hidden></section>""", """  <section id="v-list" hidden></section>
  <section id="v-library" hidden></section>""")
rep("""['discover','sagas','tv','diary','films','genres','genre','franchises','list'].forEach""", """['discover','sagas','tv','diary','films','genres','genre','franchises','list','library'].forEach""")
rep("""  if(v === 'films') renderFilms();
  scrollTo(0, 0);""", """  if(v === 'films') renderFilms();
  if(v === 'library') renderLibraryTab();
  scrollTo(0, 0);""")
rep("""  if(view === 'films') renderFilms();
  renderMyShows();""", """  if(view === 'films') renderFilms();
  if(view === 'library') renderLibraryTab();
  renderMyShows();""")
# header library button no longer needed: the tab replaces it
rep("""    <button class="ib" id="hlib" aria-label="My library"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z" fill="currentColor"/></svg></button>
""", "")
rep("""$('#setTab').onclick = openSettings;
$('#hlib').onclick = () => openLibrary();""", """$('#setTab').onclick = openSettings;""")
rep("""  $('#rdopen', b).onclick = () => openLibrary();""", """  $('#rdopen', b).onclick = () => { s.close(); setTimeout(openLibrary, 60); };""")
open(p, 'w').write(s)
print('applied library tab')
rep("films:'My films', diary:'Diary',", "films:'My films', library:'My library', diary:'Diary',")
open(p, 'w').write(s)

# ---- Settings redesign: grouped rows, each connection in its own sheet ----
import re
i = s.index('function openSettings(){')
j = re.compile(r'\n}\n').search(s, i).end()
s = s[:i] + open(os.path.join(SRC, 'settings_new.js')).read() + s[j:]
rep('.hero h2{font-size:31px}', '''.hero h2{font-size:31px}
.sgroup{margin-top:20px}
.sgh{font-size:11px;font-weight:800;letter-spacing:1.3px;text-transform:uppercase;color:var(--muted);margin:0 4px 8px}
.scard{background:var(--card);border:1px solid var(--line);border-radius:16px;overflow:hidden}
.srow{display:flex;align-items:center;gap:12px;width:100%;padding:13px 14px;background:none;border:0;border-top:1px solid var(--line);color:var(--text);text-align:left;font:inherit;cursor:pointer;-webkit-tap-highlight-color:transparent;position:relative}
.scard>.srow:first-child{border-top:0}
.srow:active:not(.static){background:var(--card2)}
.srow.static{cursor:default}
.sic{flex:none;width:34px;height:34px;border-radius:10px;background:var(--card2);display:flex;align-items:center;justify-content:center}
.sic svg{width:19px;height:19px;fill:none;stroke:var(--accent);stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
.slab{flex:1;min-width:0;font-weight:700;font-size:15px;display:flex;flex-direction:column;gap:2px}
.slab small{font-weight:500;font-size:12.5px;color:var(--muted)}
.sval{flex:none;font-size:13px;font-weight:700;color:var(--muted)}
.sval.ok{color:var(--good)}.sval.warn{color:var(--accent)}
.schev{flex:none;width:8px;height:8px;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(-45deg);margin-left:2px;opacity:.7}
.srow.sw input{position:absolute;opacity:0;pointer-events:none}
.swi{flex:none;width:48px;height:29px;border-radius:15px;background:var(--line);position:relative;transition:background .2s}
.swi::after{content:"";position:absolute;top:3px;left:3px;width:23px;height:23px;border-radius:50%;background:#fff;transition:transform .2s;box-shadow:0 1px 3px rgba(0,0,0,.4)}
.srow.sw input:checked+.swi{background:var(--good)}
.srow.sw input:checked+.swi::after{transform:translateX(19px)}
.kswhat{color:var(--text);font-size:14.5px;line-height:1.5;margin:6px 0 14px}
.kshow{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 14px;display:flex;flex-direction:column;gap:4px;font-size:13px;color:var(--muted);margin-bottom:14px}
.kshow b{color:var(--text);font-size:12px;letter-spacing:.8px;text-transform:uppercase}
.kshow a{color:var(--accent);font-weight:700;text-decoration:none;margin-top:4px}
.updbtn{flex:none;background:var(--good);color:#06150e;border:0;border-radius:999px;padding:8px 14px;font-weight:800;font-size:13px;cursor:pointer}''')
open(p, 'w').write(s)
print('applied settings')

# ---- Settings as a tab page ----
rep('<button class="tb" id="setTab">', '<button class="tb" data-v="settings" id="setTab">')
rep("""  <section id="v-library" hidden></section>""", """  <section id="v-library" hidden></section>
  <section id="v-settings" hidden></section>""")
rep("'list','library'].forEach", "'list','library','settings'].forEach")
rep("""  if(v === 'library') renderLibraryTab();
  scrollTo(0, 0);""", """  if(v === 'library') renderLibraryTab();
  if(v === 'settings') renderSettings();
  scrollTo(0, 0);""")
rep("$('#setTab').onclick = openSettings;\n", "")
rep("library:'My library',", "library:'My library', settings:'Settings',")
rep('.hero h2{font-size:31px}', '.hero h2{font-size:31px}\n.setpage{padding:4px 16px 40px}.setpage>.stitle{margin:8px 4px 0;font-family:var(--disp);font-weight:700;text-transform:uppercase;letter-spacing:.02em;font-size:30px}')
open(p, 'w').write(s)
print('applied settings page')

# ---- Cast on title cards: compact name chips, no photos ----
CASTOLD = """if(d.castL && d.castL.length) $('#mcast', b).innerHTML = `<div class="castsec fadein"><div class="lbl">Cast</div><div class="ppl nodrag">${d.castL.map(p => personHTML(p, p.role)).join('')}</div></div>`;"""
CASTNEW = """if(d.castL && d.castL.length) $('#mcast', b).innerHTML = `<div class="castsec fadein"><div class="lbl">Cast</div><div class="cchips">${d.castL.slice(0, 10).map(p => `<button class="pp cchip" data-person="${p.id}">${esc(p.name)}</button>`).join('')}</div></div>`;"""
rep('.hero h2{font-size:31px}', '.hero h2{font-size:31px}\\n.cchips{display:flex;flex-wrap:wrap;gap:6px}.pp.cchip{display:inline-flex;width:auto;flex:none;padding:6px 11px;border-radius:999px;border:1px solid var(--line);background:transparent;color:var(--text);font-weight:650;font-size:13px}')

# ---- poster badges: white number, yellow star; watchlist bookmark stays yellow ----
rep("  if(r) return `${fmtR(r)}★`;", "  if(r) return `${fmtR(r)}<i class=\"bst\">★</i>`;")
rep("t = badge(id); b.textContent = t;", "t = badge(id); b.innerHTML = t;")
# ---- Discover: no subtext under row titles ----
rep("openListPage('For you', $('#fysub') ? $('#fysub').textContent : '')", "openListPage('For you', '')")
rep('.rowhead p{margin:2px 0 0;color:var(--muted);font-size:12.5px}', '.rowhead p{display:none}')
# ---- header film graphic: red light ----
rep('<circle cx="36" cy="25.5" r="2.4" fill="#fff3d6"/>', '<circle cx="36" cy="25.5" r="2.4" fill="#e23a44"/>')
rep('.hero h2{font-size:31px}', '.hero h2{font-size:31px}\n.bd:not(.wl):not(.seen){color:#fff}.bd .bst{font-style:normal;color:#f5c518;margin-left:1px}.bd.wl::before{background:#f5c518!important}')

# ---- Streamlined series card (this app only) ----
# 1. story under the title, three lines, tap for the rest
rep('''      <div class="mact" id="mact"></div></div></div>
    <div id="mlib"></div><div id="mwatch"></div>
    <div id="imdb"><div class="msg">Loading IMDb episode ratings…</div></div>
    <div id="mcast"></div>
    <p class="ov" id="mov">${esc(m.overview || '')}</p>''', '''      <div class="mact" id="mact"></div></div></div>
    <p class="ov clamp" id="mov" onclick="this.classList.toggle('clamp')">${esc(m.overview || '')}</p>
    <div id="mlib"></div><div id="mwatch"></div>
    <div id="imdb"><div class="msg">Loading IMDb episode ratings…</div></div>
    <div id="mcast"></div>''')
# 2. where to watch: one row of streaming services (rent/buy only if nothing streams), no source line
rep('''  return `<div class="prov fadein">${p.stream.length ? '<div class="lbl">Stream it on</div>' + row(p.stream) : ''}
    ${p.rent.length ? '<div class="lbl">Rent or buy</div>' + row(p.rent) : ''}<div class="small" style="margin-top:8px">US availability from JustWatch</div></div>`;''',
'''  return `<div class="prov fadein">${p.stream.length ? '<div class="lbl">Stream it on</div>' + row(p.stream) : '<div class="lbl">Rent or buy</div>' + row(p.rent)}</div>`;''')
# 3-5. one ratings line; a strip of season averages with the full chart on its own page; short legend
rep('''  const lbl = x => `S${x.s} E${x.e}`;
  box.innerHTML = `<div class="imdbbox"><span class="ib2">IMDb</span><b>${d.r.toFixed(1)}</b><span class="small">${fmtV(d.v)} votes · ${all.length} rated episodes</span></div>
    ${best ? `<div class="hl"><div data-s="${best.s}" data-e="${best.e}">Best episode<b>${esc(best.t || lbl(best))}</b>${lbl(best)} · ${best.r.toFixed(1)}</div>
      <div data-s="${worst.s}" data-e="${worst.e}">Lowest rated<b>${esc(worst.t || lbl(worst))}</b>${lbl(worst)} · ${worst.r.toFixed(1)}</div></div>` : ''}
    <div class="chartwrap"><table class="hm">${rowsH}</table></div>
    <div class="legend"><span><i class="c9"></i>9+ Absolute cinema</span><span><i class="c8"></i>8+ Great</span><span><i class="c7"></i>7+ Good</span>
      <span><i class="c6"></i>6+ Regular</span><span><i class="c5"></i>5+ Bad</span><span><i class="c4"></i>Garbage</span></div>
    <div class="epinfo" id="epi" hidden></div>
    <div class="small" style="margin-top:8px;text-align:center">Episode ratings from IMDb, updated ${esc(meta.built)}.</div>`;''',
'''  const lbl = x => `S${x.s} E${x.e}`;
  const legend = '<div class="legend lg2"><span><i class="c9"></i>9+</span><span><i class="c8"></i>8+</span><span><i class="c7"></i>7+</span><span><i class="c6"></i>6+</span><span><i class="c5"></i>5+</span><span><i class="c4"></i>under 5</span></div>';
  const full = `<div class="chartwrap"><table class="hm">${rowsH}</table></div>${legend}<div class="epinfo" id="epi" hidden></div>`;
  const big = ss.length > 3;
  box.innerHTML = `<div class="rline"><span class="ib2">IMDb</span><b>${d.r.toFixed(1)}</b>${best ? `<button data-s="${best.s}" data-e="${best.e}">Best ${lbl(best)} <em>${best.r.toFixed(1)}</em></button><button data-s="${worst.s}" data-e="${worst.e}">Lowest ${lbl(worst)} <em>${worst.r.toFixed(1)}</em></button>` : ''}</div>
    ${big ? `<div class="sstrip nodrag">${ss.map(n => { const a = avg(d.s[n]); return `<button class="${heat(a)}" data-sea="${n}"><small>S${n}</small>${a == null ? '–' : a.toFixed(1)}</button>`; }).join('')}</div>
      <button class="btn ghost allep" id="allep">See all episodes</button><div class="epinfo" id="epi" hidden></div>` : full}`;
  const bindCells = (root, focus) => {
    const info = (n, e) => {
      const x = (d.s[n] || []).find(q => q[0] === e); if(!x) return;
      $$('.hm td.sel', root).forEach(c => c.classList.remove('sel'));
      const cell = $(`.hm td[data-s="${n}"][data-e="${e}"]`, root); if(cell){ cell.classList.add('sel'); cell.scrollIntoView({ block:'nearest', inline:'center', behavior:'smooth' }); }
      const ep = $('#epi', root); ep.hidden = false;
      ep.innerHTML = `<b>S${n} E${e}</b> &nbsp;${esc(x[3] || 'Untitled episode')}<br><span class="small">${x[1] == null ? 'Not rated yet' : `IMDb ${x[1].toFixed(1)} · ${x[2].toLocaleString()} votes`}</span>`;
    };
    $$('.hm [data-s][data-e]', root).forEach(c => c.onclick = () => info(+c.dataset.s, +c.dataset.e));
    if(focus) info(focus.s, focus.e);
    return info;
  };
  const openFull = (focus, sea) => {
    const p = sheet(`<h2 class="stitle">All episodes</h2>${full}`, { tall:true });
    bindCells(p.body, focus);
    if(sea && !focus){ const row = $$('.hm tr', p.body)[ss.indexOf(sea) + 1]; if(row) setTimeout(() => row.scrollIntoView({ block:'center' }), 350); }
  };
  if(big){
    $('#allep', box).onclick = () => openFull();
    $$('[data-sea]', box).forEach(x => x.onclick = () => openFull(null, +x.dataset.sea));
    $$('.rline [data-s]', box).forEach(x => x.onclick = () => openFull({ s:+x.dataset.s, e:+x.dataset.e }));
  }else{
    const info = bindCells(box);
    $$('.rline [data-s]', box).forEach(x => x.onclick = () => info(+x.dataset.s, +x.dataset.e));
  }
  return;''')
rep('''  const info = (n, e) => {
    const x = (d.s[n] || []).find(q => q[0] === e); if(!x) return;
    $$('.hm td.sel', box).forEach(c => c.classList.remove('sel'));''', '''  const info0 = (n, e) => {
    const x = (d.s[n] || []).find(q => q[0] === e); if(!x) return;
    $$('.hm td.sel', box).forEach(c => c.classList.remove('sel'));''')
rep('''  $$('[data-s][data-e]', box).forEach(c => c.onclick = () => info(+c.dataset.s, +c.dataset.e));
}''', '''}''')
# 6. cast: top five, one row
s = s.replace('d.castL.slice(0, 10).map(p => `<button class="pp cchip"', 'd.castL.slice(0, 5).map(p => `<button class="pp cchip"')
rep('.hero h2{font-size:31px}', '''.hero h2{font-size:31px}
.ov{color:#fff}.ov.clamp{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer}
.cchips{display:flex!important;gap:6px;flex-wrap:nowrap!important;overflow-x:auto;scrollbar-width:none;margin:0 -16px;padding:0 16px}.cchips::-webkit-scrollbar{display:none}
.rline{display:flex;align-items:center;flex-wrap:wrap;gap:6px 12px;margin-top:16px;font-size:13.5px}
.rline b{font-family:var(--disp);font-size:24px;line-height:1;margin-right:4px}
.rline button{background:none;border:0;color:#fff;padding:0;font:inherit;font-weight:650;cursor:pointer}
.rline em{font-style:normal;font-weight:800}
.sstrip{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;margin:12px -16px 0;padding:0 16px}.sstrip::-webkit-scrollbar{display:none}
.sstrip button{flex:none;border:0;border-radius:10px;padding:6px 10px;font-weight:800;font-size:14px;display:flex;flex-direction:column;align-items:center;min-width:52px;cursor:pointer}
.sstrip small{font-size:10.5px;font-weight:700;opacity:.85}
.allep{margin-top:12px}
.lg2 span{gap:4px}''')


# ---- Series card: overall IMDb (+ Rotten Tomatoes) up top like movies; no episode chart ----
rep('    <div id="imdb"><div class="msg">Loading IMDb episode ratings…</div></div>\n', '')
rep('''<div class="meta" id="md"></div>
      <div class="mact" id="mact"></div></div></div>
    <p class="ov clamp" id="mov"''', '''<div class="meta" id="md"></div>
      <div id="mimdb"></div><div class="mact" id="mact"></div></div></div>
    <p class="ov clamp" id="mov"''')
rep("""  }catch(e){ $('#imdb', b).innerHTML = `<div class="msg">${errText(e)}</div>`; return; }
  if(!d.imdb){""", """  }catch(e){ return; }
  if(d.imdb){ const sc = $('#mimdb', b); sc.className = 'scores';
    rtScore(d.imdb).then(v => { if(v && s.el.isConnected) sc.insertAdjacentHTML('beforeend', `<span class="rt" title="Rotten Tomatoes critics score">${parseInt(v) < 60 ? SPLAT : TOMATO}${esc(v)}</span>`); });
    imdbFile(d.imdb).then(res => { if(res.state === 'ok' && res.d.r && s.el.isConnected) sc.insertAdjacentHTML('afterbegin', `<span class="imdbm"><span class="ib2">IMDb</span>${res.d.r.toFixed(1)}<span class="small">${fmtV(res.d.v)} votes</span></span>`); }); }
  return;
  if(!d.imdb){""")


# ---- Every full-height card is its own page: slides in from the right, glass back button (one step),
#      and a glass X (top right) that closes them all when pages are stacked ----
rep("""  sh.innerHTML = `<div class="grab"><i></i></div><div class="sbody">${html}</div>`;""",
"""  sh.innerHTML = `<div class="grab"><i></i></div><div class="sbody">${html}</div>` + (isPage ? GLASS_BTNS : '');
  if(isPage) sh.classList.add('page');""")
rep("""  sheetStack.push(s);
  document.body.classList.add('locked');""", """  if(isPage){
    if(sheetStack.some(x => x.el.classList.contains('page'))) sh.classList.add('deep');
    $('.glback', sh).onclick = () => closeSheet(s);
    $('.glall', sh).onclick = () => { const i = sheetStack.findIndex(x => x.el.classList.contains('page')); if(i >= 0) closeSheet(sheetStack[i]); };
  }
  sheetStack.push(s);
  document.body.classList.add('locked');""")
rep("""function sheet(html, opt = {}){""", """const GLASS_BTNS = '<button class="glback" aria-label="Back"><svg viewBox="0 0 24 24"><path d="M14.5 5.5L8 12l6.5 6.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg></button>'
  + '<button class="glback glall" aria-label="Back to the tab"><svg viewBox="0 0 24 24"><path d="M4.5 11 12 4.8l7.5 6.2M6.5 9.6v9h4v-5h3v5h4v-9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>';
function sheet(html, opt = {}){
  const isPage = !!(opt.page || /mvsheet|shcard/.test(opt.cls || ''));""")
# people and "See all" lists are pages too
rep("""<div class="msg" style="grid-column:1/-1">Loading films…</div></div>`, { tall:true })""", """<div class="msg" style="grid-column:1/-1">Loading films…</div></div>`, { tall:true, page:true })""")
rep("""<div class="sent" style="height:30px"></div><div class="msg"></div>`, { tall:true });""", """<div class="sent" style="height:30px"></div><div class="msg"></div>`, { tall:true, page:true });""")


# ---- Leftover code: the episode chart is gone from series cards ----
_i = s.index("  return;\n  if(!d.imdb){ $('#imdb', b).innerHTML")
_j = s.index("/* ---------- Movie sheet ---------- */")
s = s[:_i] + "}\n\n" + s[_j:]
# ---- Faster start: Discover rows load when they're about to scroll into view, not a screen and a half early ----
rep("if(e.target.dataset.row != null) loadRow(e.target); else loadTile(e.target);\n  }), { rootMargin:'500px 400px' });",
    "if(e.target.dataset.row != null) loadRow(e.target); else loadTile(e.target);\n  }), { rootMargin:'250px 400px' });")
# ---- A light tap you can feel when you rate (Android app) ----
rep("function setRating(f, r, quiet){", "function setRating(f, r, quiet){\n  try{ NATIVE && r && navigator.vibrate && navigator.vibrate(12); }catch(e){}")


# ---- Start on: Discover, Library or the last page you were on ----
rep("""function setView(v, o = {}){""", """function setView(v, o = {}){
  try{ if(['discover','genres','films','library','settings','diary','tv'].includes(v)) store.set('rclib.lastview', v); }catch(e){}""")
rep("""\nsetView('discover');\n""", """\n{ const st = store.get('rclib.start', 'discover'), last = store.get('rclib.lastview', 'discover');
  setView(st === 'last' && last && last !== 'films' ? last : st === 'library' ? 'library' : 'discover'); }\n""")


# ---- Rating cards: no "Rate the franchise" pill ----
rep("    if(d.col){\n      $('#msaga', b).innerHTML", "    if(false && d.col){\n      $('#msaga', b).innerHTML")
# ---- Rating cards: the header picture stays still while the card scrolls over it ----
rep("$('.bdrop', b).style.backgroundImage", "$('.bdrop', s.el).style.backgroundImage", 2)
rep("""  if(isPage) sh.classList.add('page');""", """  if(isPage) sh.classList.add('page');
  { const bd = /mvsheet/.test(opt.cls || '') && $('.sbody > .bdrop', sh); if(bd) sh.insertBefore(bd, sh.firstChild); }""")


# ---- Pages close only with the glass back/home buttons (or the phone's back gesture), never by pulling down ----
rep("""  makeDraggable(s);
  return s;""", """  if(!isPage) makeDraggable(s);
  return s;""")


# ---- Library tab: the search bar takes the place of the app header ----
rep("""  try{ if(['discover','genres','films','library','settings','diary','tv'].includes(v)) store.set('rclib.lastview', v); }catch(e){}""",
"""  try{ if(['discover','genres','films','library','settings','diary','tv'].includes(v)) store.set('rclib.lastview', v); }catch(e){}
  if(!searchOn || o.fromPop) document.body.classList.toggle('libv', v === 'library');""")
# ---- Discover: "Trending now" becomes a stack of several you can swipe through ----
_i = s.index("async function loadHero(){"); _j = s.index("async function loadRow(sec){")
s = s[:_i] + r'''async function loadHero(){
  const h = $('#hero');
  try{
    const d = await listPage('/trending/movie/week', {}, 1);
    const pool = d.results.filter(m => m.backdrop).slice(0, 6);
    if(!pool.length){ h.remove(); return; }
    let at = 0;
    h.classList.add('hstack');
    h.innerHTML = pool.map((m, i) => `<div class="hcard" data-i="${i}" style="background-image:url(${IMG}w780${m.backdrop})"><div class="hin"><span class="tag">Trending now</span><h2>${esc(m.title)}</h2>
      <div class="hm">${esc(m.year)}${m.genres.length ? ' · ' + m.genres.slice(0, 3).map(g => GNAME[g]).filter(Boolean).join(', ') : ''}</div>
      <div class="hbtns"><button class="pill" data-hr="${m.id}">★ Rate</button><button class="pill ghost" data-hd="${m.id}">Details</button></div></div></div>`).join('')
      + `<div class="hdots">${pool.map((_, i) => `<i data-d="${i}"></i>`).join('')}</div>`;
    const cards = $$('.hcard', h), dots = $$('.hdots i', h);
    const lay = () => {
      cards.forEach((c, i) => { const p = (i - at + pool.length) % pool.length; c.dataset.p = p < 3 ? p : 'x'; });
      dots.forEach((x, i) => x.classList.toggle('on', i === at));
    };
    const go = n => { at = (at + n + pool.length) % pool.length; lay(); };
    lay();
    let sx = null, sy = 0, moved = false;
    h.addEventListener('pointerdown', e => { sx = e.clientX; sy = e.clientY; moved = false; });
    h.addEventListener('pointermove', e => { if(sx != null && Math.abs(e.clientX - sx) > 12 && Math.abs(e.clientX - sx) > Math.abs(e.clientY - sy)) moved = true; });
    h.addEventListener('pointerup', e => { if(sx != null && moved){ go(e.clientX < sx ? 1 : -1); } sx = null; });
    h.addEventListener('pointercancel', () => { sx = null; });
    h.onclick = e => {
      if(moved){ moved = false; return; }
      const r = e.target.closest('[data-hr]'); if(r){ openRatepad(+r.dataset.hr, false); return; }
      const dd = e.target.closest('[data-hd]'); if(dd){ openMovie(+dd.dataset.hd); return; }
      const dt = e.target.closest('[data-d]'); if(dt){ at = +dt.dataset.d; lay(); return; }
      const c = e.target.closest('.hcard'); if(!c) return;
      if(c.dataset.p === '0') openMovie(pool[+c.dataset.i].id); else { at = +c.dataset.i; lay(); }
    };
  }catch(e){ h.outerHTML = `<div class="onb"><p>${errText(e)}</p></div>`; }
}
''' + s[_j:]


# ---- Rating pages (Discover, Genres, search): Apple TV style. The tall poster art fills the top, title large and centered ----
rep("""  const s = sheet(`<div class="bdrop" style="${m.backdrop ? `background-image:url(${IMG}w780${m.backdrop})` : ''}"></div>""",
    """  const s = sheet(`<div class="bdrop" style="${f.poster || m.poster ? `background-image:url(${IMG}w780${f.poster || m.poster})` : ''}"></div>""")
rep("""  const s = sheet(`<div class="bdrop" style="${f.backdrop || m.backdrop ? `background-image:url(${IMG}w780${f.backdrop || m.backdrop})` : ''}"></div>""",
    """  const s = sheet(`<div class="bdrop" style="${f.poster || m.poster ? `background-image:url(${IMG}w780${f.poster || m.poster})` : ''}"></div>""")
rep("""    if(d.backdrop) $('.bdrop', s.el).style.backgroundImage = `url(${IMG}w780${d.backdrop})`;""",
    """    if(d.poster || d.backdrop) $('.bdrop', s.el).style.backgroundImage = `url(${IMG}w780${d.poster || d.backdrop})`;""", 2)


# ==== APPLE TV ART (to undo: delete this block and the "APPLE TV ART" block in theme.css) ====
# Rating pages use TMDB's textless poster art (sharper size) and the official title logo in place of the text title.
rep("{ tall:true, cls:'mvsheet' })", "{ tall:true, cls:'mvsheet', art:id })", 2)
rep("""  { const bd = /mvsheet/.test(opt.cls || '') && $('.sbody > .bdrop', sh); if(bd) sh.insertBefore(bd, sh.firstChild); }""",
"""  { const bd = /mvsheet/.test(opt.cls || '') && $('.sbody > .bdrop', sh); if(bd) sh.insertBefore(bd, sh.firstChild); }
  if(opt.art != null) appleArt(sh, opt.art);""")
rep("function sheet(html, opt = {}){", r"""// Textless key art and the title logo from TMDB (saved for a week); falls back quietly to the normal poster and text title
async function appleArt(sh, id){
  const tv = typeof id === 'string', tid = tv ? +String(id).slice(2) : id;
  let im;
  try{ im = await cached('art1:' + id, 24*7, () => tmdb(`/${tv ? 'tv' : 'movie'}/${tid}/images`, { include_image_language:'en,null' }).then(x => {
    const best = a => (a || []).slice().sort((p, q) => (q.vote_average - p.vote_average) || (q.vote_count - p.vote_count))[0];
    const clean = best((x.posters || []).filter(p => !p.iso_639_1));
    const logo = best((x.logos || []).filter(l => l.iso_639_1 === 'en')) || best(x.logos || []);
    return { poster:clean ? clean.file_path : '', logo:logo ? logo.file_path : '', ratio:logo ? logo.aspect_ratio : 0 };
  })); }catch(e){ return; }
  if(!sh.isConnected || !im) return;
  const bd = $('.bdrop', sh) || $('.shbg', sh);
  const art = im.poster || (filmOf(id) || {}).poster;
  if(bd && art){ const pic = new Image(); pic.onload = () => { bd.style.backgroundImage = `url(${pic.src})`; }; pic.src = IMG + 'original' + art; }
  const h2 = $('.mvhead h2', sh) || $('.shname', sh);
  if(h2 && im.logo){
    const lg = new Image(); lg.className = 'mtlogo'; lg.alt = h2.textContent;
    lg.onload = () => { if(!sh.isConnected) return; h2.classList.add('haslogo'); h2.prepend(lg); };
    lg.src = IMG + (im.logo.endsWith('.svg') ? 'original' : 'w500') + im.logo;
    if(im.ratio && im.ratio < 1.6) lg.classList.add('tall');
  }
}
function sheet(html, opt = {}){""")
# ==== end APPLE TV ART ====


# ---- No talk shows anywhere (TMDB's "Talk" TV genre) ----
rep("const keep = f => (!f.lang || LANGS.includes(f.lang)) && !f.video &&", "const keep = f => (!f.lang || LANGS.includes(f.lang)) && !f.video && !(f.tv && (f.genres || []).includes(10767)) &&")

rep("""  const out = page === 1 && !params.query ? await cached(k, 6, run) : (mem.get(k) || await run().then(d => (mem.set(k, d), d)));
  remember(out.results);""", """  const out = page === 1 && !params.query ? await cached(k, 6, run) : (mem.get(k) || await run().then(d => (mem.set(k, d), d)));
  out.results = (out.results || []).filter(keep);   // also clears talk shows out of rows saved earlier
  remember(out.results);""")


# ---- Logo: the Neo Reel only (no "Rolling Credits" wordmark) ----
import base64 as _b64
_reel = 'data:image/png;base64,' + _b64.b64encode(open(os.path.join(SRC, 'neo-reel-176.png'), 'rb').read()).decode()
_i = s.index('<svg class="hproj" id="hproj"'); _j = s.index('</svg>', _i) + len('</svg>')
s = s[:_i] + f'<img class="hproj nreel" id="hproj" src="{_reel}" alt="" draggable="false">' + s[_j:]

# ---------- ACCENT LOGO: inline folded triangle tinted by --accent ----------
_i = s.index('<img class="hproj nreel" id="hproj"'); _j = s.index('>', _i) + 1
_svg = ('<svg class="hproj nreel" id="hproj" viewBox="40 60 440 520" aria-hidden="true"><defs>'
 '<mask id="lgm"><rect width="600" height="700" fill="#000"/><path d="M88 112 L432 322 L88 532 Z" fill="#fff" stroke="#fff" stroke-width="52" stroke-linejoin="round"/>'
 '<rect x="100" y="168" width="58" height="66" rx="13" fill="#000"/><rect x="100" y="290" width="58" height="66" rx="13" fill="#000"/><rect x="100" y="412" width="58" height="66" rx="13" fill="#000"/></mask>'
 '<linearGradient id="lgt" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".35"/><stop offset="1" stop-color="#000" stop-opacity=".08"/></linearGradient>'
 '<linearGradient id="lgb" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".27"/><stop offset="1" stop-color="#000" stop-opacity=".45"/></linearGradient>'
 '<linearGradient id="lgs" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".28"/><stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient></defs>'
 '<g mask="url(#lgm)"><rect width="600" height="700" style="fill:var(--accent)"/><rect width="600" height="322" fill="url(#lgt)"/>'
 '<rect y="322" width="600" height="400" fill="url(#lgb)"/><rect y="322" width="600" height="40" fill="url(#lgs)"/></g></svg>')
s = s[:_i] + _svg + s[_j:]

# ---------- HIDE TOP BAR ON SCROLL DOWN, SHOW ON SCROLL UP ----------
_hide_js = r"""
<script>(()=>{let y=scrollY,off=0,acc=0,gliding=false;
const bars=()=>document.querySelectorAll('header.top,body.libv .ltop');
const set=(o,glide)=>{off=o;bars().forEach(b=>{const h=b.offsetHeight+2;b.style.transition=glide?'transform .42s cubic-bezier(.22,1,.36,1)':'none';b.style.transform=o?`translateY(${-Math.min(o,h)}px)`:'';});};
addEventListener('scroll',()=>{const n=Math.max(0,scrollY),d=n-y;y=n;
if(document.querySelector('.top.searching')){if(off)set(0,true);return}
if(d>0){acc=0;if(n>0){let H=0;bars().forEach(b=>H=Math.max(H,b.offsetHeight+2));set(Math.min(H,off+d),false)}}
else if(d<0&&off){set(Math.max(0,Math.min(off+d,n)),false)}},{passive:true});
document.addEventListener('click',e=>{if(e.target.closest('nav,.tabbar,.tabs'))set(0,false)},true);})();</script>
"""
s = s.replace('</body>', _hide_js + '</body>', 1)


# ---------- LIGHT MODE: set the theme before the page paints ----------
s = s.replace('<style>', "<script>try{var _t=JSON.parse(localStorage.getItem('rclib.theme')||'\"dark\"');if(_t==='light'||(_t==='system'&&matchMedia('(prefers-color-scheme: light)').matches))document.documentElement.classList.add('light')}catch(e){}</script>\n<style>", 1)

open(p, 'w').write(s)
print('applied cast chips')
