// Real-Debrid relay for Rolling Credits (Cloudflare Worker).
// Browsers can't call the Real-Debrid API directly, so the app sends /rd/... here and this passes it through.
// Only the four calls the library uses are allowed, only for jwillhub.github.io, and the person's own token
// rides along in the Authorization header. Nothing is stored or logged.

const ORIGINS = ['https://jwillhub.github.io'];
const RD_ALLOW = /^(torrents|torrents\/info\/[A-Za-z0-9]+|unrestrict\/link|streaming\/transcode\/[A-Za-z0-9]+)$/;

export default {
  async fetch(req) {
    const origin = req.headers.get('Origin') || '';
    const cors = { 'Access-Control-Allow-Origin': ORIGINS.includes(origin) ? origin : ORIGINS[0], 'Vary': 'Origin' };
    const json = (body, status) => new Response(body, { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...cors } });
    if (origin && !ORIGINS.includes(origin)) return json('{"error":"Not allowed"}', 403);
    if (req.method === 'OPTIONS') return new Response(null, { headers: { ...cors, 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'Authorization, Content-Type', 'Access-Control-Max-Age': '86400' } });

    const url = new URL(req.url);
    const path = url.pathname.replace(/^\/rd\//, '').replace(/^\/+/, '');
    if (!RD_ALLOW.test(path) || !['GET', 'POST'].includes(req.method)) return json('{"error":"Not allowed"}', 404);
    const auth = req.headers.get('Authorization');
    if (!auth) return json('{"error":"No token","error_code":8}', 401);

    const headers = { Authorization: auth };
    if (req.method === 'POST') headers['Content-Type'] = 'application/x-www-form-urlencoded';
    let up;
    try {
      up = await fetch('https://api.real-debrid.com/rest/1.0/' + path + url.search,
        { method: req.method, headers, body: req.method === 'POST' ? await req.text() : undefined });
    } catch (e) { return json('{"error":"Real-Debrid unreachable"}', 502); }
    return new Response(up.status === 204 ? null : up.body,
      { status: up.status, headers: { 'Content-Type': up.headers.get('Content-Type') || 'application/json', 'Cache-Control': 'no-store', ...cors } });
  }
};
