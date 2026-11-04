// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.chronotif.Prefs
import app.chronotif.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelHeaderAppearanceTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var oldLocale: Locale

    @Before fun setup() {
        oldLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        ctx.getSharedPreferences("chronotif", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(ctx)
    }

    @After fun restoreLocale() {
        Locale.setDefault(oldLocale)
    }

    @Test fun defaultsPreserveExistingPanelHeader() {
        assertTrue(Prefs.headerShowTitle)
        assertFalse(Prefs.headerShowClock)
        assertTrue(Prefs.headerFullWeekday)
        assertTrue(Prefs.headerFullMonth)
        assertTrue(Prefs.headerShowYear)
        assertTrue(Prefs.headerUseSystemTimeFormat)
        assertEquals(76, Prefs.headerClockSizeSp)
        assertFalse(Prefs.headerDateBesideClock)
        assertEquals(Prefs.HEADER_DATE_ALIGN_CENTER, Prefs.headerDateVerticalAlignment)
    }

    @Test fun headerPreferencesPersistAcrossInitialization() {
        Prefs.headerShowTitle = false
        Prefs.headerShowClock = true
        Prefs.headerFullWeekday = false
        Prefs.headerFullMonth = false
        Prefs.headerShowYear = false
        Prefs.headerUseSystemTimeFormat = false
        Prefs.headerOverride24Hour = false
        Prefs.headerClockSizeSp = 48
        Prefs.headerDateBesideClock = true
        Prefs.headerDateVerticalAlignment = Prefs.HEADER_DATE_ALIGN_BOTTOM
        Prefs.init(ctx)
        assertFalse(Prefs.headerShowTitle)
        assertTrue(Prefs.headerShowClock)
        assertFalse(Prefs.headerFullWeekday)
        assertFalse(Prefs.headerFullMonth)
        assertFalse(Prefs.headerShowYear)
        assertEquals(48, Prefs.headerClockSizeSp)
        assertTrue(Prefs.headerDateBesideClock)
        assertEquals(Prefs.HEADER_DATE_ALIGN_BOTTOM, Prefs.headerDateVerticalAlignment)
        assertEquals("h:mm", PanelHeaderFormat.timePattern(true))
        Prefs.headerOverride24Hour = true
        assertEquals("HH:mm", PanelHeaderFormat.timePattern(false))
    }

    @Test fun clockSizeStaysInSupportedRange() {
        Prefs.headerClockSizeSp = 1
        assertEquals(Prefs.HEADER_CLOCK_MIN_SP, Prefs.headerClockSizeSp)
        Prefs.headerClockSizeSp = 1000
        assertEquals(Prefs.HEADER_CLOCK_MAX_SP, Prefs.headerClockSizeSp)
    }

    @Test fun dateAlignmentClampsToKnownValues() {
        Prefs.headerDateVerticalAlignment = -1
        assertEquals(Prefs.HEADER_DATE_ALIGN_TOP, Prefs.headerDateVerticalAlignment)
        Prefs.headerDateVerticalAlignment = 99
        assertEquals(Prefs.HEADER_DATE_ALIGN_BOTTOM, Prefs.headerDateVerticalAlignment)
    }

    @Test fun systemTimeFormatWinsUnlessManuallyOverridden() {
        Prefs.headerUseSystemTimeFormat = true
        Prefs.headerOverride24Hour = false
        assertEquals("HH:mm", PanelHeaderFormat.timePattern(true))
        assertEquals("h:mm", PanelHeaderFormat.timePattern(false))
    }

    @Test fun dateFormattingRespectsAllThreeSwitches() {
        val day = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 4, 13, 15, 0)
        }.time
        val full = PanelHeaderFormat.date(day)
        assertTrue(full.contains("Wednesday"))
        assertTrue(full.contains("November"))
        assertTrue(full.contains("2026"))

        Prefs.headerFullWeekday = false
        Prefs.headerFullMonth = false
        Prefs.headerShowYear = false
        val short = PanelHeaderFormat.date(day)
        assertTrue(short.contains("Wed"))
        assertTrue(short.contains("Nov"))
        assertFalse(short.contains("Wednesday"))
        assertFalse(short.contains("November"))
        assertFalse(short.contains("2026"))
    }

    @Test fun panelShowsOrHidesTitleAndClockIndependently() {
        Prefs.headerShowTitle = false
        Prefs.headerShowClock = true
        val activity = Robolectric.buildActivity(PanelActivity::class.java).setup()
        try {
            val root = activity.get().findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as FrameLayout
            val scroll = (0 until root.childCount).map { root.getChildAt(it) }
                .filterIsInstance<ScrollView>().single()
            val column = scroll.getChildAt(0) as LinearLayout
            val header = column.getChildAt(0) as LinearLayout
            assertEquals(View.GONE, header.getChildAt(0).visibility)
            val row = header.getChildAt(1) as LinearLayout
            assertEquals(View.VISIBLE, row.visibility)
            assertTrue((row.getChildAt(0) as TextView).text.isNotEmpty())
            assertTrue((header.getChildAt(2) as TextView).text.isNotEmpty())
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun dateMovesBesideClockAndClockSizeApplies() {
        Prefs.headerShowClock = true
        Prefs.headerDateBesideClock = true
        Prefs.headerClockSizeSp = 48
        val activity = Robolectric.buildActivity(PanelActivity::class.java).setup()
        try {
            val root = activity.get().findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as FrameLayout
            val scroll = (0 until root.childCount).map { root.getChildAt(it) }
                .filterIsInstance<ScrollView>().single()
            val column = scroll.getChildAt(0) as LinearLayout
            val header = column.getChildAt(0) as LinearLayout
            val row = header.getChildAt(1) as LinearLayout
            assertEquals(2, header.childCount)
            assertEquals(2, row.childCount)
            val clock = row.getChildAt(0) as TextView
            val date = row.getChildAt(1) as TextView
            assertEquals(48f * ctx.resources.displayMetrics.scaledDensity,
                clock.textSize, 1.5f)
            assertEquals(3, date.maxLines)
            val params = date.layoutParams as LinearLayout.LayoutParams
            assertEquals(0, params.width)
            assertEquals(1f, params.weight, 0f)
            assertEquals(Gravity.CENTER_VERTICAL, params.gravity)
            assertTrue(date.text.isNotEmpty())
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun dateSideAlignmentAppliesToAllThreePositions() {
        for ((value, expectedGravity) in listOf(
            Prefs.HEADER_DATE_ALIGN_TOP to Gravity.TOP,
            Prefs.HEADER_DATE_ALIGN_CENTER to Gravity.CENTER_VERTICAL,
            Prefs.HEADER_DATE_ALIGN_BOTTOM to Gravity.BOTTOM,
        )) {
            Prefs.headerShowClock = true
            Prefs.headerDateBesideClock = true
            Prefs.headerDateVerticalAlignment = value
            val controller = Robolectric.buildActivity(PanelActivity::class.java).setup()
            try {
                val root = controller.get().findViewById<ViewGroup>(android.R.id.content)
                    .getChildAt(0) as FrameLayout
                val scroll = (0 until root.childCount).map { root.getChildAt(it) }
                    .filterIsInstance<ScrollView>().single()
                val header = ((scroll.getChildAt(0) as LinearLayout).getChildAt(0)) as LinearLayout
                val clockRow = header.getChildAt(1) as LinearLayout
                val date = clockRow.getChildAt(1) as TextView
                assertEquals(expectedGravity,
                    (date.layoutParams as LinearLayout.LayoutParams).gravity)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test fun dateReturnsToTheLeftWhenClockHiddenEvenIfBesideEnabled() {
        Prefs.headerShowClock = false
        Prefs.headerDateBesideClock = true
        Prefs.headerDateVerticalAlignment = Prefs.HEADER_DATE_ALIGN_BOTTOM
        val activity = Robolectric.buildActivity(PanelActivity::class.java).setup()
        try {
            val root = activity.get().findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as FrameLayout
            val scroll = (0 until root.childCount).map { root.getChildAt(it) }
                .filterIsInstance<ScrollView>().single()
            val column = scroll.getChildAt(0) as LinearLayout
            val header = column.getChildAt(0) as LinearLayout
            assertEquals(3, header.childCount)
            assertEquals(View.GONE, header.getChildAt(1).visibility)
            val date = header.getChildAt(2) as TextView
            assertEquals(1, date.maxLines)
            assertTrue(date.text.isNotEmpty())
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun clockFormatOverrideIsDisabledUntilClockAndOverrideAreEnabled() {
        val controller = Robolectric.buildActivity(HeaderSettingsActivity::class.java).setup()
        try {
            val root = controller.get().findViewById<ViewGroup>(android.R.id.content)
            fun switches(view: View): List<Switch> =
                if (view is Switch) listOf(view)
                else if (view is ViewGroup) (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
                else emptyList()
            val all = switches(root)
            assertEquals(8, all.size)
            val clock = all.single { it.text == ctx.getString(R.string.header_show_clock) }
            val system = all.single { it.text == ctx.getString(R.string.header_use_system_time) }
            val manual = all.single { it.text == ctx.getString(R.string.header_24_hour) }
            val beside = all.single { it.text == ctx.getString(R.string.header_date_beside_clock) }
            fun seekBars(view: View): List<SeekBar> =
                if (view is SeekBar) listOf(view)
                else if (view is ViewGroup) (0 until view.childCount).flatMap { seekBars(view.getChildAt(it)) }
                else emptyList()
            val size = seekBars(root).single()
            assertFalse(system.isEnabled)
            assertFalse(manual.isEnabled)
            assertFalse(beside.isEnabled)
            assertFalse(size.isEnabled)
            val alignmentGroups = mutableListOf<RadioGroup>()
            fun findGroups(view: View) {
                if (view is RadioGroup) alignmentGroups += view
                if (view is ViewGroup) (0 until view.childCount).forEach {
                    findGroups(view.getChildAt(it))
                }
            }
            findGroups(root)
            val alignment = alignmentGroups.single()
            val radios = (0 until alignment.childCount).map {
                alignment.getChildAt(it) as RadioButton
            }
            assertTrue(radios.none { it.isEnabled })
            clock.isChecked = true
            assertTrue(system.isEnabled)
            assertTrue(beside.isEnabled)
            assertTrue(size.isEnabled)
            assertTrue(radios.none { it.isEnabled })
            beside.isChecked = true
            assertTrue(Prefs.headerDateBesideClock)
            assertTrue(radios.all { it.isEnabled })
            assertEquals(3, radios.size)
            radios[2].isChecked = true
            assertEquals(Prefs.HEADER_DATE_ALIGN_BOTTOM, Prefs.headerDateVerticalAlignment)
            beside.isChecked = false
            assertTrue(radios.none { it.isEnabled })
            beside.isChecked = true
            size.progress = 48 - Prefs.HEADER_CLOCK_MIN_SP
            assertEquals(48, Prefs.headerClockSizeSp)
            assertFalse(manual.isEnabled)
            system.isChecked = false
            assertTrue(manual.isEnabled)
            assertFalse(Prefs.headerUseSystemTimeFormat)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
