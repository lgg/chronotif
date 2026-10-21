// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTest {
    private data class N(
        val key: String,
        override val stackId: String,
        override val alertTime: Long,
        override val ongoing: Boolean = false,
    ) : Meta

    @Test
    fun splitsAtWatermarkNewestFirst() {
        val s = Attention.sections(
            listOf(N("a", "mail", 10), N("b", "chat", 30), N("c", "chat", 20), N("d", "mail", 5)),
            lastCheck = 15,
        )
        assertEquals(listOf("b", "c"), s.fresh.map { it.key })
        assertEquals(listOf("mail"), s.earlier.map { it.id })
        assertEquals(listOf("a", "d"), s.earlier[0].items.map { it.key })
    }

    @Test
    fun earlierStacksOrderedByNewestMember() {
        val s = Attention.sections(
            listOf(N("a", "mail", 1), N("b", "chat", 3), N("c", "mail", 4), N("d", "sms", 2)),
            lastCheck = 100,
        )
        assertTrue(s.fresh.isEmpty())
        assertEquals(listOf("mail", "chat", "sms"), s.earlier.map { it.id })
        assertEquals(4, s.earlierCount)
    }

    @Test
    fun ongoingIsSeparateAndNeverNew() {
        val music = N("m", "music", 50, ongoing = true)
        val s = Attention.sections(listOf(music, N("x", "chat", 60)), lastCheck = 0)
        assertEquals(listOf("m"), s.ongoing.map { it.key })
        assertEquals(listOf("x"), s.fresh.map { it.key })
        assertTrue(!Attention.isNew(music, 0))
    }

    @Test
    fun watermarkNeverMovesBack() {
        assertEquals(20, Attention.advance(20, 10))
        assertEquals(30, Attention.advance(20, 30))
    }

    @Test
    fun alertClockOnlyResetsOnRealArrivals() {
        val c = AlertClock()
        assertEquals(100, c.onPosted("k", 100, signature = 1, quiet = false))
        // Same content re-posted (e.g. timestamp refresh): keeps original arrival.
        assertEquals(100, c.onPosted("k", 200, signature = 1, quiet = false))
        // New content but app asked to alert only once: still not a new arrival.
        assertEquals(100, c.onPosted("k", 300, signature = 2, quiet = true))
        // New content, not quiet: arrives again (a new message in an existing chat).
        assertEquals(400, c.onPosted("k", 400, signature = 3, quiet = false))
        c.onRemoved("k")
        assertEquals(500, c.onPosted("k", 500, signature = 3, quiet = true))
    }

    @Test
    fun relativeTime() {
        val day = 86_400_000L
        val today = 10 * day
        val now = today + 12 * 3_600_000
        assertEquals(RelTime.Label.Now, RelTime.of(now, now - 5_000, today))
        assertEquals(RelTime.Label.Minutes(5), RelTime.of(now, now - 5 * 60_000, today))
        assertEquals(RelTime.Label.Hours(3), RelTime.of(now, now - 3 * 3_600_000, today))
        assertEquals(RelTime.Label.Yesterday, RelTime.of(now, today - 8 * 3_600_000, today))
        assertEquals(RelTime.Label.Older, RelTime.of(now, today - 3 * day, today))
    }
}
