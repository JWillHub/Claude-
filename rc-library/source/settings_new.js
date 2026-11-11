// Settings keys for the player and the look of the app
const K_ACCENT = 'rclib.accent', K_START = 'rclib.start', K_LASTV = 'rclib.lastview';
const K_PL_SKIP = 'rclib.pl.autoskip', K_PL_MID = 'rclib.pl.midnight', K_PL_AUD = 'rclib.pl.audio', K_PL_TXT = 'rclib.pl.text', K_PL_SIZE = 'rclib.pl.subsize', K_PL_STYLE = 'rclib.pl.substyle';
function applyAccent(){ try{ document.documentElement.style.setProperty('--accent', store.get(K_ACCENT, '#b8242c')); }catch(e){} }
applyAccent();
// Settings is its own tab page (not a pop-up), so closing one of its cards always lands back here
function openSettings(){ setView('settings'); }
function renderSettings(){
  const v = $('#v-settings');
  v.innerHTML = '<div class="setpage"><h2 class="stitle">Settings</h2><div id="setbody"></div></div>';
  const b = v;
  const IC = {
    film:'<path d="M4 5h16v14H4z"/><path d="M8 5v14M16 5v14M4 9h4M4 15h4M16 9h4M16 15h4"/>',
    cloud:'<path d="M7 18a4 4 0 0 1-.5-8A5.5 5.5 0 0 1 17 8.5 4.5 4.5 0 0 1 17.5 18z"/><path d="M12 11v5M9.5 13.5 12 16l2.5-2.5"/>',
    tomato:'<circle cx="12" cy="13.5" r="6.5"/><path d="M12 7c-1-2-3-2.5-4-2M12 7c1.5-1.8 3.5-2 4.5-1.5"/>',
    eye:'<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/><path d="M4 20 20 4"/>',
    down:'<path d="M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19h14"/>',
    up:'<path d="M12 15V4M7.5 8.5 12 4l4.5 4.5M5 19h14"/>',
    info:'<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.6v.1"/>',
    lib:'<rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z"/>',
    next:'<path d="M6 6.5v11l8-5.5z"/><path d="M17.5 6.5v11"/>',
    skip:'<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4.5v4h4"/><path d="M10 9.5v5l4-2.5z"/>',
    moon:'<path d="M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"/>',
    audio:'<path d="M5 9.5v5h3.5L13 18V6L8.5 9.5z"/><path d="M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11"/>',
    cc:'<rect x="3.5" y="5.5" width="17" height="13" rx="2.5"/><path d="M10.5 10.2a2 2 0 1 0 0 3.6M16.5 10.2a2 2 0 1 0 0 3.6"/>',
    size:'<path d="M4 18 8.5 6 13 18M5.6 14h5.8M15 18l2.8-7.5L20.5 18M15.8 16h4"/>',
    palette:'<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.3-1.2-1.6-1.2-2.8 0-1 .8-1.7 1.8-1.7h2.3a3.8 3.8 0 0 0 3.8-3.8C20.5 6.6 16.7 3.5 12 3.5z"/><circle cx="7.8" cy="11" r=".9"/><circle cx="10.5" cy="7.5" r=".9"/><circle cx="14.6" cy="7.5" r=".9"/>',
    home:'<path d="M4 11 12 4.5 20 11v8.5h-5.5v-5h-5v5H4z"/>'
  };
  const isCacheKey = k => /^(reellog|rclib)\.c\d?\./.test(k) || /^longbox\.c\d+\./.test(k) || k === 'rclib.links' || k === 'rclib.tinfo';
  const ico = k => `<span class="sic"><svg viewBox="0 0 24 24">${IC[k]}</svg></span>`;
  const row = (id, icon, label, sub, value, tone) => `<button class="srow" id="${id}">${ico(icon)}<span class="slab">${label}${sub ? `<small>${sub}</small>` : ''}</span>${value ? `<span class="sval ${tone || ''}">${value}</span>` : ''}<span class="schev"></span></button>`;
  const LANGS = [['en','English'],['es','Spanish'],['fr','French'],['de','German'],['it','Italian'],['pt','Portuguese'],['ja','Japanese'],['ko','Korean'],['zh','Chinese'],['hi','Hindi'],['ru','Russian']];
  const ACCENTS = [['#b8242c','Red'],['#e23a44','Crimson'],['#e8902a','Amber'],['#2fa86b','Green'],['#1aa3a3','Teal'],['#3b7ddd','Blue'],['#8a5cf6','Violet'],['#e0457b','Pink'],['#ffffff','White']];
  const PICKS = {};
  const pick = (id, cur, opts) => { PICKS[id] = opts; return `<button class="ssel gsel" id="${id}" value="${esc(cur)}">${esc((opts.find(o => String(o[0]) === String(cur)) || opts[0])[1])}</button>`; };
  // a picker button: opens the glass list, then behaves like a changed select (el.value + el.onchange)
  const wirePicks = box => $$('.gsel[id]', box).forEach(el => { const opts = PICKS[el.id]; if(!opts) return;
    el.onclick = () => glassPick(opts, el.value, v => { el.value = v; el.textContent = opts.find(o => String(o[0]) === v)[1]; el.onchange && el.onchange({ target:el }); }); });
  const sw = (id, icon, label, sub, on) => `<label class="srow sw">${ico(icon)}<span class="slab">${label}${sub ? `<small>${sub}</small>` : ''}</span><input type="checkbox" id="${id}" ${on ? 'checked' : ''}><i class="swi"></i></label>`;
  const selRow = (icon, label, sub, sel) => `<div class="srow static">${ico(icon)}<span class="slab">${label}${sub ? `<small>${sub}</small>` : ''}</span>${sel}</div>`;
  const langName = c => (LANGS.find(x => x[0] === c) || [])[1];
  // a settings section as its own page (glass back button), repainted when something changes
  const subPage = (title, build) => {
    const pg = sheet(`<h2 class="stitle">${esc(title)}</h2><div class="spg"></div>`, { tall:true, page:true });
    const draw = () => { if(!pg.el.isConnected) return; const box = $('.spg', pg.body); build(box, draw); wirePicks(box); };
    draw(); pg.refresh = draw; pg.onClose = () => paint();
    return pg;
  };
  const paint = () => {
    if(!b.isConnected) return;
    const tm = !!store.get(K_KEY, '');
    $('#setbody', b).innerHTML = `
      <div class="sgroup smain"><div class="scard">
        ${row('g-conn', 'cloud', 'Connections', '', tm ? '' : 'Set up', 'warn')}
        ${NATIVE ? row('g-play', 'next', 'Player', '') : ''}
        ${row('g-look', 'palette', 'Look and feel', '')}
        ${row('g-data', 'down', 'Your data', '')}
      </div></div>
      <div class="sgroup smain"><div class="scard">
        ${NATIVE ? '<div class="srow static" id="appupd"></div>' : ''}
        ${row('s-about', 'info', 'About', '')}
      </div></div>`;
    $('#g-conn', b).onclick = () => subPage('Connections', box => {
      const tm = !!store.get(K_KEY, ''), rd = !!store.get(K_RD, ''), om = !!store.get(K_OMDB, ''), n = libTitles().length;
      box.innerHTML = `<div class="scard">
        ${row('s-tmdb', 'film', 'TMDB', 'Posters, titles and details', tm ? 'Connected' : 'Set up', tm ? 'ok' : 'warn')}
        ${row('s-rd', 'cloud', 'Real-Debrid', 'Your library and playback', rd ? (n ? n + ' title' + (n === 1 ? '' : 's') : 'Connected') : 'Set up', rd ? 'ok' : '')}
        ${row('s-omdb', 'tomato', 'Rotten Tomatoes', 'Critic scores on title cards', om ? 'On' : 'Off', om ? 'ok' : '')}</div>`;
      $('#s-tmdb', box).onclick = () => keySheet({ name:'TMDB', what:'Rolling Credits gets every poster, title and detail from TMDB. A free key switches it on.',
        how:'themoviedb.org → sign in → Settings → API → copy the API key (or the read access token).', link:'https://www.themoviedb.org/settings/api',
        key:K_KEY, placeholder:'Paste your TMDB key', verify:true });
      $('#s-rd', box).onclick = () => keySheet({ name:'Real-Debrid', what:'Your Real-Debrid library becomes the Library tab, with a Play button on every title you have.',
        how:'real-debrid.com/apitoken → copy your API token.', link:'https://real-debrid.com/apitoken', key:K_RD, placeholder:'Paste your API token', secret:true,
        extra:'<button class="btn ghost" id="ks-lib">Open my library</button>',
        onSave:v => { if(!v){ rdLib = { synced:0, items:{} }; saveLib(); } },
        wire:(kb, ks) => { const x = $('#ks-lib', kb); if(x) x.onclick = () => { ks.close(); closeAllSheets(); setTimeout(openLibrary, 60); }; } });
      $('#s-omdb', box).onclick = () => keySheet({ name:'Rotten Tomatoes', what:'Adds Rotten Tomatoes critic scores to title cards, using a free OMDb key (up to 1,000 lookups a day; each title is looked up once a week).',
        how:'omdbapi.com → API Key → choose the free tier → the key arrives by email.', link:'https://www.omdbapi.com/apikey.aspx', key:K_OMDB, placeholder:'Paste your OMDb key' });
    });
    const gp = $('#g-play', b); if(gp) gp.onclick = () => subPage('Player', box => {
      const g = (k, d) => store.get(k, d);
      box.innerHTML = `<div class="sgh">Episodes</div><div class="scard">
        ${sw('p-auto', 'next', 'Auto-play next episode', 'A 10-second countdown when the credits start', g(K_AUTOPLAY, true))}
        ${sw('p-skip', 'skip', 'Skip intros automatically', 'Jumps past intros and recaps, with a tap to undo', g(K_PL_SKIP, false))}</div>
        <div class="sgh">Sound</div><div class="scard">
        ${sw('p-mid', 'moon', 'Midnight mode by default', 'Every video starts with loud peaks evened out', g(K_PL_MID, false))}
        ${selRow('audio', 'Audio language', 'When a file has more than one', pick('p-al', g(K_PL_AUD, ''), [['', 'Last used'], ...LANGS]))}</div>
        <div class="sgh">Subtitles</div><div class="scard">
        ${selRow('cc', 'Subtitle language', '', pick('p-tl', g(K_PL_TXT, ''), [['', 'Last used'], ['off', 'Off'], ...LANGS]))}
        ${selRow('size', 'Size', '', pick('p-ts', g(K_PL_SIZE, 'm'), [['s', 'Small'], ['m', 'Medium'], ['l', 'Large'], ['xl', 'Extra large']]))}
        ${selRow('cc', 'Style', '', pick('p-tst', g(K_PL_STYLE, 'shadow'), [['shadow', 'White with shadow'], ['outline', 'White with outline'], ['box', 'White on dark box'], ['yellow', 'Yellow with shadow']]))}
        <div class="subprev ${g(K_PL_STYLE, 'shadow')} s-${g(K_PL_SIZE, 'm')}"><span>This is how subtitles will look.</span></div></div>`;
      const bind = (id, k, isBool) => { const el = $('#' + id, box); el.onchange = () => { store.set(k, isBool ? el.checked : el.value); if(!isBool) draw2(); }; };
      const draw2 = () => { const pv = $('.subprev', box); pv.className = `subprev ${store.get(K_PL_STYLE, 'shadow')} s-${store.get(K_PL_SIZE, 'm')}`; };
      bind('p-auto', K_AUTOPLAY, true); bind('p-skip', K_PL_SKIP, true); bind('p-mid', K_PL_MID, true);
      bind('p-al', K_PL_AUD); bind('p-tl', K_PL_TXT); bind('p-ts', K_PL_SIZE); bind('p-tst', K_PL_STYLE);
    });
    $('#g-look', b).onclick = () => subPage('Look and feel', (box, redraw) => {
      const cur = store.get(K_ACCENT, ACCENTS[0][0]);
      box.innerHTML = `<div class="sgh">Accent color</div><div class="scard"><div class="srow static acc">${ACCENTS.map(([c, n]) =>
          `<button class="accsw${c === cur ? ' on' : ''}" data-c="${c}" aria-label="${n}" style="--c:${c}"></button>`).join('')}</div></div>
        <div class="sgh">Start on</div><div class="scard">
        ${selRow('home', 'When the app opens', '', pick('l-start', store.get(K_START, 'discover'), [['discover', 'Discover'], ['library', 'Library'], ['last', 'Last page I was on']]))}</div>
        <div class="sgh">Browsing</div><div class="scard">
        ${sw('hide', 'eye', "Hide what I've rated", 'Keeps Discover and Genres fresh', hideRated)}</div>`;
      $$('.accsw', box).forEach(x => x.onclick = () => { store.set(K_ACCENT, x.dataset.c); applyAccent(); hap(); redraw(); });
      $('#l-start', box).onchange = e => store.set(K_START, e.target.value);
      $('#hide', box).onchange = e => { hideRated = e.target.checked; store.set(K_HIDE, hideRated); built.discover = built.genres = built.tv = false; };
    });
    $('#g-data', b).onclick = () => subPage('Your data', box => {
      box.innerHTML = `<div class="scard">
        ${row('exp', 'down', 'Back up', 'Save ratings, diary and lists to a file')}
        ${row('imp', 'up', 'Restore', 'Bring them back from a backup file')}
        <div class="srow static stor"><span class="slab">Storage<small id="stinfo">Measuring…</small><i class="stbar"><b id="stfill"></b></i></span><button class="pill sclr" id="stclr">Clear cache</button></div></div>
        <input type="file" id="file" accept=".json,application/json" hidden>`;
      // Storage: your own data vs. cached data (title details, stream links), and a button to clear the cache
      const measure = () => {
        let mine = 0, cache = 0;
        for(let i = 0; i < localStorage.length; i++){
          const k = localStorage.key(i), n = (k.length + (localStorage.getItem(k) || '').length) * 2;
          if(isCacheKey(k)) cache += n; else mine += n;
        }
        const mb = n => n < 1e6 ? Math.max(1, Math.round(n / 1e3)) + ' KB' : (n / 1e6).toFixed(1) + ' MB';
        const el = $('#stinfo', box); if(!el) return;
        el.textContent = `Yours ${mb(mine)} · Cache ${mb(cache)} · 5 MB max`;
        $('#stfill', box).style.width = Math.min(100, (mine + cache) / 5e6 * 100).toFixed(1) + '%';
      };
      measure();
      $('#stclr', box).onclick = () => {
        for(let i = localStorage.length - 1; i >= 0; i--){ const k = localStorage.key(i); if(isCacheKey(k)) localStorage.removeItem(k); }
        try{ sessionStorage.clear(); }catch(e){}
        if(typeof linkCache === 'object') Object.keys(linkCache).forEach(k => delete linkCache[k]);
        measure(); toast('Cache cleared. Your ratings and library are untouched.');
      };
      $('#exp', box).onclick = async () => {
        const json = JSON.stringify({ app:'reellog', v:5, entries, ratings, watch, seen, favs }, null, 1);
        if(NATIVE){   // in the Android app: Android's own "Save to" screen (Downloads, Drive, ...)
          try{ const r = await callNative('RCFiles', 'save', { name:`rolling-credits-${today()}.json`, data:json, mime:'application/json' }); if(r && r.saved) toast('Backup saved'); }
          catch(e){ toast('Could not save the backup'); }
          return;
        }
        const blob = new Blob([json], { type:'application/json' });
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
        a.download = `rolling-credits-${today()}.json`; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
      };
      $('#imp', box).onclick = () => $('#file', box).click();
      $('#file', box).onchange = importBackup;
    });
    $('#s-about', b).onclick = () => sheet(`<h2 class="stitle">About</h2>
      <p class="small" style="font-size:13.5px;line-height:1.55">Film data and posters from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
      <p class="small" style="font-size:13.5px;line-height:1.55">IMDb ratings: information courtesy of IMDb (imdb.com), used with permission for personal, non-commercial use.</p>
      <p class="small" style="font-size:13.5px;line-height:1.55">Streaming availability from JustWatch. Rotten Tomatoes scores via OMDb. Intro timestamps from TheIntroDB.</p>`);
    if(NATIVE){ fillUpdate(b); checkForUpdate(); }
  };
  // One connection: what it's for, where to get the key, the key itself
  function keySheet(o){
    const cur = store.get(o.key, '');
    const ks = sheet(`<h2 class="stitle">${esc(o.name)}</h2>
      <p class="kswhat">${esc(o.what)}</p>
      <div class="kshow"><b>Where to get it</b><span>${esc(o.how)}</span><a href="${esc(o.link)}" target="_blank" rel="noopener">Open ${esc(o.link.replace(/^https:\/\/(www\.)?/, '').split('/')[0])} ↗</a></div>
      <input id="ks-in" ${o.secret ? 'type="password"' : ''} value="${esc(cur)}" placeholder="${esc(o.placeholder)}" autocomplete="off" spellcheck="false">
      <button class="btn" id="ks-save">${cur ? 'Save changes' : 'Connect'}</button>
      ${cur ? '<button class="btn ghost" id="ks-del">Disconnect</button>' : ''}
      ${cur && o.extra ? o.extra : ''}`);
    const kb = ks.body, btn = $('#ks-save', kb);
    const finish = (v, first) => {
      o.onSave && o.onSave(v);
      if(o.key === K_KEY && (first || !v)){ built.discover = built.genres = built.tv = false; setView(view); }
      ks.close(); paint(); sheetStack.forEach(x => x.refresh && x.refresh()); toast(v ? `${esc(o.name)} connected` : `${esc(o.name)} disconnected`);
    };
    btn.onclick = async () => {
      const v = $('#ks-in', kb).value.trim(); if(!v){ $('#ks-in', kb).focus(); return; }
      if(!store.set(o.key, v)){ btn.textContent = 'Could not save — storage is full'; return; }
      if(!o.verify){ finish(v, !cur); return; }
      btn.textContent = 'Checking…'; btn.disabled = true;
      try{ await tmdb('/configuration'); finish(v, !cur); }
      catch(err){
        btn.disabled = false;
        if(err.message === 'badkey'){ store.set(o.key, cur); btn.textContent = 'TMDB rejected that key — check it and try again'; }
        else{ btn.textContent = 'Saved (could not check it right now)'; setTimeout(() => finish(v, !cur), 900); }
      }
    };
    const del = $('#ks-del', kb); if(del) del.onclick = () => { store.set(o.key, ''); finish('', false); };
    o.wire && o.wire(kb, ks);
  }
  async function importBackup(ev){
    const f = ev.target.files[0]; if(!f) return;
    try{
      const data = JSON.parse(await f.text());
      const list = Array.isArray(data) ? data : data.entries || [];
      const ids = new Set(entries.map(e => e.id)); let n = 0, rn = 0;
      for(const e of list) if(e && e.id && e.tmdb && e.date && !ids.has(e.id)){ entries.push(e); n++; }
      if(data.watch) for(const [id, w] of Object.entries(data.watch)) if(!watch[id] && !ratings[id]) watch[id] = w;
      if(data.seen) for(const [id, w] of Object.entries(data.seen)) if(!seen[id] && !ratings[id]) seen[id] = w;
      if(data.favs) for(const [id, p] of Object.entries(data.favs)) if(!favs[id]) favs[id] = p;
      if(data.ratings) for(const [id, r] of Object.entries(data.ratings)) if(!ratings[id] || (r.at || 0) > (ratings[id].at || 0)){ ratings[id] = r; rn++; }
      if(!data.ratings) list.forEach(e => { if(e.rating && !ratings[e.tmdb]){ ratings[e.tmdb] = { r:e.rating, title:e.title, year:e.year, poster:e.poster, at:e.added || 0 }; rn++; } });
      saveAll(); afterChange(); paint(); toast(`Restored ${n} diary entr${n === 1 ? 'y' : 'ies'} and ${rn} rating${rn === 1 ? '' : 's'}`);
    }catch(e){ toast('That file is not a Rolling Credits backup.'); }
  }
  paint();
}
