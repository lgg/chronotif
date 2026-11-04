// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.chronotif.Prefs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Align the rendered first date glyph with the last numeric clock glyph.
 *
 * FontMetrics, TextView bounds and even Paint.getTextBounds() for a whole
 * sentence are NOT the visible W/1 edges: descenders in "Wednesday" and font
 * padding change those bounds. The previous implementation produced offsets
 * of ~50px at large font sizes on HyperOS. We sample the actual pixels drawn
 * by each TextView, restricted to the relevant glyph's horizontal advance.
 *
 * This only runs when this small header lays out (not every animation frame).
 * Both TextViews remain regular accessible, localized views.
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
        gravity = Gravity.TOP
        clipChildren = false
        clipToPadding = false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (date.parent !== this || clock.visibility != VISIBLE || date.visibility != VISIBLE) return

        val clockInk = referenceGlyphInk(clock, isClock = true) ?: return
        val dateInk = referenceGlyphInk(date, isClock = false) ?: return
        // Ink ranges are inclusive pixel coordinates in the parent row.
        val delta = when (verticalAlignment) {
            Prefs.HEADER_DATE_ALIGN_TOP -> clockInk.first - dateInk.first
            Prefs.HEADER_DATE_ALIGN_BOTTOM -> clockInk.last - dateInk.last
            else -> ((clockInk.first + clockInk.last) -
                (dateInk.first + dateInk.last)) / 2
        }
        if (delta != 0) date.offsetTopAndBottom(delta)
    }

    companion object {
        /**
         * Return the actually painted vertical pixel range of the clock's last
         * digit (e.g. '1' in 05:41), or the date's FIRST glyph (e.g. 'W').
         * Pixel sampling avoids estimating the wrong box for other glyphs.
         */
        internal fun referenceGlyphInk(view: TextView, isClock: Boolean): IntRange? {
            val text = view.text.toString()
            if (text.isEmpty() || view.width <= 0 || view.height <= 0) return null
            val index = if (isClock) text.indexOfLast { it.isDigit() }
                else text.indexOfFirst { !it.isWhitespace() }
            if (index < 0) return null
            val layout = view.layout ?: return null
            val line = layout.getLineForOffset(index)
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            if (index < lineStart || index >= lineEnd) return null

            // The glyph advance comes from the real text layout, including
            // font features, letter spacing, and RTL handling.
            val x0 = view.totalPaddingLeft + layout.getPrimaryHorizontal(index)
            val x1 = view.totalPaddingLeft + layout.getPrimaryHorizontal(index + 1)
            val glyphLeft = floor(min(x0, x1)).toInt().coerceIn(0, view.width)
            val glyphRight = ceil(max(x0, x1)).toInt().coerceIn(0, view.width)
            val width = glyphRight - glyphLeft
            if (width <= 0) return fallbackGlyphInk(view, text, index)

            // ARGB_8888 is necessary here because ALPHA_8 getPixels() has
            // device-dependent channel behavior. Draw on a transparent bitmap.
            val bitmap = try {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            } catch (_: IllegalArgumentException) {
                return fallbackGlyphInk(view, text, index)
            } catch (_: OutOfMemoryError) {
                return fallbackGlyphInk(view, text, index)
            }
            try {
                view.draw(Canvas(bitmap))
                val linePixels = IntArray(width)
                var first = -1
                var last = -1
                for (y in 0 until view.height) {
                    bitmap.getPixels(linePixels, 0, width, glyphLeft, y, width, 1)
                    if (linePixels.any { Color.alpha(it) >= 48 }) {
                        if (first < 0) first = y
                        last = y
                    }
                }
                if (first >= 0) return (view.top + first)..(view.top + last)
            } finally {
                bitmap.recycle()
            }
            return fallbackGlyphInk(view, text, index)
        }

        /** Fallback when a view cannot be software-rendered (e.g. low memory). */
        private fun fallbackGlyphInk(view: TextView, text: String, index: Int): IntRange {
            val b = Rect()
            view.paint.getTextBounds(text, index, index + 1, b)
            val baseline = view.top + view.baseline
            return (baseline + b.top)..(baseline + b.bottom - 1)
        }
    }
}
