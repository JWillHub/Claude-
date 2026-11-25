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
    v.querySelectorAll('.grid .pc:not([data-lc])').forEach(pc => { pc.dataset.lc = 1;
      const img = pc.querySelector('img'); const t = img && (img.alt || img.title); if(t) pc.insertAdjacentHTML('beforeend', `<span class="lcap">${t.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}</span>`); });
  }
  let t; new MutationObserver(() => { clearTimeout(t); t = setTimeout(() => { dressGenres(); dressGenrePage(); }, 20); })
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
