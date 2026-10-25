# Smooth Home navigation and the logo on wake — design

Date: 2026-10-25 · Branch: `feat/native-app` · Approved in chat by Ludovico the same day.

## Goal

1. Navigating Home on the TCL 55C6K-UK feels smooth: no visible stutter while moving between tiles and rows.
2. When the TV comes back from standby, the BitTV logo animation plays and then Home appears (top of Home, banner
   focused) — even if another app was in front before standby.

The look does not change: glows, the fade next to the app bar, the sway and the banner cross-fade all stay.

## Baseline (measured on the TV, release build, 2026-10-25)

Hardware: MediaTek MT5896, Mali-G52 (GLES 3.2, Skia GL pipeline), UI 1920×1080 upscaled to the 4K panel, 2.4 GB RAM
(88 MB free, 786 MB swap, ~13 streaming apps cached).

| Metric | Navigating (4× Right, 4× Left, Down, Up) |
|---|---|
| Janky frames | 100 % |
| Frame time P50 / P90 | 89 ms / 125 ms (~10 fps) |
| Avg phase: input+animation | 3.3 ms |
| Avg phase: measure/layout/record (UI thread) | 13.6 ms |
| Avg phase: issue draw commands | 10.1 ms |
| Avg phase: eglSwapBuffers (waiting on the GPU) | 54.8 ms |
| GPU scratch render targets cached | 107 (18.7 MB) |

Idle Home draws nothing (2 frames in 6 s): the cost is all in moving.

**Targets on the TV:** P50 ≤ 17 ms, P90 ≤ 25 ms while navigating; UI-thread phases ≤ 10 ms on average.
If the GPU fixes below get within reach but not under, the conditional steps (§3.5) follow.

## 1. Measuring

`tools/tv/perf-nav.sh [device]`: resets `gfxinfo`, sends the fixed key sequence above (0.4 s apart for Left/Right,
0.7 s for Down/Up), then prints frames, jank %, P50/P90 and the average phase breakdown from `framestats`.
Each fix is installed as a release build on the TV and measured with it; results go into `docs/perf.md` as a
before/after table (one line per step).

## 2. GPU: less work per frame (same pixels)

### 2.1 Background drawn once
`HomeBackground` renders its three radial gradients into an `ImageBitmap` the size of the screen once (per size)
and then draws that one bitmap. The bitmap is shared through a `LocalHomeBackdrop` so §2.2 and §2.3 can paint
matching pieces of it. The window background (`android:windowBackground`) is set to `@null` once Compose has drawn
its first frame, so the decor view stops filling the whole screen under Home on every frame (the theme keeps
`@color/bg` for the starting window).

### 2.2 Row fade without offscreen layers
`fadeIntoGap` (one `CompositingStrategy.Offscreen` layer + `BlendMode.DstIn` per row) is removed. Instead HomeScreen
draws, after the LazyColumn, one strip over the gap next to the app bar (x from `ContentX + 10 dp` to
`ContentX + RowStartPad`, the same ramp as today): the backdrop bitmap through a `BitmapShader` in screen
coordinates, multiplied by a horizontal alpha ramp (opaque at the app-bar side, transparent at the strip edge) via
`ComposeShader(…, DST_IN)` in a single `drawRect`. Tiles scrolled left into the gap therefore fade into the exact
background. The strip starts at the bottom edge of the hero item while that item is on screen (read from
`listState.layoutInfo` in the draw phase only, no recomposition) and at the top of the content otherwise, so the
banner's glow is never touched. The glow bleed hack (`GlowBleed` layout grow) goes with the layer.

### 2.3 App bar fade without an offscreen layer
The AppBar's `Offscreen` + `DstIn` mask is replaced by overlays at its top and bottom edge (the same 17 px ramp):
the backdrop bitmap (screen coordinates) and then the rail colour `0x9E161B26`, both under a vertical alpha ramp,
inside the rail's rounded outline (top corners at the top, bottom corners at the bottom) and 1 dp inside its border.

### 2.4 Posters: one image draw
- `PosterArt` draws its five placeholder gradients only while the artwork has not loaded (or when there is none).
- The bottom shade (`Transparent → Black 60 %` from 40 % height) is baked into the decoded bitmap by a Coil
  `Transformation`, together with the tile's rounded corners; the `drawWithContent` gradient on the image goes.
  The transformation key includes the corner radius and size so the cache stays correct.

### 2.5 Conditional (only if §5's numbers still miss the target after 2.1–2.4 and §3)
- The focused tile's white `Modifier.shadow(14.dp)` glow → a pre-blurred glow bitmap per tile size, drawn behind.
- Banner slide change: cross-fade only the artwork image; the text swaps at the midpoint (no layer for the slide).
- Skip `clip(shape)` on tiles whose artwork is baked with rounded corners.

## 3. UI thread

- HomeScreen's LazyColumn content stops reading `focusedSection` (today every row recomposes on each Up/Down).
  Each row gets `animateDevices` from a `derivedStateOf` that only changes when the Devices row's "near" status
  changes.
- `FocusTile`'s `semantics { tileScale; tileSwaying }` test hook is applied only when `BuildConfig.DEBUG`, so the
  release build stops invalidating semantics on every animation frame. Instrumented tests run debug builds and keep
  working unchanged.

## 4. Logo on wake from standby

### 4.1 Root cause first
Before changing code, capture one real standby → wake cycle on the TV (`logcat -b events` + main buffer filtered
to power, `wm_on_*` for BitTV, `am_proc_*`, and a temporary log line in `MainActivity.onResume`/the screen
receiver). Suspected (to confirm or refute): `SCREEN_ON` delivered after `onResume`, BitTV's process killed during
standby (then `savedInstanceState != null` skips the boot), or the TV resuming another app/input. The fix below
covers all three; if the evidence shows something else, the design is revisited before coding.

### 4.2 Fix
- `WakeGate` becomes a persisted flag, not a 5 s time window:
  `markAsleep()` on `SCREEN_OFF`, `consumeWake(): Boolean` (true once after a `markAsleep`), stored in the
  `bittv_boot` SharedPreferences so it survives the process being killed.
- `HomeKeyService` (always running while the Home helper is on) registers `SCREEN_OFF`/`SCREEN_ON`. On `SCREEN_OFF`
  it marks asleep. On `SCREEN_ON` (when marked) it starts `MainActivity` with `ACTION_WAKE`
  (`FLAG_ACTIVITY_NEW_TASK`), bringing BitTV in front whatever was open.
- `MainActivity`: `ACTION_WAKE` (onCreate or onNewIntent) or `consumeWake()` in `onResume` → plays the boot
  animation (wakeSignal; in onCreate the boot is not skipped even with `savedInstanceState`). The existing
  `wakeSignal` handling already returns to the top of Home and focuses the banner after the logo.
- Fallback when the Home helper is off: MainActivity's own receiver (registered in onStart) marks asleep on
  `SCREEN_OFF`, so the logo still plays if BitTV is what comes back.
- Screensaver (Dream) start/stop does not turn the screen off, so it never plays the logo.

### 4.3 Tests
- Unit tests for `WakeGate` (TDD): consume without mark → false; mark then consume → true once; mark twice →
  one wake; survives a new instance over the same store.
- On the TV: `adb shell input keyevent KEYCODE_SLEEP`, wait 10 s, `KEYCODE_WAKEUP` — once from Home and once from
  another app (e.g. Spotify); the logo plays then Home with the banner focused. CEC may also send the PS5 to
  standby; if network ADB drops while asleep, Ludovico presses power on the remote.

## 5. Verification

- `./gradlew :app:testDebugUnitTest` and the instrumented suite on `emulator-5554` stay green.
- `tools/tv/perf-nav.sh` on the TV after each step; final numbers meet the targets above (or the conditional
  steps are applied and the remaining gap reported honestly).
- Visual check against today's look on the TV: the gap fade, the rail fade at both ends, poster shade and corners,
  banner glow untouched.

## Out of scope

Killing cached background apps (would make Netflix & co. start cold); changing TV settings such as the second
key-filtering accessibility service `flar2.homebutton` (suggested to Ludovico only); lowering the UI resolution.
