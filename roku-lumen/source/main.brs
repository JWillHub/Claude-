' Lumen for Roku: your Real-Debrid library on the TV.
' Keys arrive from Lumen on the phone as launch parameters (ECP: POST http://<tv>:8060/launch/dev?rd=...&tmdb=...),
' either when the app starts or while it's already open (roInput).
sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    inp = CreateObject("roInput")
    inp.SetMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.Show()
    takeArgs(scene, args)
    while true
        msg = wait(0, port)
        t = type(msg)
        if t = "roSGScreenEvent"
            if msg.IsScreenClosed() then return
        else if t = "roInputEvent"
            if msg.IsInput() then takeArgs(scene, msg.GetInfo())
        end if
    end while
end sub

sub takeArgs(scene as Object, args as Dynamic)
    if args = invalid then return
    if GetInterface(args, "ifAssociativeArray") = invalid then return
    rd = args.Lookup("rd")
    if rd <> invalid and rd <> ""
        tm = args.Lookup("tmdb")
        if tm = invalid then tm = ""
        scene.setKeys = { rd: rd, tmdb: tm }
    end if
end sub
