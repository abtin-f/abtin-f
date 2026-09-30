# Glass Music

An offline, Apple Music–style player for Android (Jetpack Compose, Media3, Coil, Navigation Compose).
Translucent "liquid glass" surfaces (built on [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) by Kyant0, Apache-2.0 – see NOTICE.md) are used for the mini-player, tab bar and player controls only;
everything else stays flat and artwork-first.

## Screens
- **Home** – large title + avatar, *Top Picks for You* (172×232dp cards), *Recently Played*, *Recently Added*, playlists
- **New / Radio / Library / Search** – Library has Playlists, Artists, Albums, Songs, Downloaded, Recently Added; Search filters songs, artists, albums, playlists (and lyrics)
- **Playlist Playground** – dark blue→teal editor: undo/redo, drag to reorder, swipe to remove, "+" add to playlist, prompt bar ("Customize playlist?", optional voice input), save
- **Now Playing** – artwork mode and lyrics mode (synced LRC, translations, tap a line to seek), palette-tinted blurred background, queue with shuffle / repeat / AutoMix
- **AutoMix** – fades the end of a song into the next one; the progress bar shows "Mixing"

## Offline behaviour
- Music comes from the device (`MediaStore`, needs the audio permission). Every local song counts as "Downloaded".
- With no local music (or permission denied) a built-in **sample library** is shown; its songs have no audio file, so playback is simulated.
- Lyrics: import a `.lrc` (or plain text) per song from the ⋯ menu in Now Playing. Two fictional demo songs ship with original sample lyrics.
- Favorites, playlists, history and lyrics are stored locally (`SharedPreferences`).

## Structure
`data/` (models, MediaStore, repository, user store, LRC parser) · `playback/` (`PlayerController`, `PlaybackService`) ·
`ui/` (`MusicViewModel`, `AppRoot`, screens) · `ui/components/` (AlbumCard, FeaturedCard, SongRow, MiniPlayer, BottomNavigation, DynamicAlbumBackground…) · `ui/theme/` (colors, type, line icons)

## Build
Android Studio (Koala+) or `./gradlew assembleDebug`. minSdk 31 (Android 12+: blur; Android 13+: lens refraction), compileSdk 36.
