// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelEmptyTapTest {
    @Test fun tapBelowLastCardCloses() {
        assertTrue(PanelActivity.isEmptySpaceTap(true, true, 2f, -2f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(false, true, 0f, 0f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(true, false, 0f, 0f, 10f))
    }

    @Test fun dragsAreNotMistakenForTaps() {
        assertFalse(PanelActivity.isEmptySpaceTap(true, true, 15f, 0f, 10f))
        assertFalse(PanelActivity.isEmptySpaceTap(true, true, 0f, -15f, 10f))
        assertTrue(PanelActivity.isEmptySpaceTap(true, true, -10f, 10f, 10f))
    }
}
