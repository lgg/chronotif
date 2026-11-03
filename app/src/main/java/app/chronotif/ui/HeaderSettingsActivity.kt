// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.chronotif.Prefs
import app.chronotif.R

/** Dedicated panel-header settings; intentionally no preview or extra permissions. */
class HeaderSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(window)
        val pal = Palette(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply { addView(body) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)

        body.addView(Button(this).apply {
            setText(R.string.header_back)
            setOnClickListener { finish() }
        }, Ui.lp(-2))
        body.addView(label(getString(R.string.header_settings_title), 26f, pal.text),
            Ui.lp().apply { topMargin = dp(12) })
        body.addView(label(getString(R.string.header_settings_desc), 14f, pal.textSecondary),
            Ui.lp().apply { topMargin = dp(4) })

        fun toggle(labelId: Int, checked: Boolean, update: (Boolean) -> Unit): Switch {
            val item = Switch(this).apply {
                setText(labelId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(pal.text)
                isChecked = checked
                setOnCheckedChangeListener { _, enabled -> update(enabled) }
            }
            body.addView(item, Ui.lp().apply { topMargin = dp(16) })
            return item
        }

        toggle(R.string.header_show_title, Prefs.headerShowTitle) { Prefs.headerShowTitle = it }
        val showClock = toggle(R.string.header_show_clock, Prefs.headerShowClock) {
            Prefs.headerShowClock = it
        }
        body.addView(label(getString(R.string.header_clock_desc), 13f, pal.textSecondary))

        val clockSizeLabel = label(
            getString(R.string.header_clock_size_value, Prefs.headerClockSizeSp), 14f, pal.text
        )
        body.addView(clockSizeLabel, Ui.lp().apply { topMargin = dp(16) })
        val clockSize = SeekBar(this).apply {
            max = Prefs.HEADER_CLOCK_MAX_SP - Prefs.HEADER_CLOCK_MIN_SP
            progress = Prefs.headerClockSizeSp - Prefs.HEADER_CLOCK_MIN_SP
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    // Always persist changes, including accessible/programmatic slider updates.
                    val sp = (value + Prefs.HEADER_CLOCK_MIN_SP)
                        .coerceIn(Prefs.HEADER_CLOCK_MIN_SP, Prefs.HEADER_CLOCK_MAX_SP)
                    Prefs.headerClockSizeSp = sp
                    clockSizeLabel.text = getString(R.string.header_clock_size_value, sp)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        body.addView(clockSize, Ui.lp())

        val dateBesideClock = toggle(
            R.string.header_date_beside_clock, Prefs.headerDateBesideClock
        ) { Prefs.headerDateBesideClock = it }
        toggle(R.string.header_full_weekday, Prefs.headerFullWeekday) { Prefs.headerFullWeekday = it }
        toggle(R.string.header_full_month, Prefs.headerFullMonth) { Prefs.headerFullMonth = it }
        toggle(R.string.header_show_year, Prefs.headerShowYear) { Prefs.headerShowYear = it }

        val useSystem = toggle(R.string.header_use_system_time, Prefs.headerUseSystemTimeFormat) {
            Prefs.headerUseSystemTimeFormat = it
        }
        body.addView(label(getString(R.string.header_system_time_desc), 13f, pal.textSecondary))
        val manualFormat = toggle(R.string.header_24_hour, Prefs.headerOverride24Hour) {
            Prefs.headerOverride24Hour = it
        }

        fun refreshTimeControls() {
            val clockEnabled = showClock.isChecked
            clockSize.isEnabled = clockEnabled
            clockSize.alpha = if (clockEnabled) 1f else 0.45f
            clockSizeLabel.alpha = if (clockEnabled) 1f else 0.45f
            dateBesideClock.isEnabled = clockEnabled
            dateBesideClock.alpha = if (clockEnabled) 1f else 0.45f
            useSystem.isEnabled = clockEnabled
            useSystem.alpha = if (clockEnabled) 1f else 0.45f
            manualFormat.isEnabled = clockEnabled && !useSystem.isChecked
            manualFormat.alpha = if (manualFormat.isEnabled) 1f else 0.45f
        }
        showClock.setOnCheckedChangeListener { _, checked ->
            Prefs.headerShowClock = checked
            refreshTimeControls()
        }
        useSystem.setOnCheckedChangeListener { _, checked ->
            Prefs.headerUseSystemTimeFormat = checked
            refreshTimeControls()
        }
        refreshTimeControls()
    }

    private fun label(text: String, size: Float, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
    }
}
