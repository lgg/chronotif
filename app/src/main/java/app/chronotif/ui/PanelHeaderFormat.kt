// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.content.Context
import android.text.format.DateFormat
import app.chronotif.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Locale-aware date formatting and an optional override of Android's time format. */
internal object PanelHeaderFormat {
    fun date(date: Date = Date()): String {
        val skeleton = (if (Prefs.headerFullWeekday) "EEEE" else "EEE") +
            (if (Prefs.headerFullMonth) "MMMM" else "MMM") +
            "d" + (if (Prefs.headerShowYear) "y" else "")
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).format(date)
    }

    fun timePattern(system24Hour: Boolean): String {
        val is24 = if (Prefs.headerUseSystemTimeFormat) system24Hour else Prefs.headerOverride24Hour
        return if (is24) "HH:mm" else "h:mm"
    }

    fun time(context: Context, now: Date = Date()): String =
        DateFormat.format(timePattern(DateFormat.is24HourFormat(context)), now).toString()
}
