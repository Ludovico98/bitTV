# BitTV launcher — design spec

Status: **draft for Ludovico's review** (written 2026-10-25 while he was away; nothing has been installed on the TV).
Inputs: `docs/requirements.md` (his requirements), `BitTV Prototype.html` (the agreed look and feel),
the cleanyourtv project (field notes for the same TCL G08 platform), Android TV quality guidelines, Apple tvOS HIG.

## 1. What we are building

BitTV is a native Android TV **home screen app** (launcher) for one TV: TCL 55C6K-UK, Google TV 14 (API 34),
MediaTek SoC, ~2.4 GB RAM. It replaces the stock Google TV home (disabled over ADB, reversible) and reproduces the
browser prototype natively, with real data and real platform integrations where the platform allows them.

Non-goals: a custom OS, publishing to the Play Store, supporting other TVs, accounts/sync, playing video itself.

## 2. Key decisions

| Decision | Choice | Why |
|---|---|---|
| UI toolkit | Kotlin + Jetpack Compose with `androidx.tv:tv-material` 1.1.0 | Compose 1.9+ matches Views for scroll jank (Android docs); springs, focus and theming are far quicker to build to the prototype's standard than with Views. Performance is protected by Baseline Profiles, R8 and the budgets in §7. |
| Versions | Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.2, Compose BOM 2026.09.00, Coil 3.6.3, OkHttp 5.5.1, kotlinx-serialization 1.12.0, DataStore 1.3.0 | Latest stable on 2026-10-25. |
| SDK levels | `minSdk 31`, `targetSdk 36`, `compileSdk 36` | TV quality guideline TV-PS asks for minSdk ≤ 31; the TV is 34. |
| Modules | `:app` and `:baselineprofile` (macrobenchmark + profile generator) | Smallest structure that still gets generated Baseline Profiles and measured startup/scroll. |
| Dependency wiring | Manual: one `AppGraph` object created in `Application` | ~10 dependencies; a DI framework would cost startup time and APK size for no gain. |
| Data | Content behind a `CatalogSource` interface: `TmdbCatalog` when `tmdb.apiKey` is set in `local.properties`, otherwise `DemoCatalog` (the prototype's catalogue) | The owner has no TMDB key yet; the app must be fully reviewable without one. |
| Persistence | Preferences DataStore | Subscriptions, Watch Later, Assistant voice, Animations, ambient colour. |
| Images | Coil 3, always requested at the exact tile size, memory cache sized for the TV | Decoding full-size posters is the classic TV launcher jank source. |
| Home | `MAIN`+`HOME`+`DEFAULT` activity, `singleTask`, `excludeFromRecents`, `stateNotNeeded`, landscape; also `LEANBACK_LAUNCHER` so it shows in the stock app list | Same as cleanyourtv's working TCL launcher. |
| Package | `com.bittv.home` | |

## 3. Screens and behaviour (native equivalents of the prototype)

All dimensions are in dp on a 960×540 dp TV canvas (1920×1080 px at xhdpi), i.e. half the prototype's px values.

1. **Boot** — Ludovico's animation (current version: RATE 2, blue sheen) ported to a Compose `Canvas` using the same
   maths (bezier arcs, squash and stretch, settle springs, sheen). Shown on cold start only, ~2.6 s, any key skips,
   then a 200 ms cross-fade to Home. Never shown when returning from an app.
2. **Home** — app bar (left), shortcut buttons + clock/date/weather (top), banner slideshow + active section, rows:
   Watch Later, Continue Watching, Devices, Suggested, Series, Movies, genre rows, Italian TV, Free to watch.
   - Focus rules (proven in the prototype's 267-move sweep): Left/Right stay in the row; Up/Down move between rows and
     never into the app bar; Left on the first item of a row enters the app bar; Right from the app bar restores the
     last content focus. Implemented with `focusRestorer`, `focusGroup`, and explicit `focusProperties` exits.
   - Vertical motion: the focused row sits on a fixed focus line (where the first row rests), one row per press,
     moved by a critically damped spring that keeps its velocity when retargeted. Rows taller than the line allows
     rise just enough to stay inside the bottom safe area (30 dp = 60 px).
   - Horizontal motion: `LazyRow` with a pivot offset; focused tile scales 1.10 with a white border glow, tvOS lift,
     sway and light in ~160 ms; the last tile is cut off.
   - Ambient colour bleed: the background's two radial colours tween (350 ms) to the focused poster's palette.
     Palette is extracted off the main thread once per image and cached.
3. **Search** — QWERTY grid (numbers, letters, `- ' .`, ⌫, space, clear), Speak button in the search bar (the only
   mic option), results grid (4 columns) + Similar content. Only titles available on owned apps. Phrase matching as
   in the prototype (stop words, per-word AND, partial fallback).
4. **Title details** — full-bleed artwork, Play/Resume on <app>, Watch Later toggle, Back, "Available on" chips.
5. **Not installed** — "<App> isn't installed", text button "Install <App>" (opens the Play Store listing), Back.
6. **Settings** — subscriptions; Google Assistant voice + Hear a sample; Ambient colour; Animations on/off;
   Overscan guides; Replay boot; Reset Watch Later.
7. **Active section** — Spotify now playing (real media session) with prev/play/next, news headline, Today (clock
   changes, holidays). Turns into the Assistant answer area (§5).

## 4. Platform integration — what is possible and how

| Feature | Approach | Status / risk |
|---|---|---|
| App bar, launching apps | `PackageManager.getLeanbackLaunchIntentForPackage`; missing app → Play Store `market://details?id=` | Solid. |
| Title deep links | Per-app `DeepLinker` table; v1 opens the app (and its search where an intent is known); exact title links added per app after testing on the TV | Netflix/Disney+/Prime links need their own IDs, not TMDB's. |
| Spotify now playing | `NotificationListenerService` (granted once with `cmd notification allow_listener`) + `MediaSessionManager.getActiveSessions` → `MediaController` for metadata and transport controls | Solid on Android 14; needs the one-time grant. |
| Continue Watching | `TvContract.WatchNextPrograms` with `READ_TV_LISTINGS`; only `searchable` rows are visible to us | cleanyourtv found only YouTube/Spotify publish. Row falls back to Watch Later items. |
| Weather | Open-Meteo (no key) | Solid. |
| News | An RSS feed (BBC News top stories) parsed off the main thread | Solid. |
| PS5 live tile | `TvInputManager.getTvInputList()` to find HDMI inputs and their state; `TvView` tuned to `TvContract.buildChannelUriForPassthroughInput(id)`, muted | **Unproven**: TCL may restrict passthrough sessions to system apps. Fallback: status tile ("PS5 · On") that switches input on select. Emulator has no HDMI → demo tile. |
| Remote mic → Assistant answers in the section | Google Assistant draws its own overlay and does not hand answers to other apps. To get answers in the section BitTV must own the button: `SpeechRecognizer` + `TextToSpeech` + answer providers (weather, Wikipedia, time) | **Decision pending with Ludovico.** Built behind `AssistantMode` (BitTV panel / Google). Default: BitTV panel for the mic key inside BitTV, Google Assistant still reachable by long-press via the system. |
| Remote special keys (Netflix, YouTube, Settings) | cleanyourtv's finding: with the stock home disabled they need an accessibility-service helper; out of scope for v1, documented | Later. |
| Debloat | `tools/tv/debloat.txt` adapted from cleanyourtv's 55C7K-UK list (same G08 board), applied with `pm disable-user`, restore script alongside | Prepared only; never run without Ludovico. |

## 5. Assistant answer area

States: `Idle → Listening → Thinking → Speaking → Counting(30 s) → Idle`.
- Fixed size (the active section's box never changes).
- Weather → animated scene (sun with soft round halo, clouds drifting, rain); nothing is clipped to a square.
- Question with an image → the image fills the box **uncropped** (`ContentScale.Fit`) over a blurred copy of itself;
  cropped (`Crop`) only when a whole image would cover < 35 % of the box. No text; the answer is spoken.
- Question without an image → title + three lines of text, spoken.
- The 30 s countdown and its line start **after** speech ends (or after an estimated reading time if no TTS voice).
- Selecting the section dismisses at once; a new question cancels the previous answer and speech.
- Voice chosen in Settings from the installed `TextToSpeech` voices (English, en-GB first).

## 6. Architecture

```
com.bittv.home
├─ BitTvApp (Application: builds AppGraph)        ├─ data/   CatalogSource (Demo/Tmdb), WeatherRepo, NewsRepo,
├─ MainActivity (HOME; hosts NavHost-free screen   │          WikiRepo, PrefsRepo (DataStore), TodayInfo
│   stack: Boot → Home ⇄ Search/Details/Settings)  ├─ platform/ AppsRepo (installed + launch), MediaSessions,
├─ ui/theme   (colours, type scale ≥ 23 px, dims)  │          TvInputs, WatchNext, Speech (STT/TTS)
├─ ui/components (FocusTile, AppIcon, GlowBorder,  ├─ assistant/ AssistantController (state machine), Answer
│   SpringScroll, AmbientBackground)              │          providers (Weather/Time/Wiki)
├─ ui/home, ui/search, ui/details, ui/settings,    └─ search/  QueryMatcher (pure Kotlin, unit tested)
│   ui/boot, ui/assistant
```
- Screen state lives in plain `ViewModel`-free state holders created per screen (`remember` + `rememberSaveable`
  where needed); long-lived state (prefs, catalog, now playing) in `AppGraph` as `StateFlow`s.
- Every platform adapter has a fake used by the emulator demo and tests.
- No work on the main thread beyond composition: network, JSON, RSS, palette extraction on `Dispatchers.IO/Default`.

## 7. Performance budgets (measured with Macrobenchmark on the Google TV API 34 emulator, then on the TV)

| Metric | Budget |
|---|---|
| Cold start to first Home frame (boot animation disabled) | ≤ 900 ms emulator, ≤ 1.2 s TV |
| Warm return to Home from another app | ≤ 300 ms |
| Frame time while holding Down/Right across Home | P90 ≤ 16 ms, P99 ≤ 33 ms (no frozen frames) |
| APK size (release) | ≤ 12 MB |
| Memory (PSS) on Home after scrolling everything | ≤ 180 MB |

Techniques: generated Baseline Profile (`:baselineprofile`), R8 full mode + resource shrinking, stable keys and
`contentType` in lazy lists, images decoded at tile size, palette cached, no backdrop blur on moving content, no video
autoplay, `onTrimMemory` releases image caches only when Home is not visible (cleanyourtv `MemoryTrimPolicy`).

## 8. Testing

- JVM unit tests: `QueryMatcher`, `TodayInfo` (UK clock change, holidays), `AssistantController` state machine,
  answer classification, image fit rule, deep-link table.
- Instrumented (emulator): Compose UI tests for focus rules (rail never entered vertically; Left from first tile
  enters rail; Right returns), search typing, settings persistence.
- Macrobenchmark: startup + "hold Down / hold Right" scroll jank; numbers recorded in `docs/perf.md`.
- Screenshots of every screen from the emulator into `docs/screens/` for Ludovico's review.

## 9. Delivery to the TV (prepared, not executed)

`tools/tv/` will contain: `pair.md` (wireless debugging pairing steps), `install.sh` (install APK, grant
notification listener and READ_TV_LISTINGS, set as Home: disable `launcherx` and `setupwraith` in the safe order
cleanyourtv documents), `restore.sh` (re-enable everything), `debloat.txt`. **None of it runs until Ludovico says so.**

## 10. Open questions for Ludovico

1. Assistant: BitTV owns the mic button (answers in the section) vs Google Assistant overlay — or long-press split.
2. TMDB API key (free) for real artwork and "where to watch" data; until then the demo catalogue is used.
3. Which debloat lines to keep (Chromecast, AirPlay, Netflix key, game bar…).
