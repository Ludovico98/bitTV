# The TV: TCL 55C6K-UK — what BitTV is built and tested against

Checked 2026-10-25 without touching the TV: from what it announces on the home network (Google Cast and AirPlay
records, read passively) and from retailer spec sheets.

| | |
|---|---|
| Make / model | **TCL 55C6K-UK** (AirPlay record `manufacturer=TCL`, `model=55C6K-UK`); Google Cast name "Smart TV Pro" |
| OS | Google TV 14 → Android API 34 (the emulator image BitTV is tested on is API 34 Google TV) |
| Chip / memory | MediaTek MT9653 quad-core ("AiPQ Pro"), **2 GB RAM** (the emulator also has 2 GB) |
| Panel | 4K QD-Mini LED, 144 Hz (game mode) |
| Network extras it offers | Google Cast, AirPlay (kept in `tools/tv/debloat.txt`) |

## 4K: why BitTV's interface stays at 1080p

On TVs like this the Android interface is drawn at 1080p and the TV scales it to the 4K panel; video from Netflix,
Prime Video and the others is decoded on a separate plane and stays true 4K. Drawing BitTV itself in 4K would mean
4× the pixels for every animation, shadow and fade on a small Mali GPU — the opposite of what BitTV is for — so it
is **not done**. What was done instead: BitTV lays out on a fixed 960×540 dp canvas (`ui/theme/TvCanvas.kt`), so it
looks the same whatever size and density the TV reports (1080p @ 320 or 240, or a 4K UI @ 640 or 480 — all tested).

## What the TV tests cover (`android/app/src/androidTest/…/ui/TvReadinessTest.kt`)

- Real remote key events through Android's input system (not test shortcuts), with the window out of touch mode as on
  a TV: D-pad, OK (and keyboard Enter), Back on a page and on Home.
- Holding Down/Up (auto-repeat): glides through rows, never enters the app bar, comes back to the top.
- First focus: always the banner, even if a key is pressed while BitTV is still appearing (found and fixed by these
  tests: focus could land on the app bar, or a held key could be pulled back to the banner).
- Launcher contract: BitTV answers HOME and LEANBACK_LAUNCHER, needs no touchscreen, has a TV banner.
- Overscan: the app bar, banner and top buttons sit inside the 5 % safe area.
- Memory after browsing every row: under the 180 MB budget for a 2 GB TV.
- `TvDisplayConfigTest`: same layout at 1920×1080 @ 320/240/213 and 3840×2160 @ 640/480.

## To confirm on the TV itself (read-only, together, when we connect)

```bash
adb -s <tv> shell wm size; adb -s <tv> shell wm density          # what the TV reports (any value now works)
adb -s <tv> shell dumpsys display | grep -E "mode|DisplayDeviceInfo" # UI vs panel resolution
adb -s <tv> shell getprop ro.product.model; getprop ro.build.version.sdk
```

Then the measurements in `docs/perf.md` ("Still to do on the TV").

Known TV-only item for plan 2: the remote's mic button is taken by the system (Google Assistant) before any app
sees it; BitTV answering in the active section needs the helper planned in plan 2 (Button Mapper / accessibility).
