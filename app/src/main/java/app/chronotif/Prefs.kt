// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.content.Context
import android.content.SharedPreferences
import app.chronotif.core.Attention

/** The only persisted state: the last-check watermark and a handful of settings. */
object Prefs {
    const val LOCK_OFF = 0
    const val LOCK_WHEN_NEW = 1
    const val LOCK_ALWAYS = 2

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("chronotif", Context.MODE_PRIVATE)
    }

    /** Wall-clock time up to which the user has seen notifications. */
    val lastCheck: Long get() = sp.getLong("last_check", 0L)

    fun markChecked(seenUpTo: Long) {
        val next = Attention.advance(lastCheck, seenUpTo)
        if (next != lastCheck) {
            sp.edit().putLong("last_check", next).apply()
            NotifStore.changed()
        }
    }

    var handleEnabled: Boolean
        get() = sp.getBoolean("handle_enabled", true)
        set(v) = sp.edit().putBoolean("handle_enabled", v).apply()

    var handleRight: Boolean
        get() = sp.getBoolean("handle_right", true)
        set(v) = sp.edit().putBoolean("handle_right", v).apply()

    /** Vertical position of the handle as a fraction of screen height. */
    var handleY: Float
        get() = sp.getFloat("handle_y", 0.22f)
        set(v) = sp.edit().putFloat("handle_y", v).apply()

    var handleHideLandscape: Boolean
        get() = sp.getBoolean("handle_hide_landscape", true)
        set(v) = sp.edit().putBoolean("handle_hide_landscape", v).apply()

    var lockMode: Int
        get() = sp.getInt("lock_mode", LOCK_WHEN_NEW)
        set(v) = sp.edit().putInt("lock_mode", v).apply()

    var lockHideContent: Boolean
        get() = sp.getBoolean("lock_hide_content", false)
        set(v) = sp.edit().putBoolean("lock_hide_content", v).apply()
}
