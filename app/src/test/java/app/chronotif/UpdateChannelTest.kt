// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateChannelTest {
    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("chronotif", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(context)
    }

    @Test
    fun freshInstallUsesBuildChannelAndExplicitChoicePersists() {
        assertEquals(BuildConfig.NIGHTLY, Prefs.nightlyUpdates)
        Prefs.nightlyUpdates = !BuildConfig.NIGHTLY
        Prefs.init(RuntimeEnvironment.getApplication())
        assertEquals(!BuildConfig.NIGHTLY, Prefs.nightlyUpdates)
    }

    @Test
    fun stableIgnoresNightliesDraftsAndPrereleases() {
        val releases = JSONArray()
            .put(release("nightly-0.2.4-100", prerelease = true))
            .put(release("v0.2.4", prerelease = true))
            .put(release("v0.2.5", draft = true))
            .put(release("v0.2.3"))
        assertEquals("0.2.3", Updater.selectRelease(releases, nightly = false)?.version)
    }

    @Test
    fun nightlySelectsNewestBuildWithoutCrossingToStable() {
        val releases = JSONArray()
            .put(release("v0.3.0"))
            .put(release("nightly-0.2.3-9", prerelease = true))
            .put(release("nightly-0.2.3-11", draft = true))
            .put(release("nightly-0.2.3-10", prerelease = true))
        assertEquals("0.2.3-nightly.10", Updater.selectRelease(releases, nightly = true)?.version)
    }

    @Test
    fun incompleteAssetsCannotBecomeAnUpdate() {
        val incomplete = release("nightly-0.2.3-11", prerelease = true)
        incomplete.getJSONArray("assets").getJSONObject(1).put("name", "other.apk.sha256")
        val releases = JSONArray().put(incomplete).put(release("nightly-0.2.3-10", prerelease = true))
        val selected = Updater.selectRelease(releases, nightly = true)!!
        assertEquals("0.2.3-nightly.10", selected.version)
        assertEquals("https://example.com/app.apk", selected.apkUrl)
        assertEquals("https://example.com/app.apk.sha256", selected.sumUrl)
        assertNull(Updater.selectRelease(JSONArray().put(incomplete), nightly = true))
    }

    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) =
        JSONObject().put("tag_name", tag).put("prerelease", prerelease).put("draft", draft)
            .put("assets", JSONArray(listOf("app.apk", "app.apk.sha256").map { name ->
                JSONObject().put("name", name).put("browser_download_url", "https://example.com/$name")
            }))
}
