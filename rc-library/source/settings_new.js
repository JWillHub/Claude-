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
    lib:'<rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z"/>'
  };
  const isCacheKey = k => /^(reellog|rclib)\.c\d?\./.test(k) || /^longbox\.c\d+\./.test(k) || k === 'rclib.links' || k === 'rclib.tinfo';
  const ico = k => `<span class="sic"><svg viewBox="0 0 24 24">${IC[k]}</svg></span>`;
  const row = (id, icon, label, sub, value, tone) => `<button class="srow" id="${id}">${ico(icon)}<span class="slab">${label}${sub ? `<small>${sub}</small>` : ''}</span>${value ? `<span class="sval ${tone || ''}">${value}</span>` : ''}<span class="schev"></span></button>`;
  const paint = () => {
    if(!b.isConnected) return;
    const tm = !!store.get(K_KEY, ''), rd = !!store.get(K_RD, ''), om = !!store.get(K_OMDB, ''), n = libTitles().length;
    $('#setbody', b).innerHTML = `
      <div class="sgroup"><div class="sgh">Connections</div><div class="scard">
        ${row('s-tmdb', 'film', 'TMDB', 'Posters, titles and details', tm ? 'Connected' : 'Set up', tm ? 'ok' : 'warn')}
        ${row('s-rd', 'cloud', 'Real-Debrid', 'Your library and playback', rd ? (n ? n + ' title' + (n === 1 ? '' : 's') : 'Connected') : 'Set up', rd ? 'ok' : '')}
        ${row('s-omdb', 'tomato', 'Rotten Tomatoes', 'Critic scores on title cards', om ? 'On' : 'Off', om ? 'ok' : '')}
      </div></div>
      <div class="sgroup"><div class="sgh">Browsing</div><div class="scard">
        <label class="srow sw">${ico('eye')}<span class="slab">Hide what I've rated<small>Keeps Discover and Genres fresh</small></span><input type="checkbox" id="hide" ${hideRated ? 'checked' : ''}><i class="swi"></i></label>
        ${NATIVE ? `<label class="srow sw">${ico('lib')}<span class="slab">Auto-play next episode<small>Starts the next one after a 10-second countdown</small></span><input type="checkbox" id="autop" ${store.get(K_AUTOPLAY, true) ? 'checked' : ''}><i class="swi"></i></label>` : ''}
      </div></div>
      <div class="sgroup"><div class="sgh">Your data</div><div class="scard">
        ${row('exp', 'down', 'Back up', 'Save ratings, diary and lists to a file')}
        ${row('imp', 'up', 'Restore', 'Bring them back from a backup file')}
        <div class="srow static stor"><span class="slab">Storage<small id="stinfo">Measuring…</small><i class="stbar"><b id="stfill"></b></i></span><button class="pill sclr" id="stclr">Clear cache</button></div>
      </div></div>
      ${NATIVE ? `<div class="sgroup"><div class="sgh">App</div><div class="scard"><div class="srow static" id="appupd"></div></div></div>` : ''}
      <div class="sgroup"><div class="scard">${row('s-about', 'info', 'About & data sources', '')}</div></div>
      <input type="file" id="file" accept=".json,application/json" hidden>`;
    $('#s-tmdb', b).onclick = () => keySheet({ name:'TMDB', what:'Rolling Credits gets every poster, title and detail from TMDB. A free key switches it on.',
      how:'themoviedb.org → sign in → Settings → API → copy the API key (or the read access token).', link:'https://www.themoviedb.org/settings/api',
      key:K_KEY, placeholder:'Paste your TMDB key', verify:true });
    $('#s-rd', b).onclick = () => keySheet({ name:'Real-Debrid', what:'Your Real-Debrid library becomes the Library tab, with a Play button on every title you have.',
      how:'real-debrid.com/apitoken → copy your API token.', link:'https://real-debrid.com/apitoken', key:K_RD, placeholder:'Paste your API token', secret:true,
      extra:'<button class="btn ghost" id="ks-lib">Open my library</button>',
      onSave:v => { if(!v){ rdLib = { synced:0, items:{} }; saveLib(); } },
      wire:(kb, ks) => { const x = $('#ks-lib', kb); if(x) x.onclick = () => { ks.close(); setTimeout(openLibrary, 60); }; } });
    $('#s-omdb', b).onclick = () => keySheet({ name:'Rotten Tomatoes', what:'Adds Rotten Tomatoes critic scores to title cards, using a free OMDb key (up to 1,000 lookups a day; each title is looked up once a week).',
      how:'omdbapi.com → API Key → choose the free tier → the key arrives by email.', link:'https://www.omdbapi.com/apikey.aspx', key:K_OMDB, placeholder:'Paste your OMDb key' });
    $('#hide', b).onchange = e => { hideRated = e.target.checked; store.set(K_HIDE, hideRated); built.discover = built.genres = built.tv = false; setView(view); };
    // Storage: your own data vs. cached data (posters, title details, stream links), and a button to clear the cache
    const measure = () => {
      let mine = 0, cache = 0;
      for(let i = 0; i < localStorage.length; i++){
        const k = localStorage.key(i), n = (k.length + (localStorage.getItem(k) || '').length) * 2;
        if(isCacheKey(k)) cache += n; else mine += n;
      }
      const mb = n => n < 1e6 ? Math.max(1, Math.round(n / 1e3)) + ' KB' : (n / 1e6).toFixed(1) + ' MB';
      const el = $('#stinfo', b); if(!el) return;
      el.textContent = `Yours ${mb(mine)} · Cache ${mb(cache)} · 5 MB max`;
      $('#stfill', b).style.width = Math.min(100, (mine + cache) / 5e6 * 100).toFixed(1) + '%';
    };
    measure();
    $('#stclr', b).onclick = () => {
      for(let i = localStorage.length - 1; i >= 0; i--){ const k = localStorage.key(i); if(isCacheKey(k)) localStorage.removeItem(k); }
      try{ sessionStorage.clear(); }catch(e){}
      if(typeof linkCache === 'object') Object.keys(linkCache).forEach(k => delete linkCache[k]);
      measure(); toast('Cache cleared. Your ratings and library are untouched.');
    };
    const ap = $('#autop', b); if(ap) ap.onchange = e => store.set(K_AUTOPLAY, e.target.checked);
    $('#exp', b).onclick = async () => {
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
    $('#imp', b).onclick = () => $('#file', b).click();
    $('#file', b).onchange = importBackup;
    $('#s-about', b).onclick = () => sheet(`<h2 class="stitle">About</h2>
      <p class="small" style="font-size:13.5px;line-height:1.55">Film data and posters from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
      <p class="small" style="font-size:13.5px;line-height:1.55">IMDb ratings: information courtesy of IMDb (imdb.com), used with permission for personal, non-commercial use.</p>
      <p class="small" style="font-size:13.5px;line-height:1.55">Streaming availability from JustWatch. Rotten Tomatoes scores via OMDb.</p>`);
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
      ks.close(); paint(); toast(v ? `${esc(o.name)} connected` : `${esc(o.name)} disconnected`);
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
