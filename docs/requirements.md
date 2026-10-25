# BitTV — requirements gathered so far

Everything Ludovico has asked for, in his words where possible, grouped by area. This is the input to the design spec
(`docs/superpowers/specs/`). Last updated 2026-10-25.

## Goal

- A custom home screen ("BitTV") for his own TCL 55C6K-UK Google TV (Google TV 14, model name "Smart TV Pro").
  For personal use only; never published.
- "Smooth and functional", "fast and snappy like the Apple TV", "professional and well performing".
- No ads. The time matters to him.
- Follow Apple's tvOS Human Interface Guidelines for look and feel.

## Not an OS

- Agreed: BitTV is a home screen app (launcher). The stock Google TV home is disabled over ADB, BitTV takes Home.
  A real custom OS is not possible (locked bootloader; streaming apps need a certified device for HD/DRM).
- Remove bloatware (Alexa, TCL apps, TCL tracking/ads) — disable, not uninstall, with a one-line restore.
  Reference list: cleanyourtv `examples/tcl-55c7k-uk/debloat.txt` (same G08 platform).
- Nothing is done to the TV until he has reviewed it.

## Apps (left app bar)

Netflix, Prime Video, NOW, Paramount+, Apple TV, Spotify, SmartTube, Disney+, Tubi, ITVX, La7, RaiPlay,
Google Play Store (to get more apps), Button Mapper.

- App bar on the left; icons are tvOS-shaped landscape rounded rectangles with space between them.
- No floating app-name label when an app is highlighted.
- An app that isn't installed opens a plain screen: "<App> isn't installed" with a text button "Install <App>" and Back.
  No large Play Store artwork.

## Home screen layout (concept images + wireframe PDF)

- Top: round shortcut buttons (Search, Cast, Browser, Media player, Settings); name shows under the highlighted one.
- Top right: time | date | weather.
- Banner: a slideshow of suggested titles from his subscriptions, with dots.
- Active section (right of the banner): Spotify now playing with controls, news headlines, information about the day
  (clock changes, holidays, countdowns from the calendar).
- Rows: Watch Later, Continue Watching, Devices (PS5 live preview), Suggested, Series, Movies, genre rows.
  The last tile of each row is cut off "for a sense of continuation".

## Interaction and motion

- Remote navigation must be predictable: Up/Down never jumps into the app bar; the app bar is reached only with Left
  when there is nothing further left in the current row.
- Scale-only focus animation under 200 ms: the focused tile grows ~10% with a bright border glow; grid stays locked.
  tvOS-style lift, gentle sway and light on the focused item.
- Vertical scrolling must feel as smooth as horizontal (one row per press, glides when the button is held).
- Ambient colour bleed: the background takes the colours of the highlighted poster. No video autoplay.
- Settings: an Animations on/off switch (replaces the review-speed option).
- Overscan/safe area: tvOS 80 pt sides, 60 pt top/bottom. Large legible type (23 pt minimum), dark theme.

## Search

- Searches only content from apps he has (subscribed or free); suggestions likewise.
- Typing: a normal QWERTY keyboard layout (not a single row of letters).
- Voice: one mic option only — the "Speak" button in the search bar.

## Google Assistant (remote mic button)

- Keep Google Assistant on the remote's mic button.
- Answers appear in the active section (it becomes an interactive area):
  - weather → an animated weather scene (rain liked; glows must never be clipped into a square),
  - questions → images or video, shown whole (not cropped) unless the shape is extreme,
  - with an image/video, no text: a voice reads the answer aloud.
- The section keeps its exact size while showing answers.
- The answer goes away after ~30 s, or immediately when he selects the section.
- The 30 s timer (and its line animation) starts only after the voice has finished.
- Settings: choose the Assistant's voice.
- Open question for the real TV: Google Assistant renders its own overlay and does not hand answers to other apps;
  BitTV would need to own the mic button (own speech + answers) to put answers in the section. To decide together.
- Demo: the M key plays example answers.

## Boot

- His own boot animation (`BitTV Boot Animation.html`, current version: RATE 2, blue sheen) plays when BitTV starts.
  The TV's own boot logo still shows first (no root).

## Devices

- PS5 live, muted preview tile on Home when the console wakes (HDMI). Feasibility unproven on this TCL.

## Other

- Watch Later: one local list across all apps.
- Continue Watching from the apps if the platform allows (likely only some apps publish it).
- Settings: subscriptions, ambient colour, Animations, overscan guides, Assistant voice.
