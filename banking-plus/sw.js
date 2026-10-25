// Banking+ offline support. Online: always get the latest from the site (and refresh the cache).
// Offline: use the cached copy.
const CACHE='bankingplus-v12:'+self.registration.scope;
const ASSETS=['./','./index.html','./app.webmanifest','./bp-icon-192.png','./bp-icon-512.png','./bp-apple-touch.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS.map(u=>new Request(u,{cache:'reload'})))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
  const req=e.request;
  if(req.method!=='GET'||new URL(req.url).origin!==location.origin)return;
  e.respondWith((async()=>{
    const c=await caches.open(CACHE);
    try{const r=await fetch(req,{cache:'no-cache'});if(r&&r.ok)c.put(req,r.clone());return r}
    catch(_){return (await c.match(req,{ignoreSearch:true}))||(req.mode==='navigate'?await c.match('./index.html'):null)||new Response('Offline',{status:503})}
  })());
});
