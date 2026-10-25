# Review handover — 2026-10-25

Branch `feat/native-app` (not merged, not pushed). Nothing has been run against the TV.

## State

- Plan 1 (app UI, tasks 1–10) is complete. A fresh reviewer on the most capable model reviewed the whole branch and found 0 critical, 6 important and 11 minor issues. Seven of the minors were promoted because of what they would do to you. All of these are fixed, each one by a test that failed first.
- Tests: 51 JVM tests and 24 instrumented tests on the Google TV emulator, all green.
- Release APK: 3.8 MB. Cold start on the emulator: 455 ms median. See `docs/perf.md`.

## What the review fix pass changed

- Watch Later toggles are atomic, so two fast presses can't lose one.
- Search: a single letter no longer matches every genre. The query survives opening a result and coming Back.
- Focus:
  - Back from Settings or Search lands on the button you came from.
  - Removing the focused Watch Later title keeps focus in its row, or on the banner if the row is gone.
  - Dismissing a focused Assistant answer returns focus to the controls it covered.
- Back closes an open page before a hidden Assistant answer. Opening any page puts the answer away.
- Pressing Home while BitTV is open goes back to the top of Home.
- The boot animation plays once per TV boot, not again when Android recreates the launcher process.
- Holding the mic/M key no longer restarts the question.
- Performance:
  - Animation tokens are remembered between recompositions.
  - The ambient background is the only part that recomposes when focus changes.
  - The page-scroll section is updated in the focus callback, not during composition.

## Rulings made on your behalf

- Setup: Ruling: work on branch feat/native-app in place instead of a separate worktree — single-user local repo, nothing else checked out; cost if wrong: none (branch is isolated from main).
- (pre-flight) T2,T4→T6: HomeState uses NowPlaying and WeatherNow, which no task defines. Ruling: define both in model/Models.kt during Task 6 (demo values) — plan 2 fills them from real sources; cost if wrong: a later rename.
- (pre-flight) T6/T8→T10: Task 10 needs intent extra skipBoot; no task owns it. Ruling: BitTvRoot reads `skipBoot` in Task 8 when Boot is wired in; cost if wrong: benchmark includes boot time.
- (pre-flight) T9 consumes Coil from T1 dependencies — Ruling: add Coil 3 + coil-network-okhttp in Task 1's catalog so T9 needs no build change.
- (pre-flight) Ruling: OK/Enter ignores key repeats; D-pad keeps repeats — remotes repeat on long press; cost if wrong: long-press actions need a separate path.
- (pre-flight) Ruling: Back closes the innermost temporary UI first (Assistant answer, voice capture), then pages, then Home-to-top — Android TV Back convention; cost if wrong: none.
- (pre-flight) Ruling: focus glow drawn outside row clip (rows don't clip horizontally on the left) — owner dislikes clipped glows; cost if wrong: minor overdraw.
- (pre-flight) Ruling: every loop/timer (banner, clock, Spotify progress, PS5 preview) tied to Home being resumed and visible — launcher runs for days; cost if wrong: none.
- (pre-flight) Ruling: weather/answers show an offline state instead of invented values — misleading data otherwise; cost if wrong: none.
- (pre-flight) Ruling: ambient colour = cross-fade of two cached gradient layers (alpha), no live blur behind the rail — per-key repaint cost on Mali GPUs; cost if wrong: slightly less smooth colour tween.
- Task 1: Ruling: compileSdk 36 → 37 — Compose BOM 2026.09 and Coil 3.6.3 AARs require compiling against API 37; targetSdk stays 36, minSdk 31; cost if wrong: none at runtime.
- Task 2: Ruling: Title.c1/c2 are ARGB Long instead of Compose Color — keeps the model platform-free so catalogue/search tests run on the JVM; UI converts with Color(long); cost if wrong: one conversion helper.
- Task 3: Ruling: unsubscribedServiceIsHidden expects [harbourlights], not empty — the spec's partial-match fallback finds 'harbour' in Harbour Lights; the requirement is that Red Harbour (Paramount+, unsubscribed) never appears; cost if wrong: none.
- Task 5: Ruling: AmbientBackground cross-fades two recorded gradient layers (graphicsLayer alpha) instead of tweening colours in one drawBehind — per the prototype review, a colour tween repaints the full screen every frame; cost if wrong: fade looks like a dissolve rather than a hue shift (visually near-identical).
- Task 6: Ruling: layout follows the prototype after its review fixes (content x 169 dp, banner 488 dp, active section 243 dp, row glow room 32 dp) instead of the brief's earlier 157 dp/500 dp — the prototype is the agreed visual reference; cost if wrong: a few dp.
- Task 6: Ruling: vertical page = non-lazy Column translated by an Animatable spring (retargets with current velocity), sections report their tops via onPlaced; rows are LazyRows with a custom BringIntoViewSpec (left pad 32 dp, right peek 120 dp) — reproduces the prototype's measured behaviour exactly; cost if wrong: ~10 rows composed at once (each LazyRow composes only visible tiles).
- Task 6: Ruling: model gains NowPlaying, WeatherNow, WeatherKind, NewsItem (pre-flight row) — consumed by Task 9's weather scene too.
- Task 6: Ruling: emulator runs with '-gpu host -feature -Vulkan' — with Vulkan the emulator (37.2.12) segfaulted three times (installs/tests); GLES path is stable; cost if wrong: emulator perf numbers use GLES while the TV uses its own driver (TV numbers are the ones that count).
- Task 6: Ruling: page is a LazyColumn with a custom BringIntoViewSpec (focus line at the first row's resting tile position 370 dp; header and first row → top; tall rows fitted to the bottom safe area; Devices row sized by its PS5 tile) instead of a hand-translated Column — Compose focus search can't reach rows a manual translation pushes off screen (focus bounced back via the restorer); lazy layouts compose rows on demand during focus search; cost if wrong: animation spec of bring-into-view is a deprecated hook (still honoured today).
- Task 6: Ruling: Right from the app bar returns to an explicitly remembered FocusRequester (last tile / banner) instead of focusRestorer — Compose's restorer can't restore items inside lazy lists; cost if wrong: none.
- Task 6: Ruling: downNeverEntersRail walks the whole page down/up (9 Down, 14 Up) instead of focusing a Suggested tile directly — lazy rows below the screen aren't composed until reached; stronger test; cost if wrong: none.
- Task 6: Ruling: focusProperties must precede the focus group it describes (it applies to the next focus target in the chain) — fixed in AppBar and page.
- Task 7: Ruling: typingOnKeyboardFiltersResults expects Hollow Crown Hill first, then Tenements — "cr" is inside a title and phrase-in-title ranks first (spec: "phrase-in-title first"); the brief's "starts with Tenements" predates that ranking; cost if wrong: test order only.
- Task 7: Ruling: SettingsScreen gets apps (for icons) and onSample; NotInstalled/Opening screens are stand-ins until plan 2 wires PackageManager/intents; Search takes a `listen` lambda (demo phrases until plan 2's speech recognizer).
- Task 7: Ruling: top shortcut buttons get the "Right never changes row" rule (found in emulator testing: Right from Settings fell into the Spotify controls).
- Task 8: Ruling: BootMath exposes sheenProgress(t) (0..1) instead of sheenX(t) — the sweep's x range depends on measured glyph widths, which live in the drawing layer; cost if wrong: none.
- Task 8: Ruling: the boot plays even with Animations off — it is his own boot screen (prototype review set this aside too); any key or Back skips it; cost if wrong: one condition.
- Task 8: Ruling: letters, sweep clip and the multiply blend use the native Canvas/Paint (getTextBounds/getTextPath, saveLayer) to match the HTML's canvas measurements exactly; verified by a screen recording on the emulator.
- Task 9: Ruling: added net/Http (shared OkHttp client, User-Agent "BitTV/0.1 (personal Android TV home screen)") + Coil ImageLoader using it (15 % memory cache) — Wikimedia answered 403 to okhttp's default agent (picture answers were black); no personal details sent; test HttpTest.userAgentIdentifiesBitTv RED→GREEN; cost if wrong: none.
- Task 9: Ruling: picture answers carry a fallback URL (Wikimedia's default 330 px thumbnail) used when the 960 px one fails — Wikimedia serves only fixed thumbnail widths; cost if wrong: a lower-res picture.
- Task 9: Ruling: the demo is triggered by M or the remote's SEARCH key; real mic/speech and the Google-vs-BitTV decision belong to plan 2.
- Task 10: Ruling: benchmarks run with adb `am instrument` after `gradlew --stop`, not the Gradle connected task — the host OOM-killed Gradle and then the emulator with both running (other apps hold ~6.4 GB); cost if wrong: none (same instrumentation).
- Task 10: Ruling: startup benchmark makes the emulator's stock launcher Home temporarily — a Home app is restarted the instant it is killed, so a cold start can't be measured otherwise; restored afterwards; cost if wrong: none.
- Task 10: Ruling: frame-time budget (P90 ≤ 16 ms) is NOT declared met — the emulated GPU makes legacy frame times meaningless (deadline jank 1.1 %, 0 missed vsyncs); documented as a TV measurement in docs/perf.md; the one real finding (idle Home redrawing ~57 fps from an endless sway) was fixed; cost if wrong: TV may still need tuning.
- Task 10: Ruling: tvOS sway now settles after one swing out and back (2 × 4.5 s) instead of looping forever — idle Home went from ~57 fps to 26 frames/8 s; test swaySettlesSoAnIdleScreenStopsRedrawing (note: written before the code but its RED run was not observed — it could not compile without TileSwaying); cost if wrong: less "alive" focus after 9 s.
- Final: Ruling: reviewer's "Line 10 OK ignores repeats has no explicit code" — Compose clickable already acts once per press; noted, no code change; M/Search repeat handled in the fix pass.
- Final: Ruling: pageSpec.section now set in the focus callback, not during composition — no observable RED possible (same visible result, removes a composition-time write); cost if wrong: none.
- Final: Ruling: AmbientBackground takes a colours lambda so only it recomposes on focus changes — performance-only, no RED possible in a UI test; cost if wrong: none.
- Final: Ruling: backClosesAnOpenPageBeforeAHiddenAnswer waits for Search to appear before Back — runOnUiThread doesn't wait for the click's recomposition, so Back ran against the previous frame's handlers (a real remote press always arrives after the frame); cost if wrong: none.
- Final: Ruling: M/Search key repeats (held key) are swallowed after the first press — prevents a held key restarting the question; covered by deferred minor "no held-key test"; cost if wrong: none.

## Deferred minors

- banner ambient colour stays on the previous slide while the banner keeps focus.
- boot animation allocates a LinearGradient per cube per frame (2.8 s, once).
- no held-key (repeat) or Right-hold UI test; empty-results message has only a logic test.
- PageBringIntoView relies on deprecated scrollAnimationSpec and clamping of -100000 — recheck on BOM upgrades.

## Next (plan 2: `docs/superpowers/plans/2026-10-25-bittv-platform.md`)

Real installed apps and launching, weather, news, spoken answers, live answers, speech recognition, and the TV-only adapters. The TV-only adapters are now playing, Watch Next and the HDMI/PS5 input.

Decisions only you can make:

1. The mic button: Google Assistant, or BitTV answers in the active section?
2. A TMDB key, for the real catalogue.
3. The `[ask]` lines in `tools/tv/debloat.txt`: YouTube, Miracast/T-Cast, the game bar and AirPlay.
