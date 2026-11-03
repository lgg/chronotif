// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.app.Application
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.chronotif.Prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelHeaderAlignmentTest {
    @Before fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("chronotif", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(ctx)
    }

    @Test fun headerAndNotificationContainerShareHorizontalInset() {
        val controller = Robolectric.buildActivity(PanelActivity::class.java).create()
        try {
            val root = controller.get().findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as FrameLayout
            val scroll = (0 until root.childCount).map { root.getChildAt(it) }
                .filterIsInstance<ScrollView>().single()
            val column = scroll.getChildAt(0) as LinearLayout
            val header = column.getChildAt(0) as LinearLayout
            val cards = column.getChildAt(1) as LinearLayout
            assertEquals(cards.paddingLeft, header.paddingLeft)
            assertEquals(cards.paddingRight, header.paddingRight)
            assertEquals(controller.get().dp(12), header.paddingLeft)
        } finally {
            controller.destroy()
        }
    }
}
