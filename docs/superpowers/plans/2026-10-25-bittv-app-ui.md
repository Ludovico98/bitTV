# BitTV app and UI (demo data) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android TV launcher app that reproduces the agreed browser prototype (Home, Search, Details, Settings, Boot, Assistant answer area) with demo data, measured against the spec's performance budgets on the Google TV API 34 emulator.

**Architecture:** Single `:app` module (Kotlin, Compose + `androidx.tv:tv-material`), manual `AppGraph` wiring, pure-Kotlin logic (search, today info, assistant state machine, image fit) unit-tested on the JVM, UI focus rules tested with Compose instrumented tests on the emulator, plus a `:baselineprofile` module for the Baseline Profile and Macrobenchmark.

**Tech Stack:** Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.2, Compose BOM 2026.09.00, tv-material 1.1.0, DataStore 1.3.0, Coil 3.6.3, coroutines 1.11.0, JUnit 4, androidx.benchmark 1.5.0.

**Spec:** `docs/superpowers/specs/2026-10-25-bittv-launcher-design.md` (requirements: `docs/requirements.md`; visual reference: `BitTV Prototype.html`).

## Global Constraints

- Package `com.bittv.home`; `minSdk 31`, `targetSdk 36`, `compileSdk 36`.
- Canvas 960×540 dp (1920×1080 px at xhdpi): every prototype px value is halved to dp; text sizes halved to sp.
- Safe area 40 dp sides, 30 dp top/bottom. Minimum text 11.5 sp (23 px). Dark theme only.
- Focus: tile scale 1.10 with a white 2 dp border and glow, 160 ms; tvOS lift/sway/light after the scale.
- Vertical page motion: `spring(dampingRatio = 1f, stiffness = 340f)` (critically damped, keeps velocity when retargeted), one row per press on a fixed focus line.
- Animations setting off → every duration 0 and no looping motion except the Assistant countdown line.
- Assistant: fixed-size area; countdown 30 000 ms starting after speech ends; image fit crop threshold 0.35.
- Data comes from `DemoCatalog` and fakes; the only network use is Coil loading the demo Picture answers' Wikipedia images (plan 2 adds real sources).
- Never install to or run commands against the TV (192.168.0.175). Emulator only.

## Review Focus

1. Holding Down/Right (key repeat ~30/s) must glide without skipped focus or lost rows — Task 6 test `holdDownReachesLastRowWithoutSkipping`.
2. Removing the last Watch Later item while it is focused must move focus to a neighbour, not lose focus — Task 6 test `removingFocusedWatchLaterKeepsFocusInRow`.
3. A new Assistant question while an answer is speaking cancels the old speech and timer — Task 9 test `newQuestionCancelsPreviousAnswer`.
4. Search with only stop words ("the") or no matches shows the empty message, not a crash — Task 3 test `stopWordsOnlyFallsBackToPhrase` and `noMatchReturnsEmpty`.
5. Toggling a subscription off while its title is on the banner must rebuild the banner without crashing on an out-of-range slide index — Task 6 test `bannerSurvivesSubscriptionChange`.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew (wrapper 9.8.2)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/bittv/home/
  BitTvApp.kt                 Application; builds AppGraph
  AppGraph.kt                 holds PrefsRepo, CatalogSource, Clock
  MainActivity.kt             HOME activity; hosts BitTvRoot()
  model/Models.kt             Svc, Title, TitleType, TitleFont, Progress, AppEntry
  data/DemoCatalog.kt         the prototype's 34 titles, 7 progress entries, 14 apps
  data/PrefsRepo.kt           DataStore-backed Prefs flow + mutators
  search/QueryMatcher.kt      phrase search (pure)
  today/TodayInfo.kt          clock changes + holidays (pure)
  assistant/AssistantController.kt, assistant/Answers.kt, assistant/ImageFit.kt
  ui/theme/Theme.kt           colours, typography, dims
  ui/components/              FocusTile.kt, AppIcon.kt, AmbientBackground.kt, Motion.kt (motion tokens honouring Animations)
  ui/root/BitTvRoot.kt        screen stack (Boot → Home ⇄ Search/Details/Settings/NotInstalled)
  ui/home/                    HomeScreen.kt, AppBar.kt, TopBar.kt, Banner.kt, ActiveSection.kt, Rows.kt, SpringColumn.kt
  ui/search/SearchScreen.kt, ui/details/DetailsScreen.kt, ui/details/NotInstalledScreen.kt, ui/settings/SettingsScreen.kt
  ui/boot/BootAnimation.kt, ui/assistant/AssistantArea.kt, ui/assistant/WeatherScene.kt
app/src/main/res/font/        inter, bebas_neue, playfair_display, oswald, open_sans (OFL, downloaded from Google Fonts)
app/src/test/java/com/bittv/home/...      JVM tests
app/src/androidTest/java/com/bittv/home/  Compose UI tests
baselineprofile/              BaselineProfileGenerator.kt, StartupBenchmark.kt, ScrollBenchmark.kt
docs/perf.md, docs/screens/*.png
```

### Task 1: Project scaffold, HOME activity, emulator

**Files:** Create the Gradle files above, `app/build.gradle.kts`, `AndroidManifest.xml`, `BitTvApp.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`.

**Interfaces:**
- Produces: `BitTvTheme { }` composable; colour tokens `Ink`, `Ink2`, `Ink3`, `Glass`, `GlassLine`, `Bg`; dims object `Dims` (`SafeX = 40.dp`, `SafeY = 30.dp`, `Gap = 20.dp`).

- [ ] **Step 1:** Create Gradle wrapper 9.8.2, version catalog with the Tech Stack versions, `:app` with Compose enabled, R8 + `shrinkResources` for release (debug-signed, never published).
- [ ] **Step 2:** Manifest: `MainActivity` with `MAIN`+`HOME`+`DEFAULT` and `MAIN`+`LEANBACK_LAUNCHER`, `singleTask`, `stateNotNeeded`, `excludeFromRecents`, `clearTaskOnLaunch`, landscape; `uses-feature leanback required=false`, `touchscreen required=false`; banner 320×180 and icon.
- [ ] **Step 3:** `MainActivity` sets `BitTvTheme { Text("BitTV") }`.
- [ ] **Step 4:** Create AVD `bittv_gtv34` from `system-images;android-34;google-tv;x86`, device `tv_1080p`; start headless (`emulator -avd bittv_gtv34 -no-window -no-audio -gpu swiftshader_indirect`).
- [ ] **Step 5:** Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`, install, `adb shell cmd package set-home-activity com.bittv.home/.MainActivity`, press Home, screenshot. Expected: BUILD SUCCESSFUL; screenshot shows "BitTV" as the home screen.
- [ ] **Step 6:** Commit `feat: scaffold BitTV launcher app`.

### Task 2: Models, demo catalogue, preferences

**Files:** `model/Models.kt`, `data/DemoCatalog.kt`, `data/PrefsRepo.kt`, `AppGraph.kt`; tests `test/.../DemoCatalogTest.kt`, `test/.../PrefsRepoTest.kt`.

**Interfaces:**
- Produces:
  - `enum class Svc(val label: String, val free: Boolean) { NETFLIX, PRIME, NOW, PARAMOUNT, APPLETV, DISNEY, TUBI, ITVX, LA7, RAIPLAY }` (Tubi, ITVX, La7, RaiPlay free).
  - `data class Title(id, name, type: TitleType, genres: List<String>, services: List<Svc>, c1: Color, c2: Color, year: Int, score: Double, font: TitleFont, blurb: String)`
  - `data class Progress(titleId: String, fraction: Float, label: String)`
  - `data class AppEntry(id: String, label: String, packages: List<String>, svc: Svc?)` — the 14 rail apps; `packages` lists candidate package names (verified on the TV in plan 2).
  - `object DemoCatalog { val titles: List<Title>; val progress: List<Progress>; val apps: List<AppEntry> }` — values copied from the prototype's `CATALOG`, `CW`, `APPS`.
  - `data class Prefs(subs: Set<Svc>, watchLater: List<String>, animations: Boolean, ambient: Boolean, voice: String?, overscanGuides: Boolean)` defaults: all Svc except PARAMOUNT; watchLater `["longtide","loworbit","grandeur","commissario","static"]`; animations true; ambient true; voice null; guides false.
  - `class PrefsRepo(store: DataStore<Preferences>) { val prefs: Flow<Prefs>; suspend fun toggleSub(s: Svc); suspend fun toggleWatchLater(id: String): Boolean /* true = now saved */; suspend fun clearWatchLater(); suspend fun setAnimations(on: Boolean); suspend fun setAmbient(on: Boolean); suspend fun setVoice(name: String?); suspend fun setOverscanGuides(on: Boolean) }`
  - `fun Title.isAvailable(subs: Set<Svc>): Boolean`; `fun Title.mainService(subs: Set<Svc>): Svc`.

- [ ] **Step 1: Failing tests** — `DemoCatalogTest`: `catalogueHas34Titles`, `everyProgressRefersToATitle`, `availabilityFollowsSubscriptions` (`redharbour` unavailable with defaults, available after adding PARAMOUNT); `PrefsRepoTest` (DataStore on a temp file, `runTest`): `defaultsMatchSpec`, `toggleWatchLaterAddsAtFrontThenRemoves` (returns true then false), `settingsSurviveNewRepoInstance`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest` → FAIL (unresolved references).
- [ ] **Step 3:** Implement the interfaces above.
- [ ] **Step 4:** Run tests → PASS.
- [ ] **Step 5:** Commit `feat: demo catalogue and preferences`.

### Task 3: Search matching

**Files:** `search/QueryMatcher.kt`; test `test/.../QueryMatcherTest.kt`.

**Interfaces:**
- Produces: `data class SearchResult(val results: List<Title>, val similar: List<Title>)`; `object QueryMatcher { fun search(query: String, pool: List<Title>, subs: Set<Svc>): SearchResult }` — port of the prototype's `renderSearch`/`tokMatch` (stop words, stem trailing "s", service names, "series"/"movie|film"/"tv|apple"; AND match, fallback to best partial; phrase-in-title first; similar = same genres as top 3, max 12, empty query → no similar; empty query results = all available by score).

- [ ] **Step 1: Failing tests** with default subs: `crimeSeries` → results exactly `[tenements, commissario, northernline]`; `theLongTide` → first `longtide`; `sciFiOnAppleTv` → `[static, eclipse]`; `comedyMovies` → contains `lisbon`, `laughtrack`, `diner` only movies; `netflix` → 6 titles all containing NETFLIX; `stopWordsOnlyFallsBackToPhrase` ("the" → titles containing "the"); `noMatchReturnsEmpty` ("xyzzy" → empty results and similar); `unsubscribedServiceIsHidden` ("red harbour" → empty).
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: phrase search over owned apps`.

### Task 4: Today information

**Files:** `today/TodayInfo.kt`; test `test/.../TodayInfoTest.kt`.

**Interfaces:**
- Produces: `enum class TodayKind { CLOCK, CALENDAR }`; `data class TodayItem(val kind: TodayKind, val text: String)`; `object TodayInfo { fun items(today: LocalDate, max: Int = 2): List<TodayItem> }` — UK clock changes (last Sunday of March/October; day 0 "Clocks went back/forward 1 hr last night", day 1 "… tonight", 2–7 "Clocks go back in N days"), then nearest of Halloween 31/10, Bonfire Night 5/11, Christmas 25/12, New Year's Eve 31/12, Valentine's Day 14/2 ("N days until X", "It's X today").

- [ ] **Step 1: Failing tests:** `2026-10-25` → `[CLOCK "Clocks went back 1 hr last night", CALENDAR "6 days until Halloween"]`; `2026-10-24` → first `"Clocks go back 1 hr tonight"`; `2026-12-25` → first `"It's Christmas today"`; `2026-03-27` → `"Clocks go forward in 2 days"`.
- [ ] **Step 2–4:** fail → implement → pass. **Step 5:** Commit `feat: today information`.

### Task 5: Theme, motion tokens, focus tile, app icon, ambient background

**Files:** `ui/theme/Theme.kt` (extend), `ui/components/Motion.kt`, `FocusTile.kt`, `AppIcon.kt`, `AmbientBackground.kt`, fonts in `res/font`; instrumented test `androidTest/.../FocusTileTest.kt`.

**Interfaces:**
- Produces:
  - `val LocalMotion = staticCompositionLocalOf { Motion(enabled = true) }`; `class Motion(enabled) { fun <T> tween(ms: Int): AnimationSpec<T>; fun <T> pageSpring(): AnimationSpec<T>; val loops: Boolean }` — disabled → `snap()` and no loops.
  - `@Composable fun FocusTile(onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp, height: Dp, glow: Boolean = true, onFocus: () -> Unit = {}, content: @Composable BoxScope.() -> Unit)` — scale 1.10 on focus, 2 dp white border + glow shadow, lift/sway (±1.5°/2.5° via `graphicsLayer.rotationX/Y`, 4.5 s alternate) and a moving light gradient only while focused and `Motion.loops`.
  - `@Composable fun AppIcon(app: AppEntry, width: Dp, height: Dp)` — brand-coloured rounded rectangle (radius 10 dp) with wordmark/glyph per app as in the prototype.
  - `@Composable fun AmbientBackground(c1: Color?, c2: Color?)` — two radial gradients over `#06080d`, colours tweened with `Motion.tween(350)`; drawn in one `drawBehind` (no layers).
  - `@Composable fun PosterArt(title: Title, modifier)` — prototype `artBg` gradients + title in its font.

- [ ] **Step 1: Failing UI test** `FocusTileTest.focusedTileScalesTo110` (assert `graphicsLayer` scale via semantics property `TileScale` exposed by the tile) and `animationsOffMakesScaleInstant` (frame clock advanced 0 ms → scale 1.10).
- [ ] **Step 2:** Run `./gradlew :app:connectedDebugAndroidTest` on the emulator → FAIL. **Step 3:** Implement. **Step 4:** → PASS.
- [ ] **Step 5:** Commit `feat: theme, motion and focus tile`.

### Task 6: Home screen

**Files:** `ui/home/*.kt`, `ui/root/BitTvRoot.kt`; instrumented test `androidTest/.../HomeFocusTest.kt`.

**Interfaces:**
- Consumes: Task 2–5 interfaces.
- Produces: `@Composable fun HomeScreen(state: HomeState, actions: HomeActions)`; `class HomeState(prefs, catalog, today: List<TodayItem>, nowPlaying: NowPlaying?, news: String?, weather: WeatherNow?)`; `interface HomeActions { fun openTitle(t: Title); fun openApp(a: AppEntry); fun openSearch(); fun openSettings(); fun toggleWatchLater(t: Title) }`; `@Composable fun SpringColumn(focusLineIndex: Int = 0, content)` (vertical spring scroller placing the focused row on the line, taller rows clamped to the safe area); test tags `rail`, `row:<key>`, `tile:<row>:<titleId>`, `hero`, `active`.
- Layout values (dp): rail at x 40, width 97, icons 75×45, gap 15; content x 157 (+20 inner padding); hero 500×215; active section 243×215 fixed; top buttons 36 dp circles; rows: tile 160×90, gap 20, row title 19 sp.

- [ ] **Step 1: Failing UI tests:** `downNeverEntersRail` (from every tile in the first three rows, DPAD_DOWN/UP keeps focus out of `rail`); `leftFromFirstTileEntersRail`; `leftFromSecondTileStaysInRow`; `rightFromRailRestoresLastTile`; `holdDownReachesLastRowWithoutSkipping` (20 DPAD_DOWN at 33 ms → every row key visited in order); `removingFocusedWatchLaterKeepsFocusInRow`; `bannerSurvivesSubscriptionChange`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement Home (rail, top bar with clock `HH:mm | dd/MM/yyyy | weather`, banner slideshow 7 s with dots, active section with demo Spotify/news/today, rows; Devices row shows the PS5 demo tile; ambient colour from focused tile). **Step 4:** Run → PASS.
- [ ] **Step 5:** Screenshot Home on the emulator to `docs/screens/home.png`; compare with the prototype; commit `feat: home screen`.

### Task 7: Search, Details, Not installed, Settings

**Files:** `ui/search/SearchScreen.kt`, `ui/details/*.kt`, `ui/settings/SettingsScreen.kt`; instrumented test `androidTest/.../ScreensTest.kt`.

**Interfaces:** `SearchScreen(pool, subs, onOpen, onBack)`, `DetailsScreen(title, prefs, onPlay, onToggleWatchLater, onBack)`, `NotInstalledScreen(app, onInstall, onBack)`, `SettingsScreen(prefs, repo, voices: List<String>, onReplayBoot, onBack)`.

- [ ] **Step 1: Failing UI tests:** `typingOnKeyboardFiltersResults` (select C, R → results start with Tenements); `speakButtonIsTheOnlyMic` (exactly one node with tag `mic`); `backFromSearchReturnsToHome`; `notInstalledShowsTextButtonOnly` (button text "Install Paramount+", no image); `animationsSwitchPersists` (toggle → new activity → still off).
- [ ] **Step 2–4:** fail → implement → pass.
- [ ] **Step 5:** Screenshots `docs/screens/search.png`, `details.png`, `settings.png`; commit `feat: search, details and settings`.

### Task 8: Boot animation

**Files:** `ui/boot/BootAnimation.kt`; JVM test `test/.../BootMathTest.kt`.

**Interfaces:** `object BootMath { const val RATE = 2f; const val END = 4.9f; fun cubeState(index: Int, t: Float): CubeState; fun sheenX(t: Float): Float? }`; `@Composable fun BootAnimation(onDone: () -> Unit)` (Canvas; letters "BitTV" in Open Sans 500 with the ink gradient; four cubes; blue sheen `#6CC4FF` α 0.95 clipped to logo; any key → `onDone`).

- [ ] **Step 1: Failing tests:** `cubesRestAtDestinationAtEnd` (state at t=END equals `dest` ±0.5 for all four); `cubeHiddenBeforeAppear` (cube A invisible at t=0.4); `sheenOnlyDuringSweep` (null at 3.5, non-null at 4.0, null at 4.7).
- [ ] **Step 2–4:** fail → port the prototype maths → pass.
- [ ] **Step 5:** Record a 3-frame strip to `docs/screens/boot.png`; commit `feat: boot animation`.

### Task 9: Assistant answer area (demo answers)

**Files:** `assistant/AssistantController.kt`, `assistant/Answers.kt`, `assistant/ImageFit.kt`, `ui/assistant/AssistantArea.kt`, `ui/assistant/WeatherScene.kt`; JVM tests `AssistantControllerTest.kt`, `ImageFitTest.kt`, `AnswerClassifierTest.kt`.

**Interfaces:**
- `sealed interface AssistantState { data object Idle; data class Listening(val partial: String); data class Thinking(val query: String); data class Showing(val query: String, val answer: Answer, val counting: Boolean) }`
- `sealed interface Answer { val say: String; data class Weather(kind: WeatherKind, tempC: Int, label: String, days: List<DayForecast>, say); data class Clock(time: String, date: String, say); data class Picture(imageUrl: String, title: String, say); data class Text(title: String, body: String, say) }`
- `fun interface Speaker { suspend fun speak(text: String) }`; `fun interface AnswerProvider { suspend fun answer(query: String): Answer }`
- `class AssistantController(scope: CoroutineScope, speaker: Speaker, provider: AnswerProvider, showForMs: Long = 30_000) { val state: StateFlow<AssistantState>; fun startListening(); fun transcript(partial: String); fun submit(query: String); fun dismiss() }`
- `enum class FitMode { FIT, CROP }`; `fun fitMode(imgW: Int, imgH: Int, boxW: Int, boxH: Int): FitMode` (CROP when `min(ar/boxAr, boxAr/ar) < 0.35`).
- `object AnswerClassifier { fun kind(query: String): AnswerKind /* WEATHER, TIME, QUESTION */ }`; `object DemoAnswers : AnswerProvider` (the six prototype examples, M-key cycling via `next()`).

- [ ] **Step 1: Failing tests** (`runTest`, virtual time): `countdownStartsOnlyAfterSpeech` (speaker suspends 5 s → `counting == false` at 4 s, true at 5 s); `autoDismissAfter30sOfCounting`; `dismissDuringSpeechCancels` (speaker job cancelled, state Idle); `newQuestionCancelsPreviousAnswer`; `fitModeKeepsPortraitWhole` (1286×1842 in 432×296 → FIT); `fitModeCropsExtremePanorama` (4000×300 in 432×296 → CROP); classifier: "Will it rain tomorrow?" → WEATHER, "What time is it?" → TIME, "Who was Leonardo da Vinci?" → QUESTION.
- [ ] **Step 2–4:** fail → implement → pass.
- [ ] **Step 5:** `AssistantArea` replaces the active section's content (same fixed 243×215 dp box): listening dots, weather scene (sun halo radial gradient, clouds, rain drops; drawn in `Canvas`, never clipped to the scene box), picture (Coil, blurred backdrop + `ContentScale.Fit`/`Crop` from `fitMode`), text answer, speaking bars, countdown line. Key `M` (and `KEYCODE_SEARCH` long-press placeholder) triggers `DemoAnswers.next()` with the fake `Speaker` that waits an estimated reading time (plan 2 swaps in TextToSpeech).
- [ ] **Step 6:** Screenshots `docs/screens/assistant-*.png`; commit `feat: assistant answer area with demo answers`.

### Task 10: Performance: release build, Baseline Profile, benchmarks

**Files:** `baselineprofile/build.gradle.kts`, `BaselineProfileGenerator.kt`, `StartupBenchmark.kt`, `ScrollBenchmark.kt`; `docs/perf.md`.

- [ ] **Step 1:** Generator journey: cold start (skip boot via intent extra `skipBoot=true`), hold DPAD_DOWN to last row, DPAD_RIGHT ×10 in two rows, open/close Search.
- [ ] **Step 2:** `./gradlew :app:generateBaselineProfile` (emulator `bittv_gtv34`) → `app/src/release/generated/baselineProfiles/baseline-prof.txt` exists.
- [ ] **Step 3:** Run `StartupBenchmark` (COLD, 10 iterations) and `ScrollBenchmark` (FrameTimingMetric) with `CompilationMode.Partial()` vs `None()`.
- [ ] **Step 4:** Write `docs/perf.md` with the numbers against spec §7 budgets, release APK size (`apkanalyzer apk file-size`), and PSS from `dumpsys meminfo com.bittv.home` after the scroll journey. Any budget miss → fix before commit.
- [ ] **Step 5:** Commit `perf: baseline profile and benchmarks`.
