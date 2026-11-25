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
