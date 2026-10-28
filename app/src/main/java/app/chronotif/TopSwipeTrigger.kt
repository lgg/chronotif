// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import app.chronotif.ui.PanelActivity
import app.chronotif.ui.dp
import kotlin.math.abs

/**
 * Optional, invisible touch strip immediately BELOW the system status bar.
 *
 * This cannot replace SystemUI or receive touches that originate inside the actual status bar.
 * Touches that start in this overlay are consumed, even when a swipe doesn't trigger the panel.
 * It is intentionally opt-in, adjustable in physical pixels, and hidden in landscape.
 */
class TopSwipeTrigger(service: Context) {
    companion object {
        /** Pure gesture predicate, shared with unit tests. */
        internal fun isDownwardSwipe(dx: Float, dy: Float, threshold: Float): Boolean =
            dy >= threshold && dy > abs(dx) * 1.25f
    }

    private val ctx: Context = service.getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)
        .let { service.createDisplayContext(it) }
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: TriggerView? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        Prefs.topSwipeHeightPx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "Chronotif top swipe"
        gravity = Gravity.TOP or Gravity.START
    }

    fun show() {
        if (view == null) {
            val v = TriggerView(ctx)
            layout()
            try {
                wm.addView(v, params)
                view = v
            } catch (_: RuntimeException) {
                // Xiaomi can deny overlay windows in the background even after permission.
                return
            }
        }
        update()
    }

    fun update() {
        val v = view ?: return
        layout()
        // A horizontal strip is a poor fit for full-screen landscape apps and games.
        v.visibility = if (ctx.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
        runCatching { wm.updateViewLayout(v, params) }
    }

    fun hide() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
    }

    private fun layout() {
        // FLAG_LAYOUT_IN_SCREEN uses display coordinates. Insets keep the touchable area
        // outside the actual SystemUI status bar (and below any display cutout).
        val insets = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
        )
        params.height = Prefs.topSwipeHeightPx
        params.x = 0
        params.y = insets.top
    }

    @SuppressLint("ViewConstructor")
    private class TriggerView(context: Context) : View(context) {
        private val threshold = maxOf(
            context.dp(24).toFloat(),
            ViewConfiguration.get(context).scaledTouchSlop * 2f,
        )
        private var startX = 0f
        private var startY = 0f
        private var tracking = false
        private var fired = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    tracking = true
                    fired = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> tracking = false
                MotionEvent.ACTION_MOVE -> if (tracking && !fired) {
                    val dy = event.rawY - startY
                    val dx = abs(event.rawX - startX)
                    if (isDownwardSwipe(dx, dy, threshold)) {
                        fired = true
                        tracking = false
                        runCatching {
                            context.startActivity(
                                Intent(context, PanelActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION),
                            )
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
            }
            return true
        }
    }
}
