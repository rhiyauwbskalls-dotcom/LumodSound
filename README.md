# Sonaris

🌐 **English** · [Español](LÉEME.md)

An unofficial, open-source **YouTube Music client for Android**, built with Kotlin, Jetpack Compose (Material 3) and Media3.
It focuses on a clean, modern look (dark-first, cover-colored backgrounds, floating navigation), synced lyrics and a few
quality-of-life extras: Discord Rich Presence, Last.fm scrobbling, lyrics translation and a smart queue.

> **Status:** early and actively developed. Things will break. Issues and pull requests are welcome.

---

## ⚠️ Disclaimer

- Sonaris is **not affiliated with, endorsed by, or sponsored by** YouTube, Google, Spotify, Discord, Last.fm or any other
  service mentioned here. All names and trademarks belong to their respective owners.
- Music and metadata come from YouTube Music through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor),
  **not** through an official API. This may go against YouTube's Terms of Service, and it can stop working at any time.
- Sonaris does not host, store or distribute any copyrighted content. Downloads and the audio cache are meant for personal,
  offline use only. **You are responsible** for following the law and the terms of the services you use.
- The software is provided "as is", without warranty of any kind. Use it at your own risk.

## ⚠️ Spotify-related features — read before enabling

Sonaris can **optionally** use Spotify's synced lyrics and Spotify **Canvas** (the short looping videos) in the player.

**This is not an official Spotify integration.** It works through Spotify's *unofficial, undocumented* web-player API,
authenticated with your own `sp_dc` session cookie. Please be aware of the following:

- It very likely goes against **Spotify's Terms of Use** (unofficial clients and automated access). Using it could lead to
  rate limits or restrictions on your Spotify account. **You use it entirely at your own risk**, and the maintainers and
  contributors are not responsible for any consequence.
- It is **off by default**. Nothing is sent to Spotify unless you log in yourself under *Settings → Spotify*.
- The endpoints are internal and change without notice, so these features can break at any moment.
- To request an access token, the app needs secrets used by Spotify's web player. A few are bundled in the code, and an
  up-to-date list is downloaded from a community-maintained GitHub repository
  ([`xyloflake/spot-secrets-go`](https://github.com/xyloflake/spot-secrets-go)). That list is third-party and its
  availability is not guaranteed.
- If you would rather not use any of this, simply never log in to Spotify inside the app. Everything else keeps working
  (lyrics fall back to [LRCLIB](https://lrclib.net)).
- If you represent Spotify or a rights holder and have a concern about this feature, please open an issue and we will
  take it seriously.

## Features

**Browsing and playback**
- Home feed with YouTube Music recommendations, mood/genre chips and a quick-access grid of your recent songs
- Search for songs and albums; artist and album pages
- Background playback with media notification and lock-screen controls (Media3)
- Smart queue with infinite radio, artist radio, drag-to-reorder and history
- Shuffle, repeat, sleep timer and crossfade

**Library and offline**
- Favorites and playlists stored on your device
- Audio cache (configurable size) so a replayed song starts instantly
- Permanent downloads, plus a "Cache" card in the library with fully downloaded songs
- Optional: send your listening history to your YouTube Music account to improve recommendations (off by default)

**Lyrics**
- Time-synced lyrics (LRCLIB) with tap-to-seek, shown full screen
- Lyrics translation, either on-device (ML Kit) or with your own AI provider API key
  (Claude, ChatGPT, Gemini, OpenRouter, Pollinations or a custom endpoint)

**Integrations (all optional)**
- Discord Rich Presence
- Last.fm scrobbling
- Spotify synced lyrics and Canvas — see the warning above

**Look and feel**
- Dark-first theme with a single accent color (easy to change in `ui/Theme.kt`) and an optional Material You mode
- Player, queue and lyrics screens tinted with the cover's color
- Floating mini player and navigation bar that hide while scrolling

## Screenshots

_Coming soon. Contributions with screenshots are welcome!_

## Requirements

- Android 8.1 (API 27) or newer
- To build: Android Studio Koala or newer, JDK 17

## Build

1. Clone the repository and open the folder in Android Studio.
2. Let Gradle sync (Android Studio downloads the missing Gradle wrapper files by itself).
3. Run the `app` configuration on a device or emulator.

**Last.fm (optional):** the Last.fm keys are not in the code. Create a free API account at
<<<<<<< HEAD
<https://www.last.fm/api/account/create>, copy `lastfm.properties.example` to `app/src/main/assets/lastfm.properties` and fill in
=======
<https://www.last.fm/api/account/create>, copy `local.properties.example` to `local.properties` and fill in
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/Sonaris
`LASTFM_API_KEY` and `LASTFM_SECRET`. Without them the app still works, but Last.fm scrobbling stays unavailable.
For the GitHub Actions build, add the same two names as repository secrets (*Settings → Secrets and variables → Actions*).

**No computer?** Push the project to GitHub. The workflow in `.github/workflows/build.yml` builds a debug APK:
*Actions → Build APK → Artifacts*.

If playback or browsing suddenly stops working, YouTube probably changed something. Bump the version of
`com.github.TeamNewPipe:NewPipeExtractor` in `app/build.gradle.kts` and try again.

## Privacy and security

- Sonaris has **no analytics, ads or crash-reporting libraries**, and no server of its own.
- The app talks directly to the services you use: YouTube/Google, LRCLIB, and (only if you enable them) Last.fm, Discord,
  Spotify and the AI provider you configure. On-device translation downloads language models the first time you use it.
- Discord and Spotify credentials are encrypted at rest with AES-256-GCM using a key kept in the Android Keystore.
- Your YouTube Music session cookies are kept in the app's private storage **without extra encryption**, so someone with
  root access to your phone could read them. Logging out deletes them.
- Never share your cookies, tokens or API keys in issues or screenshots.

## Battery tips

Lyrics, animated screens and background playback all use some battery. If you notice high usage, open
*Settings → Battery* inside the app to jump to Android's battery settings for Sonaris, and avoid leaving the lyrics screen
open for long periods.

## Project status and roadmap

Help is welcome with any of these:

- [ ] Add screenshots to this README
- [ ] Redesign the Library and Search screens to match the rest of the app
- [ ] Move the hard-coded Spanish UI strings to `strings.xml` and add translations
- [ ] Release signing and tagged releases
- [ ] Tests

## Contributing

1. Open an issue first for big changes, so we can talk about it.
2. Fork the repo, create a branch, and keep each pull request focused on one thing.
3. Make sure the app builds (`gradle assembleDebug`) before opening the pull request.
4. Do not commit personal keys, cookies or tokens.

## License

Sonaris is released under the **GNU General Public License v3.0** (see the `LICENSE` file), which is required because it
builds on NewPipeExtractor (GPL-3.0).

## Acknowledgements

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube data extraction
- [LRCLIB](https://lrclib.net) — synced lyrics
- [AndroidX Media3](https://developer.android.com/media/media3), [Jetpack Compose](https://developer.android.com/jetpack/compose),
  [Coil](https://coil-kt.github.io/coil/), [OkHttp](https://square.github.io/okhttp/), [ML Kit](https://developers.google.com/ml-kit)
- Inspired by other open-source music clients such as Metrolist and SimpMusic
