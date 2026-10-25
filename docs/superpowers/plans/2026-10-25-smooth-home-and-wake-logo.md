# Smooth Home and Wake Logo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home navigates at ~60 fps on the TCL TV with the same look, and waking the TV from standby plays the BitTV logo and then shows Home.

**Architecture:** Remove per-frame GPU work (offscreen mask layers, full-screen gradients, layered poster gradients) by drawing the background once into a shared bitmap and painting the two fades as single-draw overlays from that bitmap; cut UI-thread recomposition. Wake becomes a persisted "asleep" flag set on `SCREEN_OFF` and consumed on wake, with the always-on `HomeKeyService` bringing BitTV forward.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00), Compose for TV, Coil 3.6.3, Android 14 (TV, API 34), JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-25-smooth-home-and-wake-logo-design.md`

## Global Constraints

- The look must not change: gap fade (`ContentX + 10 dp` → `ContentX + RowStartPad`), rail fade (17 px at each end), poster bottom shade (`Transparent → Black 60 %` from 40 % height), banner glow untouched.
- TV targets while navigating: P50 ≤ 17 ms, P90 ≤ 25 ms; UI-thread phases ≤ 10 ms average.
- Release builds only for TV measurements (`./gradlew :app:assembleRelease`, installed with `tools/tv/install.sh` path or `adb install -r`).
- TV is `192.168.0.175:5555`; emulator is `emulator-5554`. Test on the emulator before installing on the TV.
- Code style: match surrounding files (dense one-line comments explaining *why*, Kotlin one-liners where the file uses them).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; no push, no merge.

## Review Focus

- Home scrolled so the banner is half off-screen: the gap strip must start at the hero item's bottom edge, not cover the banner glow → covered by `gapStripTop` tests (Task 3).
- Screen resized / density change (Settings → display): backdrop bitmap must be rebuilt for the new size → `HomeBackdrop` keyed on size (Task 2).
- Artwork that fails to load or has no `art`: placeholder gradients must still draw → `posterNeedsPlaceholder` tests (Task 5).
- SCREEN_ON without a prior SCREEN_OFF (screensaver wake, service restart): no logo → `WakeGate` test "consume without mark" (Task 8).
- Process killed during standby: logo still plays → `WakeGate` test "survives a new instance over the same store" (Task 8).

---

### Task 1: TV navigation perf script and baseline

**Files:**
- Create: `tools/tv/perf-nav.sh`
- Modify: `docs/perf.md` (new section "TCL TV — Home navigation" with a before/after table)

- [ ] **Step 1:** Write `tools/tv/perf-nav.sh [device]` (default `192.168.0.175:5555`, uses `lib.sh`'s conventions where useful): `dumpsys gfxinfo com.bittv.home reset`; keys `22`×4, `21`×4 at 0.4 s, `20`, `19` at 0.7 s; then print `Total frames`, `Janky frames`, P50, P90 and the average framestats phases: input+anim (`PerformTraversalsStart−HandleInputStart`), layout/record (`SyncStart−PerformTraversalsStart`), issue (`SwapBuffers−IssueDrawCommandsStart`), swap (`FrameCompleted−SwapBuffers`), total (`FrameCompleted−IntendedVsync`); only rows with Flags = 0.
- [ ] **Step 2:** Run it on the TV against the installed build. Expected: P50 ≈ 89 ms, swap ≈ 55 ms (within noise of the spec baseline).
- [ ] **Step 3:** Add the baseline row to `docs/perf.md` and commit (`chore(tv): perf-nav script and the TV baseline`).

### Task 2: Background drawn once, shared as a backdrop

**Files:**
- Modify: `app/src/main/java/com/bittv/home/ui/components/HomeBackground.kt`
- Modify: `app/src/main/java/com/bittv/home/MainActivity.kt` (window background)

**Interfaces:**
- Produces: `class HomeBackdrop(val bitmap: ImageBitmap, val isWindowBackground: Boolean)` with `fun shader(): Shader` (a `BitmapShader` CLAMP/CLAMP, screen coordinates = bitmap coordinates); `val LocalHomeBackdrop = staticCompositionLocalOf<HomeBackdrop?> { null }`; `@Composable fun rememberHomeBackdrop(size: IntSize): HomeBackdrop?` (null until size is known; rebuilt when `size` changes).
- `HomeBackground(modifier)` keeps its signature and draws `LocalHomeBackdrop.current` bitmap with `drawImage`; falls back to today's gradients if null.

- [ ] **Step 1:** Implement `rememberHomeBackdrop`: draw the same three radial gradients (unchanged colours/centres/radii from today) into an `ImageBitmap(w, h, ARGB_8888)` via `CanvasDrawScope`; `remember(size)`.
- [ ] **Step 2:** Provide `LocalHomeBackdrop` in `HomeScreen` from the window's container size (`LocalWindowInfo.current.containerSize`), around the whole Home `Box`.
- [ ] **Step 3:** Remove the duplicate full-screen pass: in `MainActivity`, once the backdrop exists (pass a callback `onBackdrop: (ImageBitmap) -> Unit` from `HomeScreen` up through `BitTvRoot`, or hoist `rememberHomeBackdrop` into `MainActivity.setContent`), set `window.setBackgroundDrawable(BitmapDrawable(resources, bitmap.asAndroidBitmap()))`, and `HomeBackground` then draws nothing (`LocalHomeBackdrop.current?.isWindowBackground == true`). Screens that paint their own background are unaffected; the theme keeps `@color/bg` for the starting window. Prefer hoisting into `setContent` (fewer parameters).
- [ ] **Step 4:** `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` on `emulator-5554` → all green. Screenshot Home on the emulator (`adb exec-out screencap -p`) and compare with one taken before the change: no visible difference.
- [ ] **Step 5:** Release build on the TV, `tools/tv/perf-nav.sh`; add a row to `docs/perf.md`. Commit (`perf(home): background drawn once into a shared backdrop bitmap`).

### Task 3: Row gap fade as one overlay strip

**Files:**
- Modify: `app/src/main/java/com/bittv/home/ui/home/Rows.kt` (remove `fadeIntoGap`, `GlowBleed`, the `.fadeIntoGap()` call)
- Create: `app/src/main/java/com/bittv/home/ui/home/GapFade.kt`
- Modify: `app/src/main/java/com/bittv/home/ui/home/HomeScreen.kt` (draw the strip after the LazyColumn)
- Test: `app/src/test/java/com/bittv/home/ui/home/GapFadeTest.kt`

**Interfaces:**
- Consumes: `LocalHomeBackdrop`, `HomeBackdrop.shader()` (Task 2).
- Produces: `fun gapStripTop(heroBottomPx: Int?, contentTopPx: Int): Int` — `heroBottomPx` is the hero item's bottom in the LazyColumn's viewport (null when the hero item is not in `visibleItemsInfo`); returns `max(contentTopPx, heroBottomPx ?: contentTopPx)`.
  `fun Modifier.gapFade(listState: LazyListState, backdrop: HomeBackdrop?, gapStartX: Dp, fadeFrom: Dp, fadeTo: Dp): Modifier` — `drawWithContent { drawContent(); drawRect(ShaderBrush(ComposeShader(backdrop.shader() translated by the node's window offset, LinearGradient alpha 1→0 from fadeFrom to fadeTo, PorterDuff.Mode.DST_IN)), …) }`; reads `listState.layoutInfo` only inside the draw block.

- [ ] **Step 1: Write the failing tests** in `GapFadeTest`:
  `heroOnScreenStripStartsAtItsBottom`: `gapStripTop(heroBottomPx = 520, contentTopPx = 0) == 520`;
  `heroScrolledAwayStripStartsAtTop`: `gapStripTop(null, 0) == 0`;
  `heroAboveTheTopNeverMovesStripUp`: `gapStripTop(-40, 0) == 0`.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*GapFadeTest'` → FAIL (unresolved `gapStripTop`).
- [ ] **Step 3:** Implement `gapStripTop` and `gapFade` in `GapFade.kt`. The backdrop shader is offset by the strip node's `positionInWindow()` (captured via `onPlaced` into a plain field, no state) so the overlay matches the screen background exactly. Strip x-range: `[gapStartX, ContentX + RowStartPad)` in the LazyColumn's coordinates = `[0, RowStartPad)` since the LazyColumn starts at `ContentX`; fade from `10.dp` (opaque before it) to `RowStartPad` (transparent).
- [ ] **Step 4:** Tests pass; remove `fadeIntoGap`/`GlowBleed` from `Rows.kt` and apply `gapFade` on the LazyColumn modifier in `HomeScreen`.
- [ ] **Step 5:** Instrumented suite on the emulator green; emulator screenshots: tiles scrolled left fade into the gap exactly as before; banner glow not cut; focused first tile's glow fades the same way it did.
- [ ] **Step 6:** TV perf run + row in `docs/perf.md`. Commit (`perf(home): row fade drawn as one overlay strip, no offscreen layers`).

### Task 4: App bar fade without an offscreen layer

**Files:**
- Modify: `app/src/main/java/com/bittv/home/ui/home/AppBar.kt:106-117`

**Interfaces:**
- Consumes: `LocalHomeBackdrop`, `HomeBackdrop.shader()`.

- [ ] **Step 1:** Replace the `graphicsLayer { Offscreen }` + `DstIn` block with a `drawWithContent` that after `drawContent()` draws, for the top 17 px and the bottom 17 px: the backdrop (window-offset shader) and then `Color(0x9E161B26)`, each masked by the vertical alpha ramp (opaque at the edge → transparent at 17 px), clipped to a path of the rail's `RoundedCornerShape(22.dp)` outline inset by 1 dp (top corners for the top band, bottom corners for the bottom band).
- [ ] **Step 2:** `AppBarReorderTest` + full instrumented suite green on the emulator; screenshot with the rail scrolled (many apps): icons fade at both ends, border intact, corners not squared.
- [ ] **Step 3:** TV perf run + row. Commit (`perf(rail): edge fades as overlays, no offscreen layer`).

### Task 5: Posters drawn as one image

**Files:**
- Modify: `app/src/main/java/com/bittv/home/ui/components/PosterArt.kt`
- Create: `app/src/main/java/com/bittv/home/ui/components/ShadeTransformation.kt`
- Test: `app/src/test/java/com/bittv/home/ui/components/PosterArtTest.kt`

**Interfaces:**
- Produces: `fun posterNeedsPlaceholder(hasArt: Boolean, loaded: Boolean): Boolean` = `!hasArt || !loaded`;
  `class ShadeTransformation(private val cornerPx: Float) : coil3.transform.Transformation` with `cacheKey = "shade:0.4:0.6:$cornerPx"`; `transform` returns a new ARGB_8888 bitmap: the input drawn into a rounded rect of `cornerPx`, then the vertical gradient `Transparent → Black α 0.6` from 40 % height.
- `PosterArt` gains `cornerRadius: Dp = 9.dp` (Banner passes `17.dp`, Details/Search keep their shapes' radius).

- [ ] **Step 1: Write the failing tests**: `noArtAlwaysPlaceholder`: `posterNeedsPlaceholder(false, true) == true`; `loadingShowsPlaceholder`: `posterNeedsPlaceholder(true, false) == true`; `loadedHidesPlaceholder`: `posterNeedsPlaceholder(true, true) == false`; `cacheKeyCarriesTheRadius`: `ShadeTransformation(18f).cacheKey == "shade:0.4:0.6:18.0"` and differs from `ShadeTransformation(34f).cacheKey`.
- [ ] **Step 2:** Run → FAIL (unresolved).
- [ ] **Step 3:** Implement. `PosterArt` uses `AsyncImage(ImageRequest.Builder(ctx).data(tmdbImage(art, imageWidth)).transformations(ShadeTransformation(cornerPx)).build(), onState = { loaded = it is AsyncImagePainter.State.Success })`; the `drawWithCache` placeholder draws only when `posterNeedsPlaceholder(title.art != null, loaded)`; drop the image's `drawWithContent` gradient. Error state keeps the placeholder.
- [ ] **Step 4:** Unit + instrumented tests green; emulator screenshots (with a TMDB key, or the demo catalogue for placeholders): shade and corners identical to before.
- [ ] **Step 5:** TV perf run + row. Commit (`perf(posters): artwork baked with its shade and corners, placeholder only while loading`).

### Task 6: UI thread — rows stop recomposing on Up/Down; semantics hook debug-only

**Files:**
- Modify: `app/src/main/java/com/bittv/home/ui/home/HomeScreen.kt:201-215`
- Modify: `app/src/main/java/com/bittv/home/ui/components/FocusTile.kt:79`

- [ ] **Step 1:** Replace the content-lambda reads of `focusedSection` (`near`) with `val devicesNear by remember(rows) { derivedStateOf { val near = rows.indexOfFirst { it.key == focusedSection }; val dev = rows.indexOfFirst { it.key == "dev" }; dev >= 0 && abs(near - dev) <= 1 } }`, read by the items lambda; `animateDevices = motion.loops && resumed && devicesNear`.
- [ ] **Step 2:** In `FocusTile`, apply `.semantics { tileScale = scale; tileSwaying = swaying }` only `if (BuildConfig.DEBUG)`.
- [ ] **Step 3:** `HomeFocusTest`, `TvReadinessTest`, `ReviewFixesTest` (they read `tileScale`) and the rest of the instrumented suite green on the emulator (debug build).
- [ ] **Step 4:** TV perf run + row; check the layout/record phase is ≤ 10 ms average. Commit (`perf(home): rows skip recomposition on Up/Down; tile semantics only in debug`).

### Task 7: Conditional GPU steps (only if Task 6's TV numbers miss P50 ≤ 17 ms / P90 ≤ 25 ms)

**Files:** `ui/components/FocusTile.kt`, `ui/home/Banner.kt` as needed.

- [ ] **Step 1:** If missing: replace `Modifier.shadow(14.dp, …White)` on the focused tile with a cached pre-blurred glow bitmap (`BlurMaskFilter` on a software canvas, keyed by tile size and shape radius) drawn in `drawBehind`; TV perf run.
- [ ] **Step 2:** If still missing: Banner cross-fade fades only the image (`AsyncImage` alpha), text swaps at the midpoint; skip `clip(shape)` when art is baked; TV perf run after each.
- [ ] **Step 3:** Rows in `docs/perf.md` for each; commit each separately. If targets are still missed, record the remaining gap honestly in `docs/perf.md`.

### Task 8: Logo on wake from standby

**Files:**
- Modify: `app/src/main/java/com/bittv/home/platform/WatchLinks.kt` (move `WakeGate` out)
- Create: `app/src/main/java/com/bittv/home/platform/WakeGate.kt`
- Modify: `app/src/main/java/com/bittv/home/MainActivity.kt`
- Modify: `app/src/main/java/com/bittv/home/platform/HomeKeyService.kt`
- Test: `app/src/test/java/com/bittv/home/platform/WakeGateTest.kt`; remove `WakeGate` cases from `WatchLinksTest.kt`

**Interfaces:**
- Produces: `class WakeGate(private val read: () -> Boolean, private val write: (Boolean) -> Unit)` with `fun markAsleep()` and `fun consumeWake(): Boolean`; `fun Context.wakeGate(): WakeGate` backed by `getSharedPreferences("bittv_boot", MODE_PRIVATE)` key `"asleep"`; `MainActivity.ACTION_WAKE = "com.bittv.home.WAKE"`.

- [ ] **Step 1: Root cause.** Add a temporary `Log.i("BitTV.wake", …)` in MainActivity `onCreate/onResume/onNewIntent` and the screen receiver; install the debug build on the TV; run `adb shell input keyevent KEYCODE_SLEEP`, wait 10 s, `KEYCODE_WAKEUP` (ask Ludovico to press power if ADB drops). Capture `logcat -b events -b main` filtered to `BitTV.wake|wm_on_.*bittv|am_proc_.*bittv|screen_toggled|power_screen_state|set_resumed`. Write the observed order of events in the commit message of Step 6. If it matches none of the spec's three suspects, stop and revisit the design with Ludovico.
- [ ] **Step 2: Write the failing tests** (`WakeGateTest`, in-memory store `var v = false`): `consumeWithoutMarkIsFalse`; `markThenConsumeIsTrueOnce` (second consume false); `markTwiceIsOneWake`; `survivesANewInstanceOverTheSameStore` (mark on gate A, consume on new gate B over the same `v` → true).
- [ ] **Step 3:** Run → FAIL.
- [ ] **Step 4:** Implement `WakeGate` + `Context.wakeGate()`. MainActivity: the screen receiver listens to `SCREEN_OFF` only and calls `wakeGate().markAsleep()`; `onResume`: `if (wakeGate().consumeWake()) wakeSignal.intValue++`; `onNewIntent`/`onCreate` with `ACTION_WAKE`: consume the gate and play (in `onCreate`, `ACTION_WAKE` makes `skipBoot = false` regardless of `savedInstanceState`; the boot count gate is not touched). Remove `lastScreenOn` and the old time-window `WakeGate`. HomeKeyService: in `onServiceConnected` register a receiver for `SCREEN_OFF` (→ `markAsleep()`) and `SCREEN_ON` (→ if the stored flag is set, `startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_WAKE).addFlags(NEW_TASK or REORDER_TO_FRONT))`, leaving consumption to MainActivity); unregister in `onDestroy`.
- [ ] **Step 5:** Unit tests pass; instrumented suite green on the emulator; on the emulator: `KEYCODE_SLEEP` → `KEYCODE_WAKEUP` from Home and from another app → logo then Home with the banner focused; screensaver start/stop (`am start -a android.intent.action.MAIN -n com.android.systemui/.Somnambulator` or waiting) → no logo.
- [ ] **Step 6:** Remove the temporary logs; release build on the TV; repeat the sleep/wake test from Home and from Spotify (with Ludovico). Commit (`fix(wake): the logo plays on every wake from standby, BitTV brought to the front`).

### Task 9: Wrap-up

- [ ] **Step 1:** Final `tools/tv/perf-nav.sh` on the TV; `docs/perf.md` shows each step's row and the final result against the targets; replace the "Still to do on the TV" item 2/4 text with the outcome.
- [ ] **Step 2:** Full unit + instrumented suites green; commit (`docs(perf): TV navigation results`).
- [ ] **Step 3:** Whole-branch code review (superpowers:requesting-code-review), fix findings, re-run the suites.
