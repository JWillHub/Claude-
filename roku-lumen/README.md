# Lumen for Roku

A Roku version of Lumen: your Real-Debrid library as rows of posters, a title page with copies or episodes,
and Roku's own player with resume points. Files the TV can't play itself fall back to Real-Debrid's converted stream.

- `source/main.brs`: starts the app; takes the keys Lumen on the phone sends (`/launch/dev?rd=...&tmdb=...`)
- `components/MainScene`: rows, title page, player, remote keys (* to search, Back to go back)
- `components/ApiTask`: Real-Debrid and TMDB calls
- `.github/workflows/roku-lumen.yml` zips it as the `roku-latest` release:
  https://github.com/JWillHub/Claude-/releases/download/roku-latest/lumen-roku.zip

Installed through the Roku's developer mode (sideloaded), so updates are re-uploaded the same way.
