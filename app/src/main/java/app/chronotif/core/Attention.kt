// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.core

/**
 * The attention model, free of Android types so it can be unit-tested.
 *
 * Every notification carries an [Meta.alertTime]: when it last *arrived* (first post, or an
 * update that genuinely brought new content). A single watermark, the time of the user's last
 * meaningful check, splits notifications into NEW (arrived after it) and EARLIER (already seen).
 * EARLIER is folded into per-app stacks so it recedes instead of cluttering.
 */
interface Meta {
    /** Notifications sharing a stack id fold together in EARLIER (one app, one user). */
    val stackId: String
    val alertTime: Long
    /** Ongoing work (media, navigation, downloads) lives outside NEW/EARLIER. */
    val ongoing: Boolean
}

class Stack<T>(val id: String, val items: List<T>)

class Sections<T>(
    val ongoing: List<T>,
    val fresh: List<T>,
    val earlier: List<Stack<T>>,
) {
    val isEmpty get() = ongoing.isEmpty() && fresh.isEmpty() && earlier.isEmpty()
    val earlierCount get() = earlier.sumOf { it.items.size }
}

object Attention {

    /** Newest first everywhere; EARLIER stacks are ordered by their newest member. */
    fun <T : Meta> sections(items: Collection<T>, lastCheck: Long): Sections<T> {
        val byRecent = items.sortedByDescending { it.alertTime }
        val ongoing = ArrayList<T>()
        val fresh = ArrayList<T>()
        val earlier = LinkedHashMap<String, MutableList<T>>()
        for (item in byRecent) when {
            item.ongoing -> ongoing += item
            item.alertTime > lastCheck -> fresh += item
            else -> earlier.getOrPut(item.stackId) { ArrayList() } += item
        }
        return Sections(ongoing, fresh, earlier.map { (id, list) -> Stack(id, list) })
    }

    fun isNew(item: Meta, lastCheck: Long) = !item.ongoing && item.alertTime > lastCheck

    /** A check never moves the watermark backwards. */
    fun advance(lastCheck: Long, seenUpTo: Long) = maxOf(lastCheck, seenUpTo)
}

/** Identity of the latest incoming message; zero time means the app supplied only plain text. */
data class MessageStamp(val time: Long, val signature: Int)

/**
 * Tracks when each notification last arrived. Apps re-post the same key constantly
 * (progress, timestamps, silent refreshes). Incoming conversation messages count even when
 * silent; other content changes count only when the app didn't mark the update as quiet.
 */
class AlertClock {
    private class State(val alertTime: Long, val signature: Int, val message: MessageStamp?)

    private val states = HashMap<String, State>()

    fun onPosted(
        key: String, postTime: Long, signature: Int, quiet: Boolean,
        conversation: Boolean = false, message: MessageStamp? = null,
    ): Long {
        val prev = states[key]
        val incoming = message != null && (prev?.message == null ||
            message.time > prev.message.time ||
            (message.time == 0L && message != prev.message))
        val arrived = if (conversation) incoming else !quiet && signature != prev?.signature
        val alertTime =
            if (prev == null || arrived) postTime else prev.alertTime
        // Keep the newest incoming marker when an app trims history or adds an outgoing reply.
        val latest = if (message != null && (prev?.message == null || message.time >= prev.message.time)) {
            message
        } else prev?.message
        states[key] = State(alertTime, signature, latest)
        return alertTime
    }

    fun onRemoved(key: String) {
        states.remove(key)
    }

    fun clear() = states.clear()
}

/** Short relative timestamps, iOS style: now, 5m, 3h, Yesterday, then a date label. */
object RelTime {
    sealed interface Label {
        data object Now : Label
        data class Minutes(val m: Int) : Label
        data class Hours(val h: Int) : Label
        data object Yesterday : Label
        data object Older : Label
    }

    fun of(now: Long, t: Long, startOfToday: Long): Label {
        val d = (now - t).coerceAtLeast(0)
        return when {
            d < 60_000 -> Label.Now
            d < 3_600_000 -> Label.Minutes((d / 60_000).toInt())
            t >= startOfToday || d < 6 * 3_600_000 -> Label.Hours((d / 3_600_000).toInt())
            t >= startOfToday - 86_400_000 -> Label.Yesterday
            else -> Label.Older
        }
    }
}
