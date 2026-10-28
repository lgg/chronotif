// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import android.app.Application
import android.content.Context
import app.chronotif.Prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelEmptyTapTest {
    @Before fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("chronotif", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(ctx)
    }

    @Test fun optionalDismissalIsDisabledByDefaultAndPersists() {
        assertFalse(Prefs.tapEmptySpaceToClose)
        Prefs.tapEmptySpaceToClose = true
        assertTrue(Prefs.tapEmptySpaceToClose)
        Prefs.init(RuntimeEnvironment.getApplication())
        assertTrue(Prefs.tapEmptySpaceToClose)
    }

    @Test fun tapBelowLastCardClosesOnlyWhenEnabled() {
        assertFalse(PanelActivity.isEmptySpaceTap(false, true, true, 0f, 0f, 10f))
        assertTrue(PanelActivity.isEmptySpaceTap(true, true, true, 2f, -2f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(true, false, true, 0f, 0f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(true, true, false, 0f, 0f, 10f))
    }

    @Test fun dragsAreNotMistakenForTaps() {
        assertFalse(PanelActivity.isEmptySpaceTap(true, true, true, 15f, 0f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(true, true, true, 0f, -15f, 10f))
        assertTrue(PanelActivity.isEmptySpaceTap(true, true, true, -10f, 10f, 10f))
    }
}
