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
