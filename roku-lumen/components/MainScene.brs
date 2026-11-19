' Lumen for Roku: library rows -> title page (copies or episodes) -> player, with resume points.

sub init()
    m.rows = m.top.findNode("rows")
    m.detail = m.top.findNode("detail")
    m.dList = m.top.findNode("dList")
    m.dPoster = m.top.findNode("dPoster")
    m.dTitle = m.top.findNode("dTitle")
    m.dSub = m.top.findNode("dSub")
    m.video = m.top.findNode("video")
    m.status = m.top.findNode("status")
    m.setup = m.top.findNode("setup")
    m.hint = m.top.findNode("hint")
    m.api = m.top.findNode("api")
    m.api.observeField("result", "onApi")
    m.rows.observeField("rowItemSelected", "onPick")
    m.dList.observeField("itemSelected", "onFile")
    m.video.observeField("state", "onVideoState")
    m.video.observeField("position", "onVideoPos")

    m.reg = CreateObject("roRegistrySection", "lumen")
    m.rd = m.reg.Read("rd")
    m.tmdb = m.reg.Read("tmdb")
    m.prog = ParseJson(regRead("prog", "{}"))
    if m.prog = invalid then m.prog = {}
    m.items = []
    m.titles = {}
    m.cur = invalid
    m.files = []
    m.playing = invalid
    m.busy = false
    m.queue = []
    m.searching = ""
    m.lastSave = 0

    if m.rd = ""
        showSetup()
    else
        ' show the last library right away, then refresh it
        cached = ReadAsciiFile("cachefs:/lumen_lib.json")
        if cached <> ""
            items = ParseJson(cached)
            if items <> invalid
                m.items = items
                buildRows()
            end if
        end if
        startSync()
    end if
end sub

function regRead(k as String, d as String) as String
    if m.reg.Exists(k) then return m.reg.Read(k)
    return d
end function

sub say(t as String)
    m.status.text = t
end sub

' ---------- keys from the phone ----------
sub onSetKeys()
    k = m.top.setKeys
    if k = invalid or k.rd = invalid or k.rd = "" then return
    m.rd = k.rd
    m.reg.Write("rd", m.rd)
    if k.tmdb <> invalid and k.tmdb <> ""
        m.tmdb = k.tmdb
        m.reg.Write("tmdb", m.tmdb)
    end if
    m.reg.Flush()
    m.setup.visible = false
    say("Keys received from your phone.")
    startSync()
end sub

sub showSetup()
    m.rows.visible = false
    m.detail.visible = false
    m.hint.visible = false
    m.setup.visible = true
    ip = ""
    addrs = CreateObject("roDeviceInfo").GetIPAddrs()
    for each k in addrs
        if ip = "" then ip = addrs[k]
    end for
    if ip = "" then ip = "(not connected)"
    m.top.findNode("ip").text = ip
    m.top.setFocus(true)
end sub

' typing the keys with the remote, if the phone route doesn't work
sub askKey(which as String)
    m.asking = which
    d = CreateObject("roSGNode", "StandardKeyboardDialog")
    if d = invalid then d = CreateObject("roSGNode", "KeyboardDialog")
    if which = "rd"
        d.title = "Real-Debrid API token"
    else
        d.title = "TMDB API key (or read access token)"
    end if
    d.buttons = ["OK", "Cancel"]
    d.observeField("buttonSelected", "onKeyDialog")
    m.dlg = d
    m.top.dialog = d
end sub

sub onKeyDialog()
    d = m.dlg
    txt = d.text.Trim()
    pressed = d.buttonSelected
    d.close = true
    if pressed <> 0 then return
    if m.asking = "rd"
        if txt = "" then return
        m.newRd = txt
        askKey("tmdb")
    else if m.asking = "tmdb"
        m.top.setKeys = { rd: m.newRd, tmdb: txt }
    else if m.asking = "search"
        doSearch(txt)
    end if
end sub

' ---------- talking to the network task (one job at a time) ----------
sub ask(req as Object)
    req.rd = m.rd
    req.tmdb = m.tmdb
    if m.busy
        m.queue.Push(req)
        return
    end if
    m.busy = true
    m.api.request = req
    m.api.control = "RUN"
end sub

sub onApi()
    r = m.api.result
    m.busy = false
    if m.queue.Count() > 0
        nxt = m.queue.Shift()
        m.busy = true
        m.api.request = nxt
        m.api.control = "RUN"
    end if
    if r = invalid then return
    if not r.ok
        say(r.error)
        if r.kind = "stream" or r.kind = "transcode" then closeVideo()
        return
    end if
    if r.kind = "sync"
        m.items = r.items
        WriteAsciiFile("cachefs:/lumen_lib.json", FormatJson(m.items))
        say("")
        buildRows()
    else if r.kind = "files"
        showFiles(r.files)
    else if r.kind = "stream"
        playUrl(r.url, r)
    else if r.kind = "transcode"
        m.playing.tried = true
        playStream(r.url, "hls")
    end if
end sub

sub startSync()
    if m.items.Count() = 0 then say("Loading your library...")
    ask({ kind: "sync" })
end sub

' ---------- library rows ----------
' one entry per title: several torrents of the same film or show become one poster
sub groupTitles()
    m.titles = {}
    for each it in m.items
        mt = it.m
        if mt = invalid
            mt = { id: "rd:" + it.tid, tv: false, title: it.name, year: "", poster: "" }
        end if
        t = m.titles.Lookup(mt.id)
        if t = invalid
            t = { id: mt.id, tv: mt.tv, title: mt.title, year: mt.year, poster: mt.poster, tors: [], added: "" }
            m.titles[mt.id] = t
        end if
        t.tors.Push(it.tid)
        a = it.added
        if a = invalid then a = ""
        if a > t.added then t.added = a
    end for
end sub

function lastWatched(t as Object) as Object
    best = invalid
    for each k in m.prog
        p = m.prog[k]
        for each tid in t.tors
            if p.tid = tid
                if best = invalid or p.at > best.at then best = p
            end if
        end for
    end for
    return best
end function

function posterUrl(p as Dynamic, size as String) as String
    if p = invalid or p = "" then return ""
    return "https://image.tmdb.org/t/p/" + size + p
end function

function tile(t as Object, prog as Dynamic) as Object
    c = CreateObject("roSGNode", "ContentNode")
    c.title = t.title
    c.HDPosterUrl = posterUrl(t.poster, "w342")
    c.id = t.id
    if prog <> invalid and prog.dur > 0
        c.addFields({ progress: prog.pos / prog.dur })
    end if
    return c
end function

sub buildRows(only = invalid as Dynamic)
    groupTitles()
    list = []
    for each k in m.titles
        list.Push(m.titles[k])
    end for
    root = CreateObject("roSGNode", "ContentNode")

    if only <> invalid
        row = root.createChild("ContentNode")
        row.title = "Results for " + Chr(34) + only + Chr(34)
        q = LCase(only)
        for each t in list
            if Instr(1, LCase(t.title), q) > 0 then row.appendChild(tile(t, lastWatched(t)))
        end for
        if row.getChildCount() = 0
            say("Nothing in your library matches " + Chr(34) + only + Chr(34) + ".")
            return
        end if
    else
        ' Continue watching: most recent first
        cw = []
        for each t in list
            p = lastWatched(t)
            if p <> invalid then cw.Push({ t: t, p: p, at: p.at })
        end for
        cw.SortBy("at", "r")
        if cw.Count() > 0
            row = root.createChild("ContentNode")
            row.title = "Continue watching"
            for each x in cw
                row.appendChild(tile(x.t, x.p))
            end for
        end if
        list.SortBy("added", "r")
        mv = root.createChild("ContentNode")
        mv.title = "Movies"
        tv = root.createChild("ContentNode")
        tv.title = "TV shows"
        for each t in list
            if t.tv
                tv.appendChild(tile(t, invalid))
            else
                mv.appendChild(tile(t, invalid))
            end if
        end for
        if tv.getChildCount() = 0 then root.removeChild(tv)
        if mv.getChildCount() = 0 then root.removeChild(mv)
        if root.getChildCount() = 0
            say("Your Real-Debrid library is empty.")
            return
        end if
    end if

    m.searching = ""
    if only <> invalid then m.searching = only
    m.rows.content = root
    m.setup.visible = false
    m.hint.visible = true
    if not m.detail.visible and not m.video.visible
        m.rows.visible = true
        m.rows.setFocus(true)
    end if
end sub

sub doSearch(q as String)
    q = q.Trim()
    if q = "" then return
    buildRows(q)
end sub

' ---------- title page ----------
sub onPick()
    sel = m.rows.rowItemSelected
    row = m.rows.content.getChild(sel[0])
    item = row.getChild(sel[1])
    t = m.titles.Lookup(item.id)
    if t = invalid then return
    m.cur = t
    m.dTitle.text = t.title
    sub_ = t.year
    if t.tv
        if sub_ <> "" then sub_ = sub_ + "  ·  "
        sub_ = sub_ + "TV show"
    end if
    m.dSub.text = sub_
    m.dPoster.uri = posterUrl(t.poster, "w500")
    m.dList.content = CreateObject("roSGNode", "ContentNode")
    m.rows.visible = false
    m.hint.visible = false
    m.detail.visible = true
    m.dList.setFocus(true)
    say("Loading files...")
    ask({ kind: "files", tids: t.tors })
end sub

function epOf(p as String) as Object
    mm = CreateObject("roRegex", "S(\d{1,2})\s?E(\d{1,3})", "i").Match(p)
    if mm.Count() < 3 then mm = CreateObject("roRegex", "\b(\d{1,2})x(\d{2})\b", "").Match(p)
    if mm.Count() < 3 then return invalid
    return { s: mm[1].ToInt(), e: mm[2].ToInt() }
end function

function qualOf(p as String) as String
    mm = CreateObject("roRegex", "2160p|1080p|720p|480p|\b4k\b", "i").Match(p)
    if mm.Count() = 0 then return ""
    q = LCase(mm[0])
    if q = "4k" then q = "2160p"
    return q
end function

function sizeOf(b as Dynamic) as String
    if b = invalid then return ""
    gb = b / 1073741824#
    if gb >= 1 then return Str(Int(gb * 10 + 0.5) / 10).Trim() + " GB"
    return Int(b / 1048576).ToStr() + " MB"
end function

function clock(s as Integer) as String
    h = Int(s / 3600)
    mi = Int((s Mod 3600) / 60)
    se = s Mod 60
    t = mi.ToStr()
    if h > 0
        t = h.ToStr() + ":" + Right("0" + mi.ToStr(), 2)
    end if
    return t + ":" + Right("0" + se.ToStr(), 2)
end function

sub showFiles(files as Object)
    say("")
    ' episodes in order; films keep their copies, best quality first
    for each f in files
        e = epOf(f.path)
        f.s = 0
        f.e = 0
        if e <> invalid
            f.s = e.s
            f.e = e.e
        end if
        f.k = f.s * 1000 + f.e
        f.q = qualOf(f.path)
    end for
    if m.cur.tv
        files.SortBy("k")
    else
        files.SortBy("bytes", "r")
    end if
    m.files = files
    c = CreateObject("roSGNode", "ContentNode")
    for each f in files
        it = c.createChild("ContentNode")
        label = ""
        if m.cur.tv and f.e > 0
            label = "Season " + f.s.ToStr() + "  ·  Episode " + f.e.ToStr()
        else
            label = "Play"
        end if
        extra = f.q
        sz = sizeOf(f.bytes)
        if sz <> ""
            if extra <> "" then extra = extra + ", "
            extra = extra + sz
        end if
        if extra <> "" then label = label + "   (" + extra + ")"
        p = m.prog.Lookup(f.link)
        if p <> invalid and p.pos > 30 then label = label + "   ·  Resume " + clock(p.pos)
        it.title = label
    end for
    if files.Count() = 0 then say("No video files in this download.")
    m.dList.content = c
    m.dList.setFocus(true)
    ' jump to the episode you were last on
    best = invalid
    bi = 0
    for i = 0 to files.Count() - 1
        p = m.prog.Lookup(files[i].link)
        if p <> invalid and (best = invalid or p.at > best.at)
            best = p
            bi = i
        end if
    end for
    if bi > 0 then m.dList.jumpToItem = bi
end sub

sub onFile()
    i = m.dList.itemSelected
    if i < 0 or i >= m.files.Count() then return
    f = m.files[i]
    m.playing = { link: f.link, tid: f.tid, title: m.cur.title, path: f.path, tried: false, id: "", streamable: 0, idx: i }
    if m.cur.tv and f.e > 0 then m.playing.title = m.cur.title + "  S" + f.s.ToStr() + " E" + f.e.ToStr()
    say("Starting...")
    ask({ kind: "stream", link: f.link })
end sub

' ---------- player ----------
sub playUrl(url as String, r as Object)
    if m.playing = invalid then return
    m.playing.id = r.id
    m.playing.streamable = r.streamable
    ext = LCase(CreateObject("roRegex", "^.*\.", "").Replace(m.playing.path, ""))
    fmt = "mp4"
    if ext = "mkv" then fmt = "mkv"
    if ext = "ts" then fmt = "ts"
    playStream(url, fmt)
end sub

sub playStream(url as String, fmt as String)
    c = CreateObject("roSGNode", "ContentNode")
    c.url = url
    c.title = m.playing.title
    c.streamFormat = fmt
    p = m.prog.Lookup(m.playing.link)
    if p <> invalid and p.pos > 30 and (p.dur = 0 or p.pos < p.dur - 60) then c.PlayStart = p.pos
    m.video.content = c
    m.video.visible = true
    m.video.setFocus(true)
    m.video.control = "play"
    say("")
end sub

sub onVideoState()
    st = m.video.state
    if st = "error"
        ' the TV can't play this file itself: try Real-Debrid's converted stream once
        if m.playing <> invalid and not m.playing.tried and m.playing.id <> ""
            m.playing.tried = true
            m.video.control = "stop"
            say("This file needs converting for the TV, starting Real-Debrid's stream...")
            ask({ kind: "transcode", id: m.playing.id })
            return
        end if
        msg = m.video.errorMsg
        closeVideo()
        say("The TV couldn't play this file. " + msg)
    else if st = "finished"
        if m.playing <> invalid then m.prog.Delete(m.playing.link)
        saveProg()
        closeVideo()
    end if
end sub

sub onVideoPos()
    if m.playing = invalid then return
    pos = Int(m.video.position)
    dur = Int(m.video.duration)
    if pos < 5 then return
    m.prog[m.playing.link] = { pos: pos, dur: dur, at: CreateObject("roDateTime").AsSeconds(), tid: m.playing.tid }
    if Abs(pos - m.lastSave) >= 10
        m.lastSave = pos
        saveProg()
    end if
end sub

sub saveProg()
    ' keep the 40 most recent so it fits in the TV's small settings storage
    list = []
    for each k in m.prog
        list.Push({ k: k, at: m.prog[k].at })
    end for
    if list.Count() > 40
        list.SortBy("at", "r")
        for i = 40 to list.Count() - 1
            m.prog.Delete(list[i].k)
        end for
    end if
    m.reg.Write("prog", FormatJson(m.prog))
    m.reg.Flush()
end sub

sub closeVideo()
    if m.video.visible
        m.video.control = "stop"
        m.video.visible = false
    end if
    saveProg()
    m.playing = invalid
    if m.detail.visible and m.cur <> invalid
        showFiles(m.files)
    else
        m.rows.setFocus(true)
    end if
end sub

' ---------- remote ----------
function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "back"
        if m.video.visible
            closeVideo()
            return true
        else if m.detail.visible
            m.detail.visible = false
            m.rows.visible = true
            m.hint.visible = true
            m.rows.setFocus(true)
            buildRows()
            return true
        else if m.searching <> ""
            buildRows()
            return true
        end if
        return false
    end if
    if m.setup.visible and key = "OK"
        askKey("rd")
        return true
    end if
    if key = "options" and m.rows.visible
        m.asking = "search"
        d = CreateObject("roSGNode", "StandardKeyboardDialog")
        if d = invalid then d = CreateObject("roSGNode", "KeyboardDialog")
        d.title = "Search your library"
        d.buttons = ["Search", "Cancel"]
        d.observeField("buttonSelected", "onKeyDialog")
        m.dlg = d
        m.top.dialog = d
        return true
    end if
    return false
end function
