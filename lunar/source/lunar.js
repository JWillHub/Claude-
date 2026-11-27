// Lunar: small additions on top of the shared Rolling Credits code
(function(){
  // Trending: thin progress lines showing which of the titles is up
  function prog(){
    const h = document.querySelector('#v-discover .hero.hstack'); if(!h) return;
    const cards = h.querySelectorAll('.hcard'); if(!cards.length) return;
    let p = h.querySelector('.lprog');
    if(!p){ p = document.createElement('div'); p.className = 'lprog'; p.setAttribute('aria-hidden', 'true');
      const fix = h.querySelector('.hfix'); (fix || h).appendChild(p); }
    if(p.children.length !== cards.length) p.innerHTML = Array.from(cards, () => '<i></i>').join('');
    const at = Array.from(cards).findIndex(c => c.dataset.p === '0');
    Array.from(p.children).forEach((x, i) => x.classList.toggle('on', i === at));
  }
  new MutationObserver(() => { clearTimeout(prog.t); prog.t = setTimeout(prog, 30); })
    .observe(document.documentElement, { subtree:true, attributes:true, attributeFilter:['data-p'], childList:true });
})();
// Genres tab: big serif heading, All / Movies / Series as buttons (instead of the filter pop-up)
(function(){
  const KINDS = [['all', 'All'], ['movie', 'Movies'], ['tv', 'Series']];
  function dressGenres(){
    const v = document.getElementById('v-genres'); if(!v) return;
    const top = v.querySelector('.gtop'); if(!top || top.querySelector('.lh1')) return;
    top.insertAdjacentHTML('afterbegin', '<h1 class="lh1">Genres</h1>');
    const seg = document.createElement('div'); seg.className = 'lseg'; seg.setAttribute('role', 'group'); seg.setAttribute('aria-label', 'Show');
    const kind = (typeof gb === 'object' && gb.kind) || 'all';
    seg.innerHTML = KINDS.map(([k, l]) => `<button type="button" data-k="${k}" class="${k === kind ? 'on' : ''}" aria-pressed="${k === kind}">${l}</button>`).join('');
    seg.onclick = e => { const b = e.target.closest('button'); if(!b || typeof gb !== 'object') return;
      gb.kind = b.dataset.k; buildGenreBrowse(); };
    top.appendChild(seg);
  }
  // Genre page: the genre's name in big serif at the top of the page; titles under the posters
  function dressGenrePage(){
    const v = document.getElementById('v-genre'); if(!v || v.hidden) return;
    const pad = v.querySelector('.pad'); if(!pad) return;
    const name = (typeof genrePageTitle === 'object' && genrePageTitle && genrePageTitle.big) || '';
    let h = pad.querySelector('.lgh1');
    if(!h){ h = document.createElement('h1'); h.className = 'lgh1'; pad.insertBefore(h, pad.firstChild); }
    if(h.textContent !== name) h.textContent = name;
    // keep the title and its options pinned under the back button while the posters scroll
    const opts = pad.querySelector('.gopts');
    let top = pad.querySelector('.lgtop');
    if(!top){ top = document.createElement('div'); top.className = 'lgtop'; pad.insertBefore(top, pad.firstChild); }
    if(h.parentNode !== top) top.appendChild(h);
    if(opts && opts.parentNode !== top) top.appendChild(opts);
    v.querySelectorAll('.grid .pc:not([data-lc])').forEach(pc => { pc.dataset.lc = 1;
      const img = pc.querySelector('img'); const t = img && (img.alt || img.title); if(t) pc.insertAdjacentHTML('beforeend', `<span class="lcap">${t.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}</span>`); });
  }
  // See all / Rated / Watchlist / In library pages: same look as a genre page
  function dressListPage(){
    const v = document.getElementById('v-list'); if(!v || v.hidden) return;
    const pad = v.querySelector('.pad'); if(!pad) return;
    const name = (typeof listPageTitle === 'string' && listPageTitle) || '';
    let h = pad.querySelector('.lgh1');
    if(!h){ h = document.createElement('h1'); h.className = 'lgh1'; pad.insertBefore(h, pad.firstChild); }
    if(h.textContent !== name) h.textContent = name;
    v.querySelectorAll('.grid .pc:not([data-lc])').forEach(pc => { pc.dataset.lc = 1;
      const img = pc.querySelector('img'); const t = img && (img.alt || img.title); if(t) pc.insertAdjacentHTML('beforeend', `<span class="lcap">${t.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}</span>`); });
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(() => { dressGenres(); dressGenrePage(); dressListPage(); }, 20); })
    .observe(document.body, { subtree:true, childList:true });
})();
// Library: serif heading with the sort button, counts, and Everything / Movies / Series
(function(){
  let kind = 'all';
  const KINDS = [['all', 'Everything'], ['movie', 'Movies'], ['tv', 'Series']];
  function dress(){
    const v = document.getElementById('v-library'); if(!v || v.hidden) return;
    const top = v.querySelector('.ltop'), grid = document.getElementById('lgrid'); if(!top || !grid) return;
    if(!top.querySelector('.llhead')){
      top.insertAdjacentHTML('afterbegin', '<div class="llhead"><h1 class="lh1">Library</h1></div><p class="lcount"></p>');
      const seg = document.createElement('div'); seg.className = 'lseg'; seg.setAttribute('role', 'group'); seg.setAttribute('aria-label', 'Show');
      seg.innerHTML = KINDS.map(([k, l]) => `<button type="button" data-k="${k}">${l}</button>`).join('');
      seg.onclick = e => { const b = e.target.closest('button'); if(!b) return; kind = b.dataset.k; dress(); };
      top.appendChild(seg);
    }
    top.querySelectorAll('.lseg button').forEach(b => { const on = b.dataset.k === kind; b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
    grid.classList.toggle('lk-movie', kind === 'movie'); grid.classList.toggle('lk-tv', kind === 'tv');
    const m = grid.querySelectorAll('[data-lmov]').length, s = grid.querySelectorAll('[data-lshow]').length;
    const c = top.querySelector('.lcount'), txt = (m || s) ? `${m} movie${m === 1 ? '' : 's'} · ${s} series` : '';
    if(c.textContent !== txt) c.textContent = txt;
    const h = v.querySelector('.cwh'); if(h && h.lastChild && h.lastChild.nodeType === 3 && h.lastChild.textContent.trim() === 'Continue watching') h.lastChild.textContent = 'Keep watching';
    let lab = v.querySelector('.llabel');
    if(v.querySelector('.cwband') && !lab){ grid.insertAdjacentHTML('beforebegin', '<h2 class="llabel">Everything you have</h2>'); }
    if(!v.querySelector('.cwband') && lab) lab.remove();
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(dress, 20); }).observe(document.body, { subtree:true, childList:true });
})();
// Settings: your numbers at the top, a short line under each section
(function(){
  const SUB = { 'g-conn':'Real-Debrid and TMDB', 'g-look':'Start page', 'g-pref':'Filters, data saver and haptics', 'g-data':'Back up, restore and storage', 'g-player':'Episodes, sound and subtitles', 's-about':'Version and updates', 'g-roku':'Watch your library on your TV' };
  function dress(){
    const v = document.getElementById('v-settings'); if(!v || v.hidden) return;
    const page = v.querySelector('.setpage'), title = page && page.querySelector('.stitle'); if(!page || !title) return;
    if(!page.querySelector('.lstats') && title.textContent.trim() === 'Settings'){
      let r = 0, w = 0, l = 0;
      try{ r = Object.keys(ratings || {}).length; }catch(e){}
      try{ w = Object.keys(watch || {}).length; }catch(e){}
      try{ l = libTitles().length; }catch(e){}
      title.insertAdjacentHTML('afterend', `<section class="lstats" aria-label="Your numbers"><button type="button" data-k="rated"><b>${r}</b><span>Rated</span></button><button type="button" data-k="watch"><b>${w}</b><span>Watchlist</span></button><button type="button" data-k="lib"><b>${l}</b><span>In library</span></button></section>`);
      page.querySelector('.lstats').onclick = e => { const b = e.target.closest('button'); if(b) openStat(b.dataset.k); };
    }
    page.querySelectorAll('.srow[id]').forEach(row => { const s = SUB[row.id], lab = row.querySelector('.slab');
      if(s && lab && !lab.querySelector('.lsub')) lab.insertAdjacentHTML('beforeend', `<small class="lsub">${s}</small>`); });
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(dress, 20); }).observe(document.body, { subtree:true, childList:true });
})();

// Sharper pictures: posters load at w500 (they're shown big on a high-density screen), the Trending backdrop at full size
(function(){
  function sharpen(root){
    if(window.LDS) return;   // data saver keeps the smaller images
    (root.querySelectorAll ? root : document).querySelectorAll('img[src*="/t/p/w185/"],img[src*="/t/p/w342/"]').forEach(im => {
      if(im.closest('.rpbg')) return;
      im.src = im.src.replace(/\/t\/p\/w(185|342)\//, '/t/p/w500/'); });
    document.querySelectorAll('#v-discover .hcard[style*="/t/p/w780/"]').forEach(c => {
      c.style.backgroundImage = c.style.backgroundImage.replace('/t/p/w780/', '/t/p/original/'); });
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(() => sharpen(document), 15); })
    .observe(document.body, { subtree:true, childList:true, attributes:true, attributeFilter:['src', 'style'] });
})();
// Ratings row on movie and show pages: IMDb · Critics · Your rating, as big numbers
(function(){
  const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  function build(box){
    const sheetEl = box.closest('.sh') || document; const s5 = sheetEl.querySelector('.ratebar .s5');
    const im = box.querySelector('.imdbm'), rt = box.querySelector('.rt');
    let imN = '', imV = ''; if(im){ im.childNodes.forEach(n => { if(n.nodeType === 3 && n.textContent.trim()) imN = n.textContent.trim(); }); const sm = im.querySelector('.small'); imV = sm ? sm.textContent.replace(' votes', '') : ''; }
    const rtV = rt ? rt.textContent.trim() : '';
    const r = s5 ? +s5.dataset.r || 0 : 0;
    const cols = [];
    if(imN) cols.push(`<div class="lsc"><b>${esc(imN)}</b><span>IMDb${imV ? ' · ' + esc(imV) : ''}</span></div>`);
    if(rtV) cols.push(`<div class="lsc"><b>${esc(rtV)}</b><span>Critics</span></div>`);
    cols.push(`<div class="lsc lmine${r ? '' : ' dim'}"><b>${r ? (r % 1 ? r.toFixed(1) : r) : '—'}</b><span>Your rating</span></div>`);
    const html = cols.join('');
    let wrap = box.querySelector('.lscw');
    if(!wrap){ wrap = document.createElement('div'); wrap.className = 'lscw'; wrap.style.display = 'contents'; box.appendChild(wrap); }
    if(wrap.innerHTML !== html) wrap.innerHTML = html;
  }
  function all(){ document.querySelectorAll('#mimdb').forEach(build); }
  // series page: big serif episode numbers
  function eps(){ document.querySelectorAll('.shcard .epc:not([data-ln])').forEach(c => { c.dataset.ln = 1;
    const b = c.querySelector('.ept b'); const n = b ? b.textContent.trim() : '';
    c.insertAdjacentHTML('afterbegin', `<span class="lepn" aria-hidden="true">${n ? n.padStart(2, '0') : ''}</span>`); }); }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(() => { all(); eps(); }, 25); })
    .observe(document.body, { subtree:true, childList:true, attributes:true, attributeFilter:['data-r'] });
})();

// Settings numbers open a page of those titles, with the usual back button
function openStat(k){
  try{
    if(k === 'watch'){ openStaticList('watch'); return; }
    if(k === 'rated'){
      const list = Object.entries(ratings || {}).sort((a, c) => (c[1].at || 0) - (a[1].at || 0));
      const b = openListPage('Rated', `${list.length} ${list.length === 1 ? 'title' : 'titles'}, newest first`);
      b.querySelector('.grid').innerHTML = list.map(([id, x]) => cardHTML({ id:pid(id), title:x.title, poster:x.poster })).join('');
      b.querySelector('.msg').textContent = list.length ? '' : 'Nothing rated yet.';
      setPageSource(list.length ? { title:'Rated', items:list.map(([id]) => ({ ...filmOf(pid(id)), id:pid(id) })) } : null);
      return;
    }
    if(k === 'lib'){
      const list = libSort(libTitles(), 'title');
      const b = openListPage('In library', `${list.length} ${list.length === 1 ? 'title' : 'titles'}`);
      b.querySelector('.grid').innerHTML = list.map(m => cardHTML(m)).join('');
      b.querySelector('.msg').textContent = list.length ? '' : 'Your library is empty.';
      setPageSource(list.length ? { title:'In library', items:list } : null);
    }
  }catch(e){ console.warn('stat page', e); }
}
// any leftover route to the rating card opens the title's page instead (rating lives there)
try{ openRatepad = id => openMovie(id); }catch(e){}
// The version and update button live on the About page, not the main Settings list
(function(){
  let home = null, keep = null;
  function place(){
    let a = document.getElementById('appupd');
    if(a) keep = a; else if(keep && home && home.isConnected) a = keep;   // About closed and took it along: bring it back
    if(!a) return;
    if(a.isConnected && a.closest('.smain')) home = a.parentNode;
    const about = [...document.querySelectorAll('.sh .stitle')].find(h => h.textContent.trim() === 'About' && h.closest('.sh:not(.closing)'));
    if(about){ if(!about.parentNode.contains(a)){ a.classList.add('labout'); about.after(a); } }
    else if(home && home.isConnected && !home.contains(a)){ a.classList.remove('labout'); home.insertBefore(a, home.firstChild); }
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(place, 15); }).observe(document.body, { subtree:true, childList:true });
})();

// Content filters: anime, reality/talk shows and little-known titles stay out of browsing (never out of search)
(function(){
  if(typeof tmdb !== 'function') return;
  const orig = tmdb, get = (k, d) => { try{ const v = localStorage.getItem('lunar.f.' + k); return v == null ? d : JSON.parse(v); }catch(e){ return d; } };
  const REAL = [10763, 10764, 10766, 10767], recent = Date.now() - 75 * 864e5;
  tmdb = async function(path, params){
    const j = await orig(path, params);
    if(!j || !Array.isArray(j.results) || /^\/search/.test(path)) return j;
    const anime = get('anime', false), real = get('real', false), votes = +get('votes', 0) || 0;
    if(!anime && !real && !votes) return j;
    const keep = m => {
      const g = m.genre_ids || (m.genres || []).map(x => x.id);
      if(anime && g.includes(16) && m.original_language === 'ja') return false;
      if(real && g.some(x => REAL.includes(x))) return false;
      if(votes && m.media_type !== 'person'){
        const d = Date.parse(m.release_date || m.first_air_date || '') || 0;
        if(d < recent && (m.vote_count || 0) < votes) return false;   // new and upcoming titles haven't had time to collect votes
      }
      return true;
    };
    return { ...j, results:j.results.filter(keep) };
  };
})();
// Haptics: a light tick on taps, and a switch that silences every vibration
(function(){
  const on = () => { try{ const v = localStorage.getItem('lunar.f.hap'); return v == null ? true : JSON.parse(v) !== false; }catch(e){ return true; } };
  const vib = navigator.vibrate ? navigator.vibrate.bind(navigator) : null;
  if(vib) navigator.vibrate = p => on() ? vib(p) : false;
  // fires on real taps only (a click), so swiping up and down never buzzes
  document.addEventListener('click', e => {
    if(!vib || !on()) return;
    if(e.target.closest('.tabbar button,.tabbar a,.tb,#v-genres .gtile,.seg button,.lseg button,.pill,.srow:not(.static),.lstats button,.s5,#hback,#hcar')) vib(6);
  }, { passive:true, capture:true });
})();
// Bottom tab bar follows the finger: it slides down as the page scrolls down, back up as it scrolls up
(function(){
  let bar = null, off = 0, ly = scrollY, settleT = 0;
  const H = () => (bar ? bar.offsetHeight : 80) + 24;
  const paint = anim => {
    if(!bar) return;
    bar.style.transition = anim ? 'transform .45s cubic-bezier(.22,1,.36,1),opacity .45s ease' : 'none';
    bar.style.transform = off ? `translateY(${off}px)` : '';
    bar.style.opacity = off ? String(Math.max(0, 1 - off / H())) : '';
    bar.style.pointerEvents = off > H() * 0.6 ? 'none' : '';
  };
  addEventListener('scroll', () => {
    bar = bar && bar.isConnected ? bar : document.querySelector('.tabbar');
    const y = Math.max(0, scrollY), d = y - ly; ly = y;
    if(document.body.classList.contains('locked')) return;
    off = y < 4 ? 0 : Math.max(0, Math.min(H(), off + d));
    paint(false);
    clearTimeout(settleT);   // when the finger stops, finish the move it was making
    settleT = setTimeout(() => { if(off && off < H()){ off = off > H() / 2 ? H() : 0; paint(true); } }, 160);
  }, { passive:true });
  // a new page or a tap on the bar brings it back
  document.addEventListener('click', e => { if(e.target.closest('.tabbar') || e.target.closest('.pc,.gtile')){ setTimeout(() => { off = 0; ly = scrollY; paint(true); }, 0); } }, true);
  addEventListener('popstate', () => { off = 0; ly = scrollY; paint(true); });
})();
// Genre and list pages: placeholder tiles right away, then each poster fades in when it has loaded
(function(){
  document.addEventListener('load', e => { const im = e.target; if(im.tagName === 'IMG') im.classList.add('lin'); }, true);
  document.addEventListener('error', e => { const im = e.target; if(im.tagName === 'IMG') im.classList.add('lin'); }, true);
  const skel = '<div class="lskel" aria-hidden="true"></div>'.repeat(6);
  function fill(){
    ['v-genre', 'v-list'].forEach(id => {
      const v = document.getElementById(id); if(!v || v.hidden) return;
      const g = v.querySelector('.grid'), m = v.querySelector('.msg'); if(!g) return;
      // images that were already in the cache finish before the listener sees them
      g.querySelectorAll('img:not(.lin)').forEach(im => { if(im.complete && im.naturalWidth) im.classList.add('lin'); });
      const loading = m && /Loading/.test(m.textContent);
      if(loading && !g.children.length){ g.innerHTML = skel; if(m) m.style.visibility = 'hidden'; }
      else if(g.querySelector('.pc') && g.querySelector('.lskel')) g.querySelectorAll('.lskel').forEach(x => x.remove());
      else if(!loading && m) m.style.visibility = '';
    });
  }
  new MutationObserver(fill).observe(document.body, { subtree:true, childList:true, characterData:true });
})();
// ================= Search tab (replaces Genres) =================
// One search bar. Genre words ("comedy", "action comedy"), decades ("90s horror"), years, "movies"/"shows" and
// "best"/"new" turn into a browse of that genre; anything else is a normal title search.
(function(){
  if(typeof buildGenreBrowse !== 'function') return;
  // genre words -> [movie genre ids, series genre ids]; null means TMDB has no such series genre
  const G = {
    action:[[28],[10759]], adventure:[[12],[10759]], animation:[[16],[16]], animated:[[16],[16]], cartoon:[[16],[16]], anime:[[16],[16]],
    comedy:[[35],[35]], comedies:[[35],[35]], funny:[[35],[35]], crime:[[80],[80]], documentary:[[99],[99]], documentaries:[[99],[99]], docs:[[99],[99]],
    drama:[[18],[18]], family:[[10751],[10751]], kids:[[10751],[10762]], fantasy:[[14],[10765]], history:[[36],null], historical:[[36],null],
    horror:[[27],null], scary:[[27],null], music:[[10402],null], musical:[[10402],null], musicals:[[10402],null], mystery:[[9648],[9648]], mysteries:[[9648],[9648]],
    romance:[[10749],null], romantic:[[10749],null], romcom:[[10749, 35],null], 'rom-com':[[10749, 35],null],
    'sci-fi':[[878],[10765]], scifi:[[878],[10765]], 'sci fi':[[878],[10765]], 'science fiction':[[878],[10765]],
    thriller:[[53],null], thrillers:[[53],null], suspense:[[53],null], war:[[10752],[10768]], western:[[37],[37]], westerns:[[37],[37]],
    reality:[null,[10764]], soap:[null,[10766]], 'talk show':[null,[10767]], news:[null,[10763]]
  };
  const NAME = { 28:'Action', 12:'Adventure', 16:'Animation', 35:'Comedy', 80:'Crime', 99:'Documentary', 18:'Drama', 10751:'Family', 14:'Fantasy', 36:'History',
    27:'Horror', 10402:'Music', 9648:'Mystery', 10749:'Romance', 878:'Sci-Fi', 53:'Thriller', 10752:'War', 37:'Western', 10759:'Action', 10762:'Kids',
    10765:'Sci-Fi & Fantasy', 10768:'War', 10764:'Reality', 10766:'Soap', 10767:'Talk show', 10763:'News' };
  const KIND = { movie:'movie', movies:'movie', film:'movie', films:'movie', show:'tv', shows:'tv', series:'tv', tv:'tv' };
  const SORT = { best:'rated', top:'rated', 'top rated':'rated', 'highest rated':'rated', new:'newest', newest:'newest', latest:'newest', recent:'newest' };
  const FILL = new Set(['and', '&', '+', 'with']);
  function parse(text){
    let w = text.toLowerCase().replace(/[’']/g, '').replace(/[^a-z0-9&+\- ]/g, ' ').trim().split(/\s+/).filter(Boolean);
    const out = { m:[], t:[], tvOk:true, mvOk:true, names:[], kind:null, sort:'popular', decade:'', year:0, left:0 };
    for(let i = 0; i < w.length; i++){
      const two = w[i] + ' ' + (w[i + 1] || ''), one = w[i], sing = one.replace(/ies$/, 'y').replace(/s$/, '');
      let hit = null, used = 1;
      if(w[i + 1] && (G[two] || SORT[two] || KIND[two])){ hit = two; used = 2; } else if(G[one] || SORT[one] || KIND[one]) hit = one; else if(G[sing]) hit = sing;
      if(hit && G[hit]){ const [m, t] = G[hit];
        if(m) out.m.push(...m); else out.mvOk = false;
        if(t) out.t.push(...t); else out.tvOk = false;
        out.names.push(NAME[(m || t)[0]] + (m && m.length > 1 ? ' ' + NAME[m[1]] : ''));
      } else if(hit && KIND[hit]) out.kind = KIND[hit];
      else if(hit && SORT[hit]) out.sort = SORT[hit];
      else if(/^(19|20)\d0s$/.test(one)) out.decade = one;
      else if(/^\d0s$/.test(one)){ const d = +one.slice(0, 2); out.decade = (d >= 30 ? '19' : '20') + one; }
      else if(/^(19[2-9]\d|20[0-3]\d)$/.test(one)) out.year = +one;
      else if(FILL.has(one)) {}
      else out.left++;
      i += used - 1;
    }
    out.browse = !out.left && (out.names.length || out.decade || out.year) ? true : false;
    return out;
  }
  function defFor(p, tv){
    const ids = [...new Set(tv ? p.t : p.m)].join(',');
    const d = genreDef(tv, ids, p.sort, p.decade);
    if(p.year){ if(tv) d.params.first_air_date_year = p.year; else d.params.primary_release_year = p.year; }
    return d;
  }
  // a browse across both movies and series: one page of each, interleaved
  const _pageOf = pageOf;
  pageOf = async function(def, page){
    if(!def || !def.mix) return _pageOf(def, page);
    const parts = await Promise.all(def.mix.map(d => _pageOf(d, page).catch(() => ({ page, total:0, results:[] }))));
    const out = [], a = parts[0].results, b = parts[1].results;
    for(let i = 0; i < Math.max(a.length, b.length); i++){ if(a[i]) out.push(a[i]); if(b[i]) out.push(b[i]); }
    return { page, total:Math.max(...parts.map(x => x.total || 0)), results:out };
  };
  const esc2 = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  const gf = { sort:'popular', decade:'', genre:'' };
  buildGenreBrowse = function(){
    const v = $('#v-genres'); built.genres = true; window.__gsOn = false;
    v.innerHTML = `<div class="pad ltop gtop lsrch"><h1 class="lh1">Search</h1>
      <div class="ltools"><label class="lsearch"><svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 20 20"/></svg><input type="search" id="gq" autocomplete="off" enterkeyhint="search" placeholder="Titles, genres, decades"></label>
      <button class="lclr" type="button" hidden>Clear</button></div>
      <div class="lseg" role="group" aria-label="Show">${[['all', 'All'], ['movie', 'Movies'], ['tv', 'Series']].map(([k, l]) => `<button type="button" data-k="${k}" class="${k === gb.kind ? 'on' : ''}">${l}</button>`).join('')}<button type="button" class="lfbtn" aria-haspopup="true"><span>Filter</span><svg viewBox="0 0 24 24"><path d="M7 10l5 5 5-5"/></svg></button></div>
      <div class="lfmenu" hidden></div></div>
      <div class="pad">
      <div class="lsum" hidden></div><div class="grid browse" id="gres1"></div><div class="sent" id="gsent1" style="height:1px"></div><div class="msg" id="gmsg1"></div>
      <div class="lsum" id="gsum2" hidden></div><div class="grid browse" id="gres2"></div><div class="sent" id="gsent2" style="height:30px"></div><div class="msg" id="gmsg2"></div></div>`;
    const q = $('#gq', v), clr = $('.lclr', v), sum = $('.lsum', v), sum2 = $('#gsum2', v);
    const g1 = $('#gres1', v), g2 = $('#gres2', v), m1 = $('#gmsg1', v), m2 = $('#gmsg2', v), s1 = $('#gsent1', v), s2 = $('#gsent2', v);
    const stop = g => { g._gen = (g._gen || 0) + 1; g._obs?.disconnect(); g.innerHTML = ''; };
    const load = () => {
      const t = gb.q.trim(); clr.hidden = !t; sum.hidden = sum2.hidden = true; m1.textContent = m2.textContent = '';
      stop(g1); stop(g2);
      const fOn = gf.sort !== 'popular' || gf.decade || gf.genre;
      if(!t && !gf.genre) return;            // an empty search shows nothing, unless a genre is picked in Filter
      const p = parse(t || gf.genre), kind = p.kind || gb.kind;
      if(t && gf.genre && p.browse && !p.names.length){ const x = parse(gf.genre); Object.assign(p, { m:x.m, t:x.t, mvOk:x.mvOk, tvOk:x.tvOk, names:x.names }); }
      else if(t && gf.genre && p.browse){ const x = parse(gf.genre); p.m.push(...x.m); p.t.push(...x.t); p.mvOk = p.mvOk && x.mvOk; p.tvOk = p.tvOk && x.tvOk; if(!p.names.includes(x.names[0])) p.names.push(x.names[0]); }
      if(p.sort === 'popular') p.sort = gf.sort;
      if(!p.decade && !p.year) p.decade = gf.decade;
      const search = { path:kind === 'all' ? '/search/multi' : '/search/' + kind, params:{ query:t }, tv:kind === 'tv' };
      let browse = null;
      if(p.browse){
        const mv = p.mvOk && (p.m.length || !p.names.length), tv = p.tvOk && (p.t.length || !p.names.length);
        const useM = mv && kind !== 'tv', useT = tv && kind !== 'movie';
        if(useM && useT) browse = { mix:[defFor(p, false), defFor(p, true)] };
        else if(useM) browse = defFor(p, false);
        else if(useT) browse = defFor(p, true);
        if(browse){
          const what = useM && useT ? 'Movies and series' : useM ? 'Movies' : 'Series';
          const bits = [p.names.join(' + '), p.year || (p.decade ? p.decade.replace(/^\d\d(\d0s)$/, "'$1") : ''), { rated:'Top rated', newest:'Newest', popular:'' }[p.sort]].filter(Boolean);
          sum.innerHTML = `<b>${esc2(bits.join(' · '))}</b><span>${what}</span>`; sum.hidden = false;
        }
      }
      if(browse){
        setPageSource({ title:'Search', def:browse });
        feed(null, g1, s1, m1, { ...browse, max:p.names.length === 1 && !p.decade && !p.year && p.sort === 'popular' ? 60 : 0 });
        // a single genre word can also be a title (a film called "Comedy"): those come under the genre results
        if(t && !fOn && p.names.length === 1 && !p.decade && !p.year && p.sort === 'popular' && !p.kind){
          sum2.innerHTML = `<b>Titles matching “${esc2(t)}”</b>`; sum2.hidden = false;
          feed(null, g2, s2, m2, { ...search, max:40 });
        }
      } else {
        setPageSource({ title:'Search', def:search });
        feed(null, g1, s1, m1, search);
      }
    };
    let tm; q.value = gb.q;
    q.oninput = () => { clearTimeout(tm); clr.hidden = !q.value; tm = setTimeout(() => { gb.q = q.value; load(); }, 280); };
    q.onkeydown = e => { if(e.key === 'Enter'){ clearTimeout(tm); gb.q = q.value; q.blur(); load(); } };
    clr.onclick = () => { clearTimeout(tm); q.value = ''; gb.q = ''; load(); q.focus(); };
    // Filter dropdown: sort, decade and genre. Words typed in the search still win over it.
    const fb = $('.lfbtn', v), menu = $('.lfmenu', v);
    const GEN = [['', 'Any'], ...Object.keys(G).filter(k => ['action','adventure','animation','comedy','crime','documentary','drama','family','fantasy','history','horror','music','mystery','romance','sci-fi','thriller','war','western','reality'].includes(k)).map(k => [k, k === 'sci-fi' ? 'Sci-Fi' : k[0].toUpperCase() + k.slice(1)])];
    const SECT = [['sort', 'Sort by', [['popular', 'Popular'], ['rated', 'Top rated'], ['newest', 'Newest']]],
      ['decade', 'Decade', [['', 'Any'], ['2020s', '2020s'], ['2010s', '2010s'], ['2000s', '2000s'], ['1990s', '1990s'], ['1980s', '1980s'], ['1970s', '1970s'], ['1960s', '1960s']]],
      ['genre', 'Genre', GEN]];
    const fLabel = () => { const n = (gf.sort !== 'popular') + !!gf.decade + !!gf.genre;
      fb.querySelector('span').textContent = n ? `Filter · ${n}` : 'Filter'; fb.classList.toggle('on', !!n); };
    const drawMenu = () => { menu.innerHTML = SECT.map(([k, h, opts]) => `<div class="lfh">${h}</div><div class="lfopts">${opts.map(([val, l]) =>
      `<button type="button" data-f="${k}" data-val="${val}" class="${gf[k] === val ? 'on' : ''}">${l}</button>`).join('')}</div>`).join('')
      + `<div class="lfend"><button type="button" class="lfreset">Reset</button><button type="button" class="lfdone">Done</button></div>`; };
    const closeMenu = () => { menu.hidden = true; fb.setAttribute('aria-expanded', 'false'); };
    fb.onclick = e => { e.stopPropagation(); if(!menu.hidden) return closeMenu(); drawMenu(); menu.hidden = false; fb.setAttribute('aria-expanded', 'true'); };
    menu.onclick = e => { e.stopPropagation(); const b = e.target.closest('button'); if(!b) return;
      if(b.classList.contains('lfdone')) return closeMenu();
      if(b.classList.contains('lfreset')){ gf.sort = 'popular'; gf.decade = ''; gf.genre = ''; }
      else gf[b.dataset.f] = b.dataset.val;
      drawMenu(); fLabel(); scrollTo(0, 0); load(); };
    document.addEventListener('click', e => { if(!menu.hidden && !e.target.closest('.lfmenu,.lfbtn')) closeMenu(); });
    fLabel();
    $('.lseg', v).onclick = e => { const b = e.target.closest('button'); if(!b || !b.dataset.k || b.dataset.k === gb.kind) return; gb.kind = b.dataset.k;
      $$('.lseg button[data-k]', v).forEach(x => x.classList.toggle('on', x === b)); scrollTo(0, 0); load(); };
    load();
  };
  // the tab itself: "Search" with a magnifier
  const tab = document.querySelector('.tb[data-v="genres"]');
  if(tab) tab.innerHTML = '<svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 20 20"/></svg>Search';
  if(document.querySelector('#v-genres .gtop')) buildGenreBrowse();   // already built at start-up (Genres as start page)
})();
