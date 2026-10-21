# Chronotif

An iPhone-inspired notification experience for Android: chronological, calm, and built around
**attention** rather than a pile of cards.

- **NEW**: what arrived since your last meaningful check, newest first, shown in full.
- **EARLIER**: what you've already seen. It stays accessible, but recedes: smaller, dimmer and
  folded into per-app stacks.
- **Ongoing** (media, navigation, downloads) sits compactly below NEW and is never "new".

The same model runs on the lock screen: fresh notifications up front, earlier ones folded behind
an "N earlier" pill, with privacy rules applied.

You interact with the **real** notifications: tap to open, swipe sideways to dismiss, expand in
place (big text, inbox lines, conversations, pictures, progress), use the app's actions, and
reply inline.

No internet permission, no accounts, no analytics, no backend. The APK is ~120 KB with zero
third-party dependencies.

## How it works

| Piece | What it does | Cost when idle |
|---|---|---|
| Notification listener | Mirrors active notifications in memory; opens/dismisses/acts on them | Event-driven, no polling |
| Edge handle | A slim pill on the screen edge. Tap or swipe it to open the panel. Tinted with your accent colour while something is NEW | One tiny overlay window, no timers |
| Quick Settings tile | Another way into the panel; shows "N new" | Only when the shade is open |
| Panel | The shade-like screen: blurred backdrop, NEW / Ongoing / EARLIER | Exists only while open |
| Lock-screen view | Shown over the locked keyguard when the screen turns on | Started only on screen-on |

No foreground service, wakelocks, alarms, jobs or network.

### What counts as a "meaningful check"

- The panel was open for at least 1.5 s, or you interacted with it. When it closes, everything
  it showed moves to EARLIER.
- On the lock screen, glancing isn't enough. Like iOS, the lock-screen notifications move to
  EARLIER when you **unlock** after they were shown.
- A notification comes back to NEW only when it actually brings something new: e.g. a new
  message in an existing chat. Silent refreshes, progress updates and "alert once" updates don't.

The only persisted state is that last-check timestamp plus your settings, stored in app-private
storage and excluded from backups.

### Lock-screen privacy

- Content is only hidden when the device has a secure lock (PIN/pattern/password), matching
  Android itself.
- **Secret** notifications (by the app, or your per-channel setting) are not shown at all.
- **Private** notifications are redacted ("App · Notification", or the app's public version)
  when Android's *hide sensitive content* setting is on, or when you turn on
  *Hide sensitive content* in Chronotif.
- If Android's *show notifications on lock screen* is off, the lock-screen view never appears.
- Opening, actions and replies always go through the system unlock first. Swiping a
  notification away doesn't.
- It never covers a ringing call or an alarm/call screen.

## Install

1. Download `chronotif-vX.Y.Z.apk` from
   [Releases](../../releases) (verify against the `.sha256` file if you like) and install it.
2. Open **Chronotif** and work through *Permissions*:
   - **Notification access**: on Android 13+ a sideloaded app's toggle is greyed out at first.
     Go to *App info → ⋮ → Allow restricted settings*, then grant it.
   - **Display over other apps**: for the edge handle and the lock-screen view.
3. Optional: add the **Notifications** Quick Settings tile (button in setup on Android 13+).

After that you never need to open the app again. Use the edge handle or the tile.

### "App not installed" (Google Play Protect)

Installing an unknown, newly released app from a file manager can fail with a bare
"App not installed" (in `adb logcat`: `INSTALL_FAILED_VERIFICATION_FAILURE: Install not
allowed`). This comes from **Google Play Protect**, not from a problem with the APK. It asks to
upload the unknown app for scanning, and rejects the install when upload consent is off.

Any one of these fixes it:

- Open **Play Store → profile icon → Play Protect → ⚙ settings** and turn on
  **Improve harmful app detection** (lets it send unknown apps to Google for scanning), then
  install again; or
- Temporarily turn off **Scan apps with Play Protect** in the same screen, install, turn it
  back on; or
- Install over USB with `adb install chronotif-vX.Y.Z.apk`, which skips the on-device check.

### Xiaomi / HyperOS / MIUI (Poco X3 NFC, Xiaomi 17T Pro)

Xiaomi adds its own permission layer. In setup, the *Xiaomi permissions* and *Xiaomi autostart*
buttons jump to the right screens:

- **Other permissions → Show on Lock screen**: required for the lock-screen view.
- **Other permissions → Display pop-up windows while running in the background**: required for
  the handle and lock-screen view to open.
- **Autostart**: on, so notification access survives reboots.
- **Battery saver → No restrictions** for Chronotif (App info → Battery saver).

Optionally, lock Chronotif in Recents so the system doesn't kill it.

## Using it

- **Open the panel**: tap the edge handle, or swipe it down/inward; or use the QS tile.
- **Close**: back, tap empty space, or fling up at the end of the list.
- **Open a notification**: tap it.
- **Dismiss**: swipe sideways. Swiping a collapsed stack dismisses the whole stack.
  **Clear** clears EARLIER; × clears an expanded stack.
- **Expand**: chevron or long-press. Expanded cards show full text, conversation history,
  pictures and actions.
- **Reply**: expand, tap the reply action, type, send.
- **Lock screen**: swipe up (or use fingerprint/face) to unlock; back reveals the system lock
  screen. *Settings → Lock screen* chooses Off / *When something is new* (default) /
  *Whenever there are notifications*.

## Limitations

Android doesn't let a third-party app replace the system shade or lock screen, so Chronotif
works **alongside** them:

- Swiping down from the top still opens the system shade. Heads-up banners are still the
  system's.
- The lock-screen view is an activity on top of the (still locked) keyguard. You may briefly see
  the system lock screen as the screen turns on. It shows your home wallpaper.
- On stock Android 8+, the system posts "Chronotif is displaying over other apps". Long-press it
  to turn that notification off.
- Custom notification layouts (RemoteViews) are rendered from their text, not pixel-for-pixel.
- Apps that refuse input while *any* overlay is on screen (rare, mostly banking) may complain.
  Disable the edge handle and use the tile instead.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Without signing variables, release builds are signed with the debug key. To sign properly:

```sh
keytool -genkeypair -v -keystore release.jks -alias chronotif -keyalg RSA -keysize 4096 -validity 10000
export CHRONOTIF_KEYSTORE=$PWD/release.jks CHRONOTIF_KEYSTORE_PASSWORD=… \
       CHRONOTIF_KEY_ALIAS=chronotif CHRONOTIF_KEY_PASSWORD=…
./gradlew assembleRelease
```

Keep the keystore safe and out of git. Android only installs updates signed with the same key.

### Releasing on GitHub

`.github/workflows/build.yml` builds and tests every push and PR. Pushing a `v*` tag also publishes
a GitHub Release with the APK and its SHA-256. Add these repository secrets for signed
releases: `KEYSTORE_B64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

```sh
git tag v0.1.0 && git push origin v0.1.0
```

## Code map

```
app/src/main/java/app/chronotif/
  core/Attention.kt     pure attention model: NEW/EARLIER split, stacks, arrival tracking (unit-tested)
  NotifStore.kt         in-memory mirror of active notifications
  ListenerService.kt    the notification listener; screen on/off/unlock events
  EdgeHandle.kt         edge overlay
  PanelTile.kt          Quick Settings tile
  LockPolicy.kt         when the lock view appears and what it may reveal
  Content.kt            extracting text/messages/pictures from notifications; app labels & icons
  Act.kt                open / dismiss / action / inline reply
  ui/NotifList.kt       renders sections with keyed view reuse (shared by panel and lock view)
  ui/CardView.kt        notification card and collapsed stack
  ui/PanelActivity.kt   the panel
  ui/LockActivity.kt    the lock-screen view
  ui/SetupActivity.kt   one-time setup & settings
```
