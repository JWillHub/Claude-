// Offline support. Online: always get the latest (and refresh the cache). Offline: use the cached copy.
const PREFIX='cg-chat-';
const CACHE=PREFIX+'v1:'+self.registration.scope;
const ASSETS=['./','./index.html','./manifest.json','./icon-192.png','./icon-512.png','./apple-touch-icon.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS.map(u=>new Request(u,{cache:'reload'})))).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith(PREFIX)&&k.endsWith(':'+self.registration.scope)&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',e=>{const req=e.request;if(req.method!=='GET')return;const same=new URL(req.url).origin===location.origin;
  e.respondWith((async()=>{const c=await caches.open(CACHE);try{const r=await fetch(req,same?{cache:'no-cache'}:undefined);if(r&&(r.ok||r.type==='opaque'))c.put(req,r.clone());return r;}
  catch(_){return (await c.match(req,{ignoreSearch:true}))||(req.mode==='navigate'?await c.match('./index.html'):null)||new Response('Offline',{status:503});}})());});
