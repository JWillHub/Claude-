sub init()
    m.top.functionName = "go"
end sub

sub go()
    r = m.top.request
    res = { ok: false, error: "" }
    if r.kind = "sync"
        res = doSync(r)
    else if r.kind = "files"
        res = doFiles(r)
    else if r.kind = "stream"
        res = doStream(r)
    else if r.kind = "transcode"
        res = doTranscode(r)
    end if
    res.kind = r.kind
    res.ctx = r.ctx
    m.top.result = res
end sub

' ---------- HTTP ----------
function newXfer(url as String) as Object
    ut = CreateObject("roUrlTransfer")
    ut.SetCertificatesFile("common:/certs/ca-bundle.crt")
    ut.InitClientCertificates()
    ut.RetainBodyOnError(true)
    ut.SetUrl(url)
    return ut
end function

function http(url as String, method = "GET" as String, body = "" as String, bearer = "" as String) as Object
    ut = newXfer(url)
    port = CreateObject("roMessagePort")
    ut.SetMessagePort(port)
    if bearer <> "" then ut.AddHeader("Authorization", "Bearer " + bearer)
    if method = "POST"
        ut.AddHeader("Content-Type", "application/x-www-form-urlencoded")
        ut.AsyncPostFromString(body)
    else
        ut.AsyncGetToString()
    end if
    msg = wait(30000, port)
    if type(msg) = "roUrlEvent" then return { code: msg.GetResponseCode(), body: msg.GetString() }
    ut.AsyncCancel()
    return { code: -1, body: "" }
end function

function rdUrl(path as String, key as String) as String
    sep = "?"
    if Instr(1, path, "?") > 0 then sep = "&"
    return "https://api.real-debrid.com/rest/1.0" + path + sep + "auth_token=" + key
end function

function rdErr(code as Integer) as String
    if code = 401 then return "Real-Debrid rejected the token. Send your keys again from Lumen on your phone."
    if code = 403 then return "Real-Debrid refused the request. Check your subscription."
    if code < 0 then return "Couldn't reach Real-Debrid. Check the TV's internet connection."
    return "Real-Debrid error " + code.ToStr()
end function

' ---------- library ----------
function doSync(r as Object) as Object
    all = []
    for page = 1 to 20
        h = http(rdUrl("/torrents?limit=500&page=" + page.ToStr(), r.rd))
        if h.code = 204 then exit for
        if h.code <> 200 then return { ok: false, error: rdErr(h.code) }
        l = ParseJson(h.body)
        if l = invalid then exit for
        all.Append(l)
        if l.Count() < 500 then exit for
    end for

    ' earlier matches are kept on the TV, so only new downloads are looked up on TMDB
    cache = invalid
    txt = ReadAsciiFile("cachefs:/lumen_match.json")
    if txt <> "" then cache = ParseJson(txt)
    if cache = invalid then cache = {}

    items = []
    todo = []
    for each t in all
        if t.status = "downloaded"
            it = { tid: t.id, name: t.filename, added: t.added, m: invalid }
            c = cache.Lookup(t.id)
            if c <> invalid and c.name = t.filename
                it.m = c.m
            else
                todo.Push(it)
            end if
            items.Push(it)
        end if
    end for

    if r.tmdb <> "" and todo.Count() > 0 then matchAll(todo, r.tmdb)

    fresh = {}
    for each it in items
        if it.m <> invalid then fresh[it.tid] = { name: it.name, m: it.m }
    end for
    WriteAsciiFile("cachefs:/lumen_match.json", FormatJson(fresh))
    return { ok: true, items: items }
end function

' Look titles up on TMDB, eight at a time
sub matchAll(todo as Object, key as String)
    port = CreateObject("roMessagePort")
    bearer = ""
    if Len(key) > 40 then bearer = key
    i = 0
    live = {}
    while i < todo.Count() or live.Count() > 0
        while i < todo.Count() and live.Count() < 8
            it = todo[i]
            q = parseName(it.name)
            it.q = q
            path = "movie"
            yk = "year"
            if q.tv
                path = "tv"
                yk = "first_air_date_year"
            end if
            ut = newXfer("https://api.themoviedb.org/3/search/" + path)
            url = "https://api.themoviedb.org/3/search/" + path + "?include_adult=false&query=" + ut.Escape(q.title)
            if q.year <> "" then url = url + "&" + yk + "=" + q.year
            if bearer = "" then url = url + "&api_key=" + key
            ut.SetUrl(url)
            if bearer <> "" then ut.AddHeader("Authorization", "Bearer " + bearer)
            ut.SetMessagePort(port)
            if ut.AsyncGetToString()
                live[ut.GetIdentity().ToStr()] = { ut: ut, it: it }
            end if
            i = i + 1
        end while
        msg = wait(20000, port)
        if type(msg) = "roUrlEvent"
            id = msg.GetSourceIdentity().ToStr()
            job = live.Lookup(id)
            if job <> invalid
                live.Delete(id)
                if msg.GetResponseCode() = 200
                    j = ParseJson(msg.GetString())
                    if j <> invalid and j.results <> invalid and j.results.Count() > 0
                        x = j.results[0]
                        it = job.it
                        if it.q.tv
                            it.m = { id: "tv" + x.id.ToStr(), tv: true, title: strOr(x.name, it.q.title), year: Left(strOr(x.first_air_date, ""), 4), poster: strOr(x.poster_path, "") }
                        else
                            it.m = { id: x.id.ToStr(), tv: false, title: strOr(x.title, it.q.title), year: Left(strOr(x.release_date, ""), 4), poster: strOr(x.poster_path, "") }
                        end if
                    end if
                end if
            end if
        else if msg = invalid
            for each k in live
                live[k].ut.AsyncCancel()
            end for
            live = {}
        end if
    end while
end sub

function strOr(v as Dynamic, d as String) as String
    if v = invalid then return d
    if type(v) = "roString" or type(v) = "String" then return v
    return d
end function

' "The.Movie.Name.2019.2160p.UHD.BluRay.x265" -> title "The Movie Name", year "2019"
function parseName(n as String) as Object
    t = CreateObject("roRegex", "\.(mkv|mp4|m4v|avi|mov|webm|ts|wmv)$", "i").Replace(n, "")
    t = CreateObject("roRegex", "^\s*(\[[^\]]*\]|www\.\S+)\s*[-]?\s*", "i").Replace(t, "")
    t = CreateObject("roRegex", "[._]+", "").ReplaceAll(t, " ")
    t = CreateObject("roRegex", "\s+", "").ReplaceAll(t, " ").Trim()
    tv = CreateObject("roRegex", "\bS\d{1,2}(E\d{1,3})?\b|\bSeason\s*\d+|\b\d{1,2}x\d{2}\b|\bComplete Series\b", "i").IsMatch(t)
    cut = CreateObject("roRegex", "\b(S\d{1,2}(E\d{1,3})?|Season\s*\d+|\d{1,2}x\d{2}|2160p|1080p|720p|480p|4k|uhd|blu ?ray|brrip|bdrip|web ?dl|webrip|web|hdtv|dvdrip|remux|x26[45]|h ?26[45]|hevc|10bit|complete|proper|repack|extended|unrated|imax|hdr|multi)\b", "i")
    head = t
    parts = cut.Split(t)
    if parts.Count() > 0 and parts[0].Trim() <> "" then head = parts[0]
    ' the year is the last 19xx/20xx word that isn't the first word
    words = head.Trim().Split(" ")
    year = ""
    at = -1
    yr = CreateObject("roRegex", "^[\(\[]?((19|20)\d\d)[\)\]]?$", "")
    for w = 1 to words.Count() - 1
        mm = yr.Match(words[w])
        if mm.Count() > 1
            year = mm[1]
            at = w
        end if
    end for
    if at > 0
        head = ""
        for w = 0 to at - 1
            head = head + words[w] + " "
        end for
    end if
    head = CreateObject("roRegex", "[\[\(][^\]\)]*[\]\)]", "").ReplaceAll(head, " ")
    head = CreateObject("roRegex", "[\s\-:\(\[]+$", "").Replace(head, "")
    head = CreateObject("roRegex", "\s+", "").ReplaceAll(head, " ").Trim()
    if head = "" then head = t
    return { title: head, year: year, tv: tv }
end function

' ---------- files in a title's torrents ----------
function doFiles(r as Object) as Object
    out = []
    vid = CreateObject("roRegex", "\.(mkv|mp4|m4v|avi|mov|webm|ts|wmv)$", "i")
    smp = CreateObject("roRegex", "(^|[\W_])sample([\W_]|$)", "i")
    for each tid in r.tids
        h = http(rdUrl("/torrents/info/" + tid, r.rd))
        if h.code <> 200 then return { ok: false, error: rdErr(h.code) }
        info = ParseJson(h.body)
        if info <> invalid
            sel = []
            if info.files <> invalid
                for each f in info.files
                    if f.selected = 1 then sel.Push(f)
                end for
            end if
            links = info.links
            if links = invalid then links = []
            if sel.Count() = links.Count()
                for k = 0 to sel.Count() - 1
                    if vid.IsMatch(sel[k].path) and not smp.IsMatch(sel[k].path) then out.Push({ path: sel[k].path, bytes: sel[k].bytes, link: links[k], tid: tid })
                end for
            else if links.Count() = 1
                out.Push({ path: info.filename, bytes: info.bytes, link: links[0], tid: tid })
            end if
        end if
    end for
    return { ok: true, files: out }
end function

function doStream(r as Object) as Object
    h = http(rdUrl("/unrestrict/link", r.rd), "POST", "link=" + CreateObject("roUrlTransfer").Escape(r.link))
    if h.code <> 200 then return { ok: false, error: rdErr(h.code) }
    u = ParseJson(h.body)
    if u = invalid or u.download = invalid then return { ok: false, error: "Real-Debrid didn't return a link for this file." }
    sid = ""
    if u.id <> invalid then sid = u.id
    st = 0
    if u.streamable <> invalid then st = u.streamable
    return { ok: true, url: u.download, name: strOr(u.filename, ""), id: sid, streamable: st }
end function

' Real-Debrid's converted stream (HLS), for files the TV can't play itself
function doTranscode(r as Object) as Object
    h = http(rdUrl("/streaming/transcode/" + r.id, r.rd))
    if h.code <> 200 then return { ok: false, error: rdErr(h.code) }
    j = ParseJson(h.body)
    if j = invalid or j.apple = invalid then return { ok: false, error: "No converted stream available." }
    u = j.apple
    if type(u) = "roAssociativeArray"
        pick = u.Lookup("full")
        if pick = invalid
            for each k in u
                if pick = invalid then pick = u[k]
            end for
        end if
        u = pick
    end if
    return { ok: true, url: u }
end function
