// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.chronotif.Prefs

/**
 * Position the date relative to the actual painted clock digits, not the vertical
 * bounds of their TextViews. Large clock fonts and small date fonts reserve very
 * different top/bottom areas even with includeFontPadding=false; native Gravity.TOP
 * and Gravity.BOTTOM therefore appear visually offset as the clock size changes.
 *
 * This is an ordinary horizontal LinearLayout; only the date's Y position changes
 * after layout, and the rest of the header retains its normal measurement.
 */
internal class InkAlignedClockRow(
    context: Context,
    private val clock: TextView,
    private val date: TextView,
) : LinearLayout(context) {
    var verticalAlignment: Int = Prefs.HEADER_DATE_ALIGN_CENTER
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Font boxes may shift slightly beyond their measured rows; actual glyphs
        // remain within the clock's visible ink bounds.
        clipChildren = false
        clipToPadding = false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (date.parent !== this || clock.visibility != VISIBLE || date.visibility != VISIBLE) return
        val a = visibleInk(clock) ?: return
        val b = visibleInk(date) ?: return
        val delta = when (verticalAlignment) {
            Prefs.HEADER_DATE_ALIGN_TOP -> a.top - b.top
            Prefs.HEADER_DATE_ALIGN_BOTTOM -> a.bottom - b.bottom
            else -> (a.top + a.bottom - b.top - b.bottom) / 2
        }
        if (delta != 0) date.offsetTopAndBottom(delta)
    }

    companion object {
        /**
         * Vertical ink bounds in the row's coordinates, including all visible
         * lines and their actual baselines. Works for both single-line digits and
         * multiline date strings.
         */
        internal fun visibleInk(view: TextView): Rect? {
            val layout = view.layout ?: return null
            val text = view.text.toString()
            if (text.isEmpty() || layout.lineCount == 0) return null
            val ink = Rect()
            val lineBounds = Rect()
            var found = false
            val firstLineBaseline = layout.getLineBaseline(0)
            for (line in 0 until layout.lineCount) {
                var start = layout.getLineStart(line).coerceIn(0, text.length)
                var end = layout.getLineEnd(line).coerceIn(start, text.length)
                while (start < end && text[start].isWhitespace()) start++
                while (end > start && text[end - 1].isWhitespace()) end--
                if (end == start) continue
                view.paint.getTextBounds(text, start, end, lineBounds)
                val baseline = view.top + view.baseline +
                    layout.getLineBaseline(line) - firstLineBaseline
                if (!found) {
                    ink.set(0, baseline + lineBounds.top, 0, baseline + lineBounds.bottom)
                    found = true
                } else {
                    ink.top = minOf(ink.top, baseline + lineBounds.top)
                    ink.bottom = maxOf(ink.bottom, baseline + lineBounds.bottom)
                }
            }
            return if (found) ink else null
        }
    }
}
