# BitTV real data and TV integrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace plan 1's demo stand-ins with real sources — installed apps and launching, weather, news, spoken answers (TextToSpeech) with a voice picker, Wikipedia/weather/time answers, speech recognition for Search and the mic key — each behind an interface with a fake, so the emulator demo keeps working and the TV-only parts fail safe.

**Architecture:** One adapter per source in `platform/` or `data/`, each an interface plus a real implementation plus a fake; `AppGraph` wires the real ones; screens keep consuming the plan 1 types (`HomeState`, `Answer`, `Speaker`, `AnswerProvider`, `listen: suspend () -> String?`). Network through `net/Http` (user agent already set). Everything that touches the network caches its last good value and shows an offline state instead of invented data.

**Tech Stack:** as plan 1, plus Android `TextToSpeech`, `SpeechRecognizer`, `PackageManager`, `MediaSessionManager` + `NotificationListenerService`, `TvInputManager`/`TvView`, `TvContract.WatchNextPrograms`; Open-Meteo, BBC News RSS, Wikipedia REST.

**Spec:** `docs/superpowers/specs/2026-10-25-bittv-launcher-design.md` §4–5 (plan 1: `docs/superpowers/plans/2026-10-25-bittv-app-ui.md`).

## Global Constraints

- Never run anything against the TV; emulator `emulator-5554` only. TV-only adapters must degrade to the plan 1 behaviour when the platform says no.
- No personal data in any request (user agent `BitTV/0.1 (personal Android TV home screen)`).
- Network failures → last cached value or an explicit offline state; never invented numbers.
- Loops and refreshes run only while Home is resumed; refresh weather every 30 min, news every 15 min, Today at midnight.
- Mic button behaviour is a setting (`MicMode.BITTV` answers in the active section / `MicMode.GOOGLE` lets the system Assistant handle it), default `BITTV` (decided 2026-10-25). Questions BitTV cannot answer are handed to Google Assistant.

## Review Focus

1. No network at boot → Home shows clock and rows, no weather, no crash — `WeatherRepoTest.offlineGivesNull`.
2. Speech engine missing or mic permission denied → Search's Speak returns to typing with a message, never hangs — `AndroidListenerTest.errorReturnsNull` (fake recognizer).
3. TTS has no voice installed → answers still count down after the estimated reading time — `TtsSpeakerTest.noEngineFallsBackToEstimate`.
4. An app on the rail is uninstalled while BitTV runs → its icon greys out on resume, launching it opens the not-installed screen — `AppsRepoTest.refreshOnResume`.
5. Wikipedia returns a disambiguation page or no thumbnail → text answer, no broken image — `WikiAnswersTest.noThumbnailGivesText`.

---

### Task 1: Installed apps and launching
**Files:** `platform/AppsRepo.kt` (interface `AppsRepo { fun installed(apps: List<AppEntry>): Set<String>; fun launch(app: AppEntry): Boolean; fun openStore(app: AppEntry) }`, `AndroidAppsRepo` via `getLeanbackLaunchIntentForPackage`/`getLaunchIntentForPackage` and `market://details?id=`), manifest `<queries>` for LEANBACK_LAUNCHER/LAUNCHER/the 14 packages; Root uses it on resume. Tests: `AppsRepoTest` with a fake PackageManager facade (`installedPicksFirstPresentCandidate`, `refreshOnResume`).

### Task 2: Weather and Today refresh
**Files:** `data/WeatherRepo.kt` (`suspend fun now(): WeatherNow?`, `suspend fun forecast(): List<DayForecast>`; Open-Meteo London 51.51,-0.13; parse with `org.json`), cache in memory; Home reads `StateFlow<WeatherNow?>`. Tests: `parsesOpenMeteo` (fixture JSON), `offlineGivesNull`, `codeToKind` (0–1 sun, ≤48 or 71–77/85–86 cloud, else rain).

### Task 3: News headlines
**Files:** `data/NewsRepo.kt` (BBC News top-stories RSS `https://feeds.bbci.co.uk/news/rss.xml`, `XmlPullParser`, first 5 items, "BBC News · N min ago"). Tests: `parsesRssFixture`, `offlineKeepsLast`.

### Task 4: Spoken answers and the voice picker
**Files:** `platform/TtsSpeaker.kt` (`Speaker` over `TextToSpeech`; suspends until `onDone`; `stop()` on cancellation; English voices en-GB first; falls back to `EstimatedSpeaker` when init fails or no voice), Settings receives `voices` and plays the sample. Tests: `TtsSpeakerTest.noEngineFallsBackToEstimate`, `cancellationStopsSpeech` (fake engine).

### Task 5: Live answers (weather, time, Wikipedia)
**Files:** `assistant/LiveAnswers.kt` (`AnswerProvider`: weather from Task 2, time, Wikipedia search + REST summary, 960 px thumbnail with 330 px fallback, `Picture` when an image exists else `Text`); `DemoAnswers` stays for the M key. Tests: `WikiAnswersTest` with fixtures (`pictureWhenThumbnail`, `noThumbnailGivesText`, `termStripsQuestionWords`).

### Task 6: Speech recognition (Search Speak and the mic key)
**Files:** `platform/Listener.kt` (`suspend fun listen(onPartial: (String) -> Unit): String?` over `SpeechRecognizer`, `RECORD_AUDIO` runtime permission, returns null on error/timeout); Search's `listen` and the Assistant use it; mic key (`KEYCODE_SEARCH`, `KEYCODE_VOICE_ASSIST`) handling per `MicMode` setting. Tests: `AndroidListenerTest.errorReturnsNull`, `partialResultsAreForwarded` (fake recognizer).

### Task 7: TV-only adapters (implemented with safe fallbacks; verified on the TV later)
**Files:** `platform/NowPlaying.kt` (NotificationListenerService + MediaSessionManager → `StateFlow<NowPlaying?>`, transport controls; needs `cmd notification allow_listener` — install.sh grants it); `platform/WatchNext.kt` (`READ_TV_LISTINGS`, read whole table, filter in code; empty → row uses Watch Later); `platform/TvInputs.kt` (list HDMI inputs and state; `TvView` passthrough attempt, status tile fallback). Tests with fakes only (`nowPlayingMapsMetadata`, `watchNextEmptyFallsBack`, `inputsWithoutPermissionGiveStatusTile`).

### Task 8: TMDB catalogue — DONE ahead of plan (commit a3444c1); remaining: try it with the real key
### Task 9: Cast devices from the network
**Files:** `platform/CastDevices.kt` (`NsdManager` discovery of `_companion-link._tcp` / `_airplay._tcp` for Apple devices by name; Android phones remembered when they cast; demo list as fallback). Tests: `castDevicesFromBonjourNames`, `noNetworkKeepsKnownDevices`.

### (was) Task 8: TMDB catalogue (when `tmdb.apiKey` is set)
**Files:** `data/TmdbCatalog.kt` (trending + watch providers GB/IT mapped to `Svc`, posters at tile size), `AppGraph` picks it when the key exists. Tests with fixtures (`mapsProvidersToServices`, `noKeyUsesDemo`).

Each task: failing test → implement → pass → screenshot on the emulator where visible → commit.
