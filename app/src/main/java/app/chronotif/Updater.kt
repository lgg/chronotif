// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.chronotif.core.Version
import app.chronotif.ui.SetupActivity
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Opt-in self-update from GitHub Releases. The only network code in the app: it reads the release
 * list from api.github.com and downloads the APK plus its `.sha256`. Nothing is sent.
 *
 * Installs go through PackageInstaller. On Android 12+ a self-update can be silent once
 * "Install unknown apps" is allowed for Chronotif; otherwise the system asks for confirmation.
 * Android itself refuses an APK not signed with the same key as the installed app.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/nagarete/chronotif/releases?per_page=10"
    private const val MAX_APK_BYTES = 50L shl 20
    private const val JOB_ID = 1
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 1
    const val EXTRA_VERSION = "version"

    class Release(val version: String, val apkUrl: String, val sumUrl: String)

    /** True while the setup screen is visible, so install prompts open directly instead of notifying. */
    @Volatile var foreground = false

    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    // --- Scheduling ---

    /** Keeps the daily check scheduled iff auto-update is on. Cheap; called on every app start. */
    fun sync(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java)
        if (!Prefs.autoUpdate) return js.cancel(JOB_ID)
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build(),
        )
    }

    // --- Check / download / install ---

    /**
     * Checks for a newer release and, if there is one, downloads and installs it.
     * Runs on a background thread; [onStatus] (main thread) gets human-readable progress.
     * [manual] checks also retry a version that failed to install before.
     */
    fun run(context: Context, manual: Boolean, onStatus: (String) -> Unit = {}, done: () -> Unit = {}) {
        val app = context.applicationContext
        fun status(res: Int, vararg args: Any) = main.post { onStatus(app.getString(res, *args)) }
        if (!running.compareAndSet(false, true)) {
            status(R.string.update_busy)
            return done()
        }
        thread(name = "chronotif-update") {
            try {
                status(R.string.update_checking)
                val r = latest()
                when {
                    r == null || !Version.isNewer(r.version, BuildConfig.VERSION_NAME) ->
                        status(R.string.update_none, BuildConfig.VERSION_NAME)
                    !manual && r.version == Prefs.updateFailedVersion -> status(R.string.update_skipped, r.version)
                    else -> {
                        status(R.string.update_downloading, r.version)
                        val apk = download(app, r)
                        status(R.string.update_installing, r.version)
                        install(app, apk, r.version)
                    }
                }
            } catch (e: Exception) {
                status(R.string.update_error, e.message ?: e.javaClass.simpleName)
            } finally {
                running.set(false)
                main.post(done)
            }
        }
    }

    /** Newest non-draft release that ships an APK and its checksum, pre-releases included. */
    private fun latest(): Release? {
        val json = get(RELEASES, api = true).use { it.readBytes().decodeToString() }
        val list = JSONArray(json)
        var best: Release? = null
        for (i in 0 until list.length()) {
            val rel = list.getJSONObject(i)
            if (rel.optBoolean("draft")) continue
            val tag = rel.optString("tag_name")
            val assets = rel.optJSONArray("assets") ?: continue
            var apk: String? = null
            var sum: String? = null
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                if (name.endsWith(".apk")) apk = url
                if (name.endsWith(".apk.sha256")) sum = url
            }
            if (apk == null || sum == null || Version.parse(tag) == null) continue
            if (best == null || Version.isNewer(tag, best.version)) best = Release(tag.removePrefix("v"), apk, sum)
        }
        return best
    }

    private fun download(context: Context, r: Release): File {
        val expected = get(r.sumUrl).use { it.readBytes().decodeToString() }
            .let(Version::sha256Of) ?: throw IOException("bad checksum file")
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        get(r.apkUrl).use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_APK_BYTES) throw IOException("download too large")
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            throw IOException("checksum mismatch")
        }
        return file
    }

    private fun install(context: Context, apk: File, version: String) {
        val installer = context.packageManager.packageInstaller
        // Drop sessions left over from an update the user never confirmed.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = Intent(context, UpdateReceiver::class.java)
                .setAction(UpdateReceiver.ACTION_STATUS)
                .putExtra(EXTRA_VERSION, version)
            // Mutable: the installer adds the status and confirmation intent as extras.
            val pi = PendingIntent.getBroadcast(
                context, id, result,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
        apk.delete()
    }

    private fun get(url: String, api: Boolean = false): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Chronotif/${BuildConfig.VERSION_NAME}")
        if (api) c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != HttpURLConnection.HTTP_OK) {
            c.disconnect()
            throw IOException("HTTP ${c.responseCode}")
        }
        return c.inputStream
    }

    // --- Notifications ---

    fun notify(context: Context, title: String, text: String, tap: PendingIntent?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = tap ?: PendingIntent.getActivity(
            context, 0, Intent(context, SetupActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            NOTIF_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun clearNotification(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
}

/** The daily background check. Only scheduled while auto-update is on. */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Updater.run(this, manual = false, done = { jobFinished(params, false) })
        return true
    }

    override fun onStopJob(params: JobParameters) = false
}

/** Install results from PackageInstaller, and the "we were just updated" broadcast. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Prefs.updateFailedVersion = null
            Updater.sync(context)
            if (Prefs.autoUpdate) {
                Updater.notify(
                    context, context.getString(R.string.update_done_title, BuildConfig.VERSION_NAME),
                    context.getString(R.string.update_done_body), null,
                )
            }
            return
        }
        if (intent.action != ACTION_STATUS) return
        val version = intent.getStringExtra(Updater.EXTRA_VERSION) ?: return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }) ?: return
                if (Updater.foreground) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    val pi = PendingIntent.getActivity(
                        context, 1, confirm,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    Updater.notify(
                        context, context.getString(R.string.update_ready_title, version),
                        context.getString(R.string.update_ready_body), pi,
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.clearNotification(context)
            // Dismissing the system prompt isn't a failure; the next daily check asks again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                Prefs.updateFailedVersion = version
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Updater.notify(
                    context, context.getString(R.string.update_failed_title, version),
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                        context.getString(R.string.update_failed_signature)
                    } else {
                        why
                    },
                    null,
                )
            }
        }
    }

    companion object {
        const val ACTION_STATUS = "app.chronotif.UPDATE_STATUS"
    }
}
