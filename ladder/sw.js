/* HLL Ladder service worker: offline app shell + runtime font caching.
   App files are fetched network-first (revalidated, so GitHub Pages' 10-minute HTTP cache cannot hand back a stale copy)
   and fall back to the saved copy when offline or when the network takes longer than 4 s. Fonts are cache-first.
   Only this folder is handled, and only caches named hll-ladder-* are cleaned up, so other apps on this site keep theirs.
   Bump VERSION to force a clean cache. */
const PREFIX = "hll-ladder-";
const VERSION = PREFIX + "v1";
const SHELL = ["./", "index.html", "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png"];
const FONT_HOSTS = ["fonts.googleapis.com", "fonts.gstatic.com"];
const WAIT_MS = 4000;

self.addEventListener("install", (e) => {
  // cache:"reload" skips the HTTP cache, so a new version always installs the newest files
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL.map((u) => new Request(u, { cache: "reload" })))).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith(PREFIX) && k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  const sameOrigin = url.origin === self.location.origin;
  if (!sameOrigin && !FONT_HOSTS.includes(url.hostname)) return;
  const here = new URL("./", self.location).pathname;   // this app's folder; files of other apps (or nested folders) are not ours
  if (sameOrigin && (!url.pathname.startsWith(here) || url.pathname.slice(here.length).includes("/"))) return;

  e.respondWith((async () => {
    const cache = await caches.open(VERSION);
    const nav = req.mode === "navigate";
    const key = nav ? "index.html" : req;
    const cached = await cache.match(key, { ignoreSearch: true });

    if (!sameOrigin) {                       // fonts: saved copy first, refreshed in the background
      const refresh = fetch(req).then((res) => {
        if (res && (res.ok || res.type === "opaque")) cache.put(key, res.clone());
        return res;
      }).catch(() => null);
      if (cached) { e.waitUntil(refresh); return cached; }
      return (await refresh) || new Response("", { status: 504, statusText: "Offline" });
    }

    // app files: ask the network first
    const net = fetch(nav ? "index.html" : req, { cache: "no-cache" }).then((res) => {
      if (res && res.ok) { cache.put(key, res.clone()); return res; }
      return null;
    }).catch(() => null);
    const slow = new Promise((r) => setTimeout(() => r(null), WAIT_MS));
    const res = await Promise.race([net, cached ? slow : net]);
    if (res) return res;
    if (cached) { e.waitUntil(net); return cached; }
    const again = await net;
    return again || new Response("", { status: 504, statusText: "Offline" });
  })());
});
