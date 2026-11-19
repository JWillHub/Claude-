sub init()
    m.bg = m.top.findNode("bg")
    m.img = m.top.findNode("img")
    m.name = m.top.findNode("name")
    m.alt = m.top.findNode("alt")
    m.bar = m.top.findNode("bar")
    m.barbg = m.top.findNode("barbg")
end sub
sub show()
    c = m.top.itemContent
    if c = invalid then return
    m.name.text = c.title
    m.alt.text = c.title
    m.img.uri = c.HDPosterUrl
    m.alt.visible = (c.HDPosterUrl = "")
    p = c.getField("progress")
    if p <> invalid and p > 0
        m.barbg.visible = true
        m.bar.visible = true
        m.bar.width = 220 * p
    else
        m.barbg.visible = false
        m.bar.visible = false
    end if
end sub
sub focus()
    f = m.top.focusPercent
    m.name.color = &hFFFFFFFF
    if f < 0.5 then m.name.color = &h9A9A9AFF
end sub
  
