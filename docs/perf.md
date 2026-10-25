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
   in plan 2 is a single bitmap per tile) and the ambient background layers.
