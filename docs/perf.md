# BitTV performance — measurements

Measured 2026-10-25 on the **Google TV API 34 emulator** (x86, 2 GB RAM, host GPU via GLES, Vulkan off) with a
release build (R8, resource shrinking) and the generated Baseline Profile. The emulator is not the TV: its GPU is
emulated, so frame times are only indicative. **Repeat these on the TCL before calling any budget met.**

| Metric (spec §7) | Budget | Emulator result | Status |
|---|---|---|---|
| Cold start to Home's first frame (boot skipped) | ≤ 900 ms emulator / ≤ 1.2 s TV | **455 ms** median with the Baseline Profile (414–538); 520 ms without it | Met on emulator; TV to measure |
| Release APK size | ≤ 12 MB | **3.8 MB** | Met |
| Memory (PSS) after browsing every row | ≤ 180 MB | **69 MB** (Java 12 MB, native 30 MB, code 12 MB) | Met on emulator |
| Frames while browsing (hold Down/Right) | P90 ≤ 16 ms, P99 ≤ 33 ms | Deadline-based jank **1.1 %** (`gfxinfo`), 0 missed vsyncs; legacy frame time P50 23 ms / P90 30 ms / P99 48 ms | Not judgeable on the emulated GPU; TV to measure |
| Idle Home (nothing pressed) | — | **26 frames in 8 s** (the banner's cross-fade) after the change below; was ~57 frames/s | Fixed |

## What changed because of these numbers

- **The focus sway now settles.** tvOS-style lift and sway play one 4.5 s swing out and back after an item gains
  focus, then rest. Before, the focused item swayed forever, so Home redrew ~57 times a second even when nobody was
  touching the remote — wasted GPU work and heat on a TV (Apple TV's parallax follows the thumb; it does not loop).
- **Baseline Profile** (17k rules, generated from: cold start → down every row → along two rows → Search and back)
  cut cold start by ~13 % (520 → 455 ms median).

## How to reproduce

```bash
cd android
./gradlew :app:generateBaselineProfile                       # writes app/src/release/generated/baselineProfiles/
./gradlew :app:assembleBenchmarkRelease :baselineprofile:assembleBenchmarkRelease
./gradlew --stop                                              # the emulator needs the memory
adb -s emulator-5554 install -r app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk
adb -s emulator-5554 install -r -t baselineprofile/build/outputs/apk/benchmarkRelease/baselineprofile-benchmarkRelease.apk
# a launcher is restarted the moment it is killed, so for a true cold start make the stock launcher Home first
adb -s emulator-5554 shell pm enable com.google.android.apps.tv.launcherx
adb -s emulator-5554 shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
adb -s emulator-5554 shell am instrument -w -e class com.bittv.home.baselineprofile.StartupBenchmark \
  -e androidx.benchmark.suppressErrors EMULATOR -e androidx.benchmark.enabledRules Macrobenchmark \
  com.bittv.home.baselineprofile/androidx.test.runner.AndroidJUnitRunner
# … same with ScrollBenchmark; then put BitTV back as Home
adb -s emulator-5554 shell cmd package set-home-activity com.bittv.home/.MainActivity
adb -s emulator-5554 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

## Still to do on the TV (with Ludovico)

1. Cold start and warm return to Home (target ≤ 1.2 s / ≤ 300 ms).
2. `dumpsys gfxinfo com.bittv.home` while holding Down and Right on Home: P90 ≤ 16 ms.
3. PSS after an evening of use (a launcher runs for days).
4. If frames miss on the MediaTek GPU, the first candidates are the demo posters' layered gradients (real artwork
   in plan 2 is a single bitmap per tile), the ambient background layers, and each row's offscreen layer for the
   fade next to the app bar (`fadeIntoGap` in `Rows.kt`, 2–3 rows on screen); the fade can become a plain clip.

## TCL TV — Home navigation (2026-10-25)

TCL 55C6K-UK: MediaTek MT5896, Mali-G52 (GLES 3.2, Skia GL), UI 1920×1080 upscaled to the 4K panel, 2.4 GB RAM.
Release builds, measured with `tools/tv/perf-nav.sh 192.168.0.175:5555` (4× Right, 4× Left, Down, Up from the banner).
Phases are framestats averages in ms; "swap" is eglSwapBuffers waiting for the GPU.
Target: P50 ≤ 17 ms, P90 ≤ 25 ms, layout/record ≤ 10 ms.

| Step | Frames | P50 | P90 | input+anim | layout/record | issue | swap (GPU) | total |
|---|---|---|---|---|---|---|---|---|
| Baseline (5938a85), process running for hours | 22 | 85 | 117 | 2.8 | 15.1 | 11.9 | 54.4 | 97.3 |
| Baseline, fresh start (2 runs) | 21 / 19 | 93 / 81 | 117 / 125 | 7.0 / 5.2 | 11.2 / 10.9 | 10.1 / 9.6 | 52.2 / 53.5 | 104.6 / 97.5 |
| Background drawn once, as the window background (2 runs) | 24 / 27 | 61 / 57 | 69 / 77 | 7.3 / 6.0 | 4.9 / 5.4 | 9.0 / 10.5 | 28.9 / 29.2 | 64.4 / 62.0 |
| *Tried:* row fade as one overlay strip, no row layers (2 runs) — **reverted** | 27 / 23 | 77 / 73 | 101 / 97 | 5.7 / 5.3 | 9.0 / 9.0 | 14.8 / 14.4 | 37.2 / 38.3 | 82.9 / 81.9 |
| *Tried:* row layer kept, mask removed (2 runs) | 28 / 26 | 61 / 57 | 77 / 73 | 6.5 / 5.2 | 4.9 / 5.5 | 8.9 / 9.0 | 28.2 / 29.6 | 63.3 / 60.9 |

**Finding:** the rows' offscreen layers (`fadeIntoGap`) are render caches, not a cost: Android only re-renders a
layer whose content changed and otherwise copies its texture. Without them every unchanged row was redrawn tile by
tile; the mask itself costs nothing measurable. So the fade and the rail's layer stay, and the remaining GPU time is
in re-rendering the *focused* row's layer — i.e. its tiles' own drawing (posters).
