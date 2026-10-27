# Notification wake and conversation behavior

Agreed behavior from the October 2026 bug-report interview. Implemented with automated regression coverage; verification on the reporting HyperOS device is pending.

## Wake session

An untouched notification wake is one continuous session that lasts as long as the stock lock screen would have. Chronotif remains visible until the display sleeps, without an additional interval showing the stock lock screen. Back explicitly reveals the stock lock screen.

The first reported failure was approximately ten seconds of Chronotif followed by approximately ten seconds of the stock lock screen, caused by an inactivity timer that finished the activity. Removing that timer exposed a second failure: a window covering the keyguard gets the normal screen timeout (the one used while an app is open), so the display stayed on for the full screen-timeout setting. Chronotif's window now requests the keyguard's own short timeout (`config_lockScreenDisplayTimeout`, ten seconds when the ROM doesn't define it). Touches extend it as they do on the stock lock screen. The device's display settings are not changed.

## Conversation history

A collapsed New conversation card lists every message sent since the last check (the latest five at most), like the stock notification does, without expanding or revealing Earlier. Already-checked messages stay behind the expand chevron. Expanding shows all messages Android currently supplies for that conversation. Earlier cards preview only the latest message.

This covers messages available in active Android notifications. It does not require an archive of messages Android no longer supplies. Lock-screen privacy rules continue to apply.

## New incoming messages

A new incoming message returns its conversation to New, including when the app sends the update silently or marks it to alert only once. Timestamp refreshes, progress updates, and updates with no new incoming message do not return a checked conversation to New merely because they were reposted.

For structured conversations, incoming message timestamps identify arrivals. Outgoing replies and changes to historic messages do not count. For apps that supply only a message title and body, changed text identifies an arrival; Android supplies no incoming-message identity in that format. Identical plain-text messages cannot be distinguished from reposts.

## Calls, media and Running

Agreed in the October 2026 interview about always-running services crowding the top of the panel.

Ongoing calls come first, then Media players, then New and Earlier. Every other ongoing notification is Running: always-running services, connected devices, step counters, downloads, navigation and timers. Running sits below Earlier behind an "N running" pill and collapses again each time the panel opens. When Running is all there is, only the pill shows.

A media player stays a Media player while paused, even though apps drop the ongoing flag when they pause. It never falls into Earlier. Calls, Media players and Running never count as New, and none of them bring up the lock-screen view by themselves. Running never appears on the lock-screen view.

## Verification cases

- Leave a notification wake untouched. Chronotif remains visible and the display sleeps after the stock lock-screen timeout (about ten seconds), not the normal screen timeout, without an extra stock lock-screen interval.
- Press Back while Chronotif is visible. The stock lock screen appears.
- Receive several messages in one conversation. The New card lists them all without expanding. Expanding shows the supplied history without opening Earlier first.
- Check a conversation, then receive another incoming message through a silent update of its notification. The conversation returns to New.
- Refresh a checked notification without a new incoming message. It remains in Earlier.
- Open the panel with only always-running services active. Only the "N running" pill shows; tapping it reveals them.
- Pause music. Its player stays at the top and does not move to Earlier.

## Automated verification

`./gradlew testDebugUnitTest` runs the pure attention-model tests and Android-backed Robolectric tests. Regression tests reproduce the silent message being classified into Earlier, the six-message history cap, and the ten-second activity exit before the fixes. They also cover outgoing replies, historic messages, repeated content, plain-text message updates, and redaction.

The lock-activity test verifies that the window requests the keyguard timeout, that inactivity leaves Chronotif visible, and that a screen-off broadcast closes it. On an Android 16 emulator with a 60-second screen timeout, an untouched wake slept after about 11 seconds with Chronotif visible; without the timeout request it stayed on for 60 seconds. HyperOS power management is not emulated. Verify that behavior on the reporting phone.
