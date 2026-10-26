# Notification wake and conversation behavior

Agreed behavior from the October 2026 bug-report interview. Implemented with automated regression coverage; verification on the reporting HyperOS device is pending.

## Wake session

An untouched notification wake is one continuous session. Chronotif remains visible until the display sleeps, without an additional interval showing the stock lock screen. Back explicitly reveals the stock lock screen.

The reported failure was approximately ten seconds of Chronotif followed by approximately ten seconds of the stock lock screen. The inactivity callback that finished the activity has been removed. Android now controls the display timeout while Chronotif remains visible. This can be longer than the stock lock-screen timeout; the app does not force sleep or change the device's display settings.

## Conversation history

A collapsed conversation card previews its latest message. Expanding a New conversation card shows all messages Android currently supplies for that conversation, including already-checked messages. Revealing Earlier or expanding an app stack must not be a prerequisite.

This covers messages available in active Android notifications. It does not require an archive of messages Android no longer supplies. Lock-screen privacy rules continue to apply.

## New incoming messages

A new incoming message returns its conversation to New, including when the app sends the update silently or marks it to alert only once. Timestamp refreshes, progress updates, and updates with no new incoming message do not return a checked conversation to New merely because they were reposted.

For structured conversations, incoming message timestamps identify arrivals. Outgoing replies and changes to historic messages do not count. For apps that supply only a message title and body, changed text identifies an arrival; Android supplies no incoming-message identity in that format. Identical plain-text messages cannot be distinguished from reposts.

## Verification cases

- Leave a notification wake untouched. Chronotif remains visible until the display sleeps, without an extra stock lock-screen interval.
- Press Back while Chronotif is visible. The stock lock screen appears.
- Expand a New conversation containing multiple supplied messages. All supplied messages are accessible without opening Earlier first.
- Check a conversation, then receive another incoming message through a silent update of its notification. The conversation returns to New.
- Refresh a checked notification without a new incoming message. It remains in Earlier.

## Automated verification

`./gradlew testDebugUnitTest` runs the pure attention-model tests and Android-backed Robolectric tests. Regression tests reproduce the silent message being classified into Earlier, the six-message history cap, and the ten-second activity exit before the fixes. They also cover outgoing replies, historic messages, repeated content, plain-text message updates, and redaction.

The lock-activity test verifies that inactivity leaves Chronotif visible and a screen-off broadcast closes it. It does not emulate HyperOS power management or establish a physical display-sleep duration. Verify that behavior on the reporting phone.
