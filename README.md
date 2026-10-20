<p align="center">
  <img src="design/assets/ic_launcher-playstore.png" width="120" alt="TouchGrass logo">
</p>

<h1 align="center">TouchGrass</h1>

<p align="center">
  <b>Keep Instagram and TikTok. Lose the endless scroll.</b><br>
  An Android app that blocks Reels and the For You feed — and nothing else.
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/Russian03/TouchGrass?label=download&color=b5e35a" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/internet%20permission-none-1c1c1c" alt="No internet permission">
</p>

<p align="center">
  🇬🇧 English · <a href="README.es.md">🇪🇸 Español</a>
</p>

---

TouchGrass sits **on top of the official apps**. You keep using Instagram and TikTok for what
you actually want (messages, your friends' posts, stories, profiles), but the moment you
enter the endless video feed, a calm full-screen pause covers it.

If you really want to watch, you ask for a few minutes from a **daily budget** that you set
yourself. When they run out, the pause comes back.

## Features

- **Blocks only the scroll.** Instagram Reels (tab and viewer) and TikTok's *For You* and
  *Community* tabs. Everything else stays free.
- **Smart exceptions.** A reel opened from a profile, a chat, your feed or your saved posts
  plays normally. From a chat, your feed or your saved posts, swiping to the next reel brings
  the pause.
- **Daily budget.** Choose how many minutes per day you can unlock (up to 4 h). The day resets
  at 8:00. Lowering the limit applies at once. Raising it only applies from the next month.
- **Ask for time, with a reason.** Pick the app, the minutes and why (bathroom, food, bus,
  hobby, sleep or your own text). Finish early and the unused time comes back, rounded down
  to multiples of 5 minutes.
- **Stats that motivate.** A face for every day, streaks of low-usage days, a monthly calendar
  and how often the pause stopped you.
- **Hard to cheat.** Unlocks are timed with a clock you can't change, the budget needs
  automatic date and time, and turning the protection off is remembered.
- **Silences the feed.** While the pause is on, the video is muted and the volume buttons do
  nothing.
- **100% private.** No internet permission, no accounts, no analytics. Nothing leaves your
  phone.

## How it works

TouchGrass uses Android's **accessibility service**, limited to Instagram, TikTok and your
home screen. It looks only at the internal *IDs* of the on-screen buttons (for example "Reels
tab, selected") and, on TikTok, the name of the top tabs. It never reads messages, captions,
names or anything typed.

When those IDs match a blocked area, TouchGrass places a full-screen pause on top. The
official app keeps running underneath, so nothing is modified, injected or patched.

The detection rules live in small JSON files (`app/src/main/assets/rules/`), so they can be
updated when Instagram or TikTok change their apps.

## Download and install

TouchGrass is not on the Play Store. It is published on
[GitHub Releases](../../releases/latest).

### Recommended: with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs apps straight from their GitHub
releases and tells you when there's a new version. Apps installed this way aren't hit by
Android's *restricted setting* block, which otherwise stops you from turning on the
accessibility service (on some phones, like Xiaomi with HyperOS, there's no way to lift it).

1. **Install Obtainium** from its [releases page](https://github.com/ImranR98/Obtainium/releases/latest)
   (`app-arm64-v8a-release.apk` works on almost every current phone).
2. In Obtainium, tap **Add app**, paste `https://github.com/Russian03/TouchGrass` and tap
   **Add**.
3. Tap **Install**. The first time, Android asks you to allow Obtainium to install apps.
4. Continue with [first steps](#first-steps).

### Manual: download the APK

1. **Download the APK.** On your phone, open the [latest release](../../releases/latest) and
   download `touchgrass-vX.Y.Z.apk` from *Assets*. Open it and tap *Install*. Android will ask
   you to allow installing apps from your browser or file manager.
2. **Allow restricted settings** (Android 13+). Android blocks accessibility for apps installed
   from a browser or file manager. Try to turn on the protection once, so Android refuses, then
   go to *Settings → Apps → TouchGrass → ⋮ → Allow restricted settings*. If that option never
   appears (common on Xiaomi), uninstall TouchGrass and install it with Obtainium instead.

### First steps

1. **Set up.** TouchGrass asks for your name (optional), explains how it works and asks for
   your daily limit. Before turning on the protection, it shows exactly what the service reads
   and what it doesn't, and asks for your consent.
2. **Turn on the protection.** Tap *Activar en Accesibilidad* (turn on in Accessibility) and
   enable TouchGrass. Back in the app, it should say the scroll is now blocked.

Google Play Protect may warn you about an app that doesn't come from the Play Store and uses
accessibility. Tap *More details → Install anyway*.

> The app is in **Spanish**.

### Xiaomi, Redmi and POCO (HyperOS / MIUI)

These systems kill background apps aggressively. Android restarts the protection by itself
within seconds, but only if the system allows it:

- In *Settings → Apps → TouchGrass*, turn on **Autostart** and set **Battery saver** to **No
  restrictions**. The app shows this tip, with a shortcut, on Xiaomi phones.
- TouchGrass is hidden from Recents on purpose, so it can't be closed by swiping it away.

### Other phones

On stock Android (Pixel, Motorola…) nothing else is needed. On brands with aggressive battery
savers (Samsung, Huawei, OnePlus, Oppo…), exclude TouchGrass from battery optimization if the
protection stops. On Samsung: *Settings → Battery → Background usage limits → Never sleeping
apps*.

Tested on a Xiaomi with HyperOS (Android 14). Other brands should work but are not verified.

### Verify the download (optional)

Each release includes `touchgrass-vX.Y.Z.apk.sha256`:

```bash
sha256sum -c touchgrass-vX.Y.Z.apk.sha256    # should print OK
```

All releases are signed with the same key. SHA-256 fingerprint of the certificate:

```
4d9cde9b128b248b103de0124f4ba33e56604af95620c538ef45f0e8af8df652
```

Check it with `apksigner verify --print-certs touchgrass-vX.Y.Z.apk` (part of the Android
SDK).

## Updating

TouchGrass **never updates itself**: it has no internet permission, on purpose.

- **With Obtainium**, you get a notification when there's a new version; tap *Update*.
- **Manually**, download the new APK and install it over the current one. To hear about new
  versions, use *Watch → Custom → Releases* on this repo.

Every version is signed with the same key, so updating keeps your limit, budget and stats.

If Android says there is a conflict with an existing package, the APK wasn't signed with the
TouchGrass key. Don't install it: download only from this repo's Releases.

## Privacy

- **No internet permission** and no runtime permissions: only the accessibility service,
  which you turn on yourself after reading what it does.
- The service only watches Instagram, TikTok and your home screen, and only keeps button
  IDs. It never reads messages, posts, names or passwords, and never stores what's on your
  screen.
- Your name, limit, unlocks (with their reason) and stats are stored only on your phone and
  excluded from cloud backups.

Full policy: [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md) (in Spanish, also inside the app).

## Troubleshooting

**"TouchGrass has stopped".** The system closed the service and it didn't come back within a
minute. Your day isn't affected: only turning it off yourself in Accessibility counts as
cheating. Check *Autostart* and the battery saver (see above). If it still doesn't come back,
open the app's settings, tap *Reactivar en Accesibilidad* (reactivate in Accessibility), and
turn TouchGrass **off** and **on** again.

**The budget doesn't reset.** Turn on automatic date, time and time zone. Without them, the
budget freezes so the clock can't be used to cheat.

**Instagram or TikTok updated and the block stopped working.** The apps sometimes rename their
internal IDs. Open an issue with the app version.

## Limitations

- Android only: iOS doesn't allow this kind of service.
- Instagram and TikTok only (no YouTube Shorts yet).
- Rules depend on the internal IDs of the official apps, so a big update can break them until
  the rules are updated.

## Build from source

Requirements: JDK 17+ and Android SDK 37 (Android Studio includes both).

```bash
./gradlew assembleDebug        # debug APK
./gradlew installDebug         # install on a phone connected with adb
./gradlew testDebugUnitTest    # unit tests
./gradlew ktlintCheck lintDebug
```

The debug build (`io.github.russian03.touchgrass.debug`) can be installed next to the release
build. Each one has its own accessibility service.

Built with Kotlin, Jetpack Compose (Material 3 Expressive), Hilt and DataStore. The blocking
logic (`core/`) is plain Kotlin and covered by unit tests.

## License

© 2026 Russian03. All rights reserved. The source code is public so you can read and audit
it, but it isn't licensed for reuse or redistribution.

The app includes the [Montserrat](https://fonts.google.com/specimen/Montserrat) font, under
the [SIL Open Font License 1.1](docs/licencias/montserrat-OFL.txt).
