// Final Panel shared server (Cloudflare Worker).
// Keeps the Comic Vine key private and saves every answer, so a list fetched for one person is handed to everyone else
// without using the hourly Comic Vine allowance again.
// Setup: add a secret named CV_KEY (your Comic Vine API key) in the Worker's Settings → Variables and Secrets.

const ORIGINS = ['https://jwillhub.github.io'];   // only the Long Box site may use this server from a browser
const ALLOW = /^(issues|volumes|characters|people|story_arcs|search|issue\/4000-\d+|volume\/4050-\d+|story_arc\/4045-\d+|character\/4005-\d+|person\/4040-\d+)\/$/;
// how long a saved answer is reused: single items a week, searches 12 hours, lists (like New this week) 6 hours
const TTL = p => /^(issue|volume|story_arc|character|person)\//.test(p) ? 604800 : p.startsWith('search') ? 43200 : 21600;

// Rolling Credits library relay: browsers can't call the Real-Debrid API directly, so /rd/... is passed through to it.
// The person's own token rides along in the Authorization header and is never stored or logged here.
const RD_ALLOW = /^(torrents|torrents\/info\/[A-Za-z0-9]+|unrestrict\/link|streaming\/transcode\/[A-Za-z0-9]+)$/;
async function rdRelay(req, url, cors) {
  const json = (body, status) => new Response(body, { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...cors } });
  if (req.method === 'OPTIONS') return new Response(null, { headers: { ...cors, 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'Authorization, Content-Type', 'Access-Control-Max-Age': '86400' } });
  const path = url.pathname.replace(/^\/rd\//, '');
  if (!RD_ALLOW.test(path) || !['GET', 'POST'].includes(req.method)) return json('{"error":"Not allowed"}', 404);
  const auth = req.headers.get('Authorization');
  if (!auth) return json('{"error":"No token","error_code":8}', 401);
  const headers = { Authorization: auth };
  if (req.method === 'POST') headers['Content-Type'] = 'application/x-www-form-urlencoded';
  let up;
  try { up = await fetch('https://api.real-debrid.com/rest/1.0/' + path + url.search, { method: req.method, headers, body: req.method === 'POST' ? await req.text() : undefined }); }
  catch (e) { return json('{"error":"Real-Debrid unreachable"}', 502); }
  return new Response(up.status === 204 ? null : up.body, { status: up.status, headers: { 'Content-Type': up.headers.get('Content-Type') || 'application/json', 'Cache-Control': 'no-store', ...cors } });
}

export default {
  async fetch(req, env, ctx) {
    const origin = req.headers.get('Origin') || '';
    const cors = { 'Access-Control-Allow-Origin': ORIGINS.includes(origin) ? origin : ORIGINS[0], 'Vary': 'Origin' };
    const u0 = new URL(req.url);
    if (u0.pathname.startsWith('/rd/')) {
      if (origin && !ORIGINS.includes(origin)) return new Response('{"error":"Not allowed"}', { status: 403, headers: { 'Content-Type': 'application/json', ...cors } });
      return rdRelay(req, u0, cors);
    }
    const reply = (body, status, extra = {}) => new Response(body, { status, headers: { 'Content-Type': 'application/json', ...cors, ...extra } });
    if (req.method === 'OPTIONS') return new Response(null, { headers: { ...cors, 'Access-Control-Allow-Methods': 'GET', 'Access-Control-Max-Age': '86400' } });
    if (req.method !== 'GET') return reply('{"status_code":0,"error":"GET only"}', 405);
    if (origin && !ORIGINS.includes(origin)) return reply('{"status_code":0,"error":"Not allowed"}', 403);
    if (!env.CV_KEY) return reply('{"status_code":100,"error":"No Comic Vine key on the server"}', 200);

    const url = new URL(req.url);
    const path = url.pathname.replace(/^\/+/, '');
    if (!ALLOW.test(path)) return reply('{"status_code":101,"error":"Object Not Found"}', 404);
    const q = new URLSearchParams(url.search);
    ['api_key', 'format', 'json_callback'].forEach(k => q.delete(k));
    q.sort();

    const cacheKey = new Request('https://long-box-cache/' + path + '?' + q.toString());
    const cache = caches.default;
    const hit = await cache.match(cacheKey);
    if (hit) return reply(hit.body, 200, { 'X-Cache': 'HIT' });

    q.set('api_key', env.CV_KEY); q.set('format', 'json');
    let up;
    try {
      up = await fetch('https://comicvine.gamespot.com/api/' + path + '?' + q.toString(),
        { headers: { 'User-Agent': 'LongBox/1.0 (shared server for a personal comics app)', 'Accept': 'application/json' } });
    } catch (e) { return reply('{"status_code":0,"error":"Comic Vine unreachable"}', 502); }
    const text = await up.text();
    let ok = false;
    try { ok = up.ok && JSON.parse(text).status_code === 1; } catch (e) {}
    if (!ok) return reply(text.trim().startsWith('{') ? text : '{"status_code":0,"error":"Comic Vine error ' + up.status + '"}', 200);
    ctx.waitUntil(cache.put(cacheKey, new Response(text, { headers: { 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=' + TTL(path) } })));
    return reply(text, 200, { 'X-Cache': 'MISS' });
  }
};
