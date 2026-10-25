# Putting BitTV on the TV — prepared, not run

Nothing in this folder has been run against the TV. Every script talks to exactly one device that you name, and
**only prints what it would do** unless you add `--apply`. All three were tested end to end on the Google TV
emulator (`emulator-5554`): dry run, apply, and restore.

| Script | What it does | Undo |
|---|---|---|
| `install.sh <device> [--apply]` | Installs the release build, opens it, turns off the Google setup app's HOME claim and the stock Google TV home, makes BitTV Home | `restore.sh` |
| `debloat.sh <device> [--apply]` | Disables (never uninstalls) the packages in `debloat.txt`; saves the package state first | `restore.sh` |
| `restore.sh <device> [--apply]` | Re-enables everything in `debloat.txt` and the stock home, and makes it Home again | — |

A factory reset also undoes everything (disabled packages come back).

## Before we start (together)

1. On the TV: Settings → System → About → press OK on **Android TV OS build** seven times → back → Developer
   options → **USB debugging** on, then **Wireless debugging** on → "Pair device with pairing code".
2. On this computer (adb is in `~/Android/Sdk/platform-tools`):
   ```bash
   adb pair 192.168.0.175:<pairing port>     # type the 6-digit code shown on the TV
   adb connect 192.168.0.175:<debug port>
   adb devices                               # the TV should be listed
   ```
3. Build the release app: `(cd android && ./gradlew :app:assembleRelease)`.

## Then, in this order

```bash
tools/tv/debloat.sh 192.168.0.175:<port>            # read the list of what would change
tools/tv/debloat.sh 192.168.0.175:<port> --apply
tools/tv/install.sh 192.168.0.175:<port>            # read it
tools/tv/install.sh 192.168.0.175:<port> --apply
```

## Decisions (2026-10-25)

- YouTube: **disabled** (SmartTube replaces it). Remap the remote's YouTube button to SmartTube with Button Mapper.
- Miracast / TCL T-Cast: **kept**. AirPlay: **kept** (iPhones cast through it).
- Still open: YouTube Music, and the **game bar**. The game bar is TCL's overlay that appears when a console is
  detected on HDMI: it shows the game mode, VRR, ALLM and frame rate, and holds picture shortcuts. It is kept by
  default because the PS5 uses VRR/ALLM.

Kept on purpose: Google Assistant (`katniss`, the mic button), Chromecast, Miracast/T-Cast, AirPlay, HDMI/Live TV (`com.tcl.tv`), Netflix,
Prime Video, the UK catch-up apps, Play Store.

## Known quirks on this TCL platform (from cleanyourtv's 55C7K-UK notes)

- `com.tcl.ttvs` is also the firmware updater: while it is disabled there are no system updates. To update:
  `adb shell pm enable com.tcl.ttvs`, update, then disable it again.
- If the Wi-Fi network was saved during the TV's first setup, Google's setup app owns it; `install.sh` stops and
  asks you to forget and re-join the Wi-Fi first, otherwise the TV could drop off the network.
- With the stock home off, the remote's Netflix/YouTube buttons need a small helper (accessibility service) to keep
  working — planned for plan 2 together with the mic button decision.
