// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import app.chronotif.NotifStore
import app.chronotif.Prefs
import app.chronotif.R
import java.text.DateFormat
import java.util.Date

/**
 * The shade-like panel. Opens from the edge handle or Quick Settings tile, closes on back,
 * an outside tap, an upward fling or after opening something. Being on screen for a moment
 * (or interacting) counts as a meaningful check: on close, what was shown moves to EARLIER.
 */
class PanelActivity : Activity(), NotifList.Host {

    companion object {
        const val EXTRA_FOCUS_KEY = "focus_key"
        const val EXTRA_REPLY_INDEX = "reply_index"
        private const val CHECK_DWELL_MS = 1500L
    }

    override val activity: Activity get() = this

    private lateinit var scrim: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var date: TextView
    private lateinit var list: NotifList
    private val main = Handler(Looper.getMainLooper())
    private var resumedAt = 0L
    private var interacted = false
    private var closing = false

    private val render: () -> Unit = { list.render() }
    private val ticker = object : Runnable {
        override fun run() {
            list.render()
            main.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(window)
        val blur = Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled
        if (blur) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = dp(48) }
        }

        val root = FrameLayout(this)
        scrim = View(this).apply {
            setBackgroundColor(if (blur) 0x8C000000.toInt() else 0xD9000000.toInt())
            setOnClickListener { close() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        val pal = Palette(this)
        date = Ui.text(this, 15f, pal.onScrimSecondary, bold = true, lines = 1)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(4))
            addView(Ui.text(context, 30f, pal.onScrim, bold = true, lines = 1).apply {
                setText(R.string.tile_label)
            })
            addView(date, Ui.lp().apply { topMargin = dp(2) })
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(content)
            // Taps on empty space between cards close the panel, like a shade.
            setOnClickListener { close() }
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(column, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1, Gravity.TOP))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            scroll.setPadding(bars.left, bars.top + dp(8), bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(root)

        list = NotifList(this, content, onLock = false)
        handleFocus(intent)

        if (Build.VERSION.SDK_INT >= 33) {
            // Older versions fall back to the default back behaviour (finish).
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { close() }
        }
        animateIn()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFocus(intent)
        list.render()
    }

    private fun handleFocus(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_FOCUS_KEY) ?: return
        list.focus(key, intent.getIntExtra(EXTRA_REPLY_INDEX, -1))
        interacted = true
    }

    override fun onResume() {
        super.onResume()
        resumedAt = System.currentTimeMillis()
        date.text = DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        NotifStore.addListener(render)
        list.render()
        main.postDelayed(ticker, 60_000)
    }

    override fun onPause() {
        super.onPause()
        NotifStore.removeListener(render)
        main.removeCallbacks(ticker)
        if (interacted || System.currentTimeMillis() - resumedAt >= CHECK_DWELL_MS) {
            Prefs.markChecked(list.shownUpTo)
        }
    }

    override fun onStop() {
        super.onStop()
        // The panel is transient: once it's not on screen, it's gone.
        if (!isFinishing) finish()
    }

    // --- Host ---

    override fun launched() {
        finish()
        noTransition()
    }

    override fun guarded(action: () -> Unit) = action()

    // --- Gestures: upward fling at the bottom of the list closes, like pushing a shade up.

    private var downY = 0f
    private var atBottomOnDown = false
    private var tracker: VelocityTracker? = null

    @SuppressLint("Recycle")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                interacted = true
                downY = ev.rawY
                atBottomOnDown = !scroll.canScrollVertically(1)
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> tracker?.addMovement(ev)
            MotionEvent.ACTION_UP -> {
                val t = tracker
                t?.addMovement(ev)
                t?.computeCurrentVelocity(1000)
                val vy = t?.yVelocity ?: 0f
                t?.recycle()
                tracker = null
                if (atBottomOnDown && downY - ev.rawY > dp(90) && vy < -dp(600)) {
                    close()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // --- Animation ---

    private fun animateIn() {
        scrim.alpha = 0f
        scroll.alpha = 0f
        scroll.translationY = -dp(48).toFloat()
        scrim.animate().alpha(1f).setDuration(180).start()
        scroll.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun close() {
        if (closing) return
        closing = true
        scrim.animate().alpha(0f).setDuration(160).start()
        scroll.animate().alpha(0f).translationY(-dp(64).toFloat()).setDuration(160)
            .withEndAction {
                finish()
                noTransition()
            }.start()
    }

    @Suppress("DEPRECATION")
    private fun noTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}
