// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import app.chronotif.core.Kind
import java.util.Locale

/**
 * Request real dismissal of an inactive Media player, never local-only hiding.
 *
 * Android's notification listener can cancel clearable notifications. For a
 * protected (FLAG_NO_CLEAR) Media player with a deleteIntent, forward the user's
 * swipe to the app-provided dismissal action instead. MediaSession state takes
 * priority; a stale/absent session needs an unambiguous "Play" notification action.
 *
 * Sending the PendingIntent is not proof of dismissal: the card remains tied to
 * Android's notification removal, and the UI rolls back if it is not removed.
 */
object MediaDismiss {
    /** Allow a user swipe only when the player can reasonably be treated as inactive. */
    fun canSwipe(e: Entry, playback: PlaybackState?): Boolean =
        e.kind == Kind.MEDIA &&
            MediaSwipePolicy.canAttempt(
                playback?.state, e.clearable, e.n.deleteIntent != null, hasPlayAction(e.n)
            )

    /**
     * Playback may have resumed while the swipe animated. Look up the current
     * notification and session again just before asking Android to remove it.
     * A successful return means cancellation was requested, not yet confirmed.
     * The UI restores the card if onNotificationRemoved never arrives.
     */
    fun dismiss(context: Context, entry: Entry): Boolean {
        val live = NotifStore.get(entry.key) ?: return false
        if (live.kind != Kind.MEDIA) return false

        val token = mediaToken(live.n)
        // A notification key may be reused when a different media session arrives.
        if (mediaToken(entry.n) != token) return false
        val state = token?.let {
            runCatching {
                MediaController(context.applicationContext, it).playbackState?.state
            }.getOrNull()
        }
        // A stale media notification can outlive its session. A currently supplied
        // "Play" action is the only fallback if the session state is unavailable.
        // A known PLAYING / BUFFERING / CONNECTING state always wins over it.
        if (!MediaSwipePolicy.canAttempt(
                state, live.clearable, live.n.deleteIntent != null, hasPlayAction(live.n)
            )
        ) return false

        // Require a connected listener to observe real removal before the UI considers
        // the dismissal complete. This never edits the local notification store.
        val listener = ListenerService.instance ?: return false
        return if (live.clearable) {
            runCatching {
                Act.dismiss(live)
                true
            }.getOrDefault(false)
        } else {
            // The source app (not Chronotif) decides whether this explicit user
            // dismissal removes its ongoing media notification. No stop()/pause().
            val delete = live.n.deleteIntent ?: return false
            runCatching {
                delete.send()
                true
            }.getOrDefault(false)
        }
    }

    /** A strict, current Play action from the media app indicates an inactive player
     * when its MediaSession no longer reports a state (e.g. after closing Apple Music).
     * Never infer inactivity from an absent session alone. */
    private fun hasPlayAction(n: Notification): Boolean {
        val titles = n.actions.orEmpty().map { it.title.toString().trim().lowercase(Locale.ROOT) }
        val play = setOf("play", "resume", "воспроизвести", "продолжить")
        val pause = setOf("pause", "приостановить", "пауза")
        return titles.any { it in play } && titles.none { it in pause }
    }

    private fun mediaToken(notification: Notification): MediaSession.Token? =
        if (Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
}

/** Active and transitional playback states always override the Play-action fallback. */
internal object MediaSwipePolicy {
    fun canAttempt(
        state: Int?, clearable: Boolean, hasDeleteIntent: Boolean, hasPlayAction: Boolean = false,
    ): Boolean =
        (clearable || hasDeleteIntent) && when (state) {
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_ERROR -> true
            null, PlaybackState.STATE_NONE -> hasPlayAction
            else -> false
        }

}
