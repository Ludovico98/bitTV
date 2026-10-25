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

## TV settings changed (2026-10-25, with Ludovico)

| Setting | Was | Now | Why | Undo |
|---|---|---|---|---|
| `settings global tcl_app_livetv_auto_standby` | `HDMI1,HDMI2,HDMI3,HDMI4,AV1` | empty | TCL put the PS5 to rest (CEC `<Standby>` 04:36) whenever you left its input, e.g. pressing Home | `adb shell settings put global tcl_app_livetv_auto_standby HDMI1,HDMI2,HDMI3,HDMI4,AV1` |
| `com.google.android.apps.tv.launcherx` | disabled by install.sh | **enabled** | Google Assistant refuses to work without it ("not available in this profile yet") | — |

Found on the TV: browser `com.tcl.browser`; media player `com.tcl.ui_mediaCenter` (TCL Media Center: USB and network
videos, music, pictures); HDMI inputs open in `com.tcl.tv` (Live Channels also claims them, hence the chooser before).
The HDMI preview used to send CEC routing when it connected; it now tunes with TCL's `isCecDeviceDisable` (see below)
and only for a device the TV reports as on.

## The Home helper (2026-10-25)

BitTV's `HomeKeyService` keeps BitTV as the home screen while Google's home stays enabled. On this TCL it only starts
when switched on in the TV's own menu (Settings → Accessibility → BitTV Home button), after allowing restricted
settings for BitTV (`cmd appops set com.bittv.home ACCESS_RESTRICTED_SETTINGS allow`). Writing the setting over adb
lists it as enabled but the system never binds it. Verified: Home from TV Settings → BitTV.

## Google Assistant: "Sorry, I'm not available in this profile yet" (2026-10-25)

Google TV's home (launcherx) and Google TV Setup (setupwraith) are enabled again — the Assistant needs both.
The message persisted with both enabled, after restarting the Assistant app: the remaining known cause is Google TV's
**Apps only mode** (TV settings → Accounts & sign-in → the Google account), which switches the Assistant off.

## Learned 2026-10-25 (performance and standby work)

- **Updating BitTV with `adb install -r` can drop the Home helper** from `enabled_accessibility_services` (seen after
  one of many installs this day); `settings put` re-lists it but the TCL does not bind it — switch it on again in
  Settings → Accessibility after installing.
- **Standby:** going to sleep puts launcherx's `DispatchActivity` on top (BitTV is stopped while the TV is already
  non-interactive); waking returns to Home and resumes BitTV ~200 ms *before* `SCREEN_ON` is delivered.
- **HDMI input state can be stale after a wake:** the TCL removes and re-adds the PS5's input on wake and hotplug; a
  re-added input reads CONNECTED (the default) until the CEC power status arrives 1–2 s later. BitTV trusts an input's
  state only 10 s after it was added (`InputSettle`).
- **Why the PS5 woke and slept "on its own" (CEC log, `dumpsys hdmi_control`, 2026-10-25):** every TvView session on
  the PS5's input made TCL's `TvPassThroughSession.onTune` run `HdmiTvClient.deviceSelect` → `<Set Stream Path>` to
  2.0.0.0, which wakes the PS5; the PS5 then announced `<Active Source>` and TCL launched `com.tcl.tv/.TVActivity`
  over Home. Releasing the session (or Home taking over) ran `deviceSelect(0)` + TCL's auto-switch standby →
  `<Standby>` to the PS5. The TCL session skips both when the tune params carry `isCecDeviceDisable=true` (read from
  `TIF_TvInput.apk` with dexdump); BitTV's preview now always passes it (`HdmiPreview.TUNE_FLAGS`). Verified on the
  TV: Home and the Devices row with the PS5 in standby send no CEC. Not yet seen: the live picture of a PS5 that is
  on with the flag set.
- Compose treats *any* enabled accessibility service (here Button Mapper and the Home helper) as a reason to diff
  semantics every frame; `ComposeA11y` turns that off unless a screen reader is on.
- Build on this machine with `JAVA_HOME=~/.local/jdks/jdk-21.0.13+9` (the system JDK 21 is a JRE without javac);
  the emulator needs `-feature -Vulkan -gpu host` (it segfaults with Vulkan).
