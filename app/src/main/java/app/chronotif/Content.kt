package app.chronotif

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.util.LruCache

/** What a card displays, extracted from a notification's extras (no foreign RemoteViews). */
class Content(
    val appName: CharSequence,
    val title: CharSequence,
    val text: CharSequence?,
    /** Full text for the expanded card (big text style), if different from [text]. */
    val bigText: CharSequence?,
    /** Inbox lines or recent messages. */
    val lines: List<CharSequence>,
    val subText: CharSequence?,
    val progress: Progress?,
    val isMedia: Boolean,
    val redacted: Boolean,
) {
    class Progress(val value: Int, val max: Int, val indeterminate: Boolean)

    val expandable: Boolean
        get() = !redacted && (bigText != null || lines.size > 1 || (text?.length ?: 0) > 80)

    companion object {
        private const val MAX_LINES = 6

        fun of(context: Context, e: Entry, redact: Boolean): Content {
            val appName = Apps.label(context, e.pkg)
            if (redact) {
                val pub = e.n.publicVersion?.extras
                return Content(
                    appName = appName,
                    title = pub?.getCharSequence(Notification.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: appName,
                    text = pub?.getCharSequence(Notification.EXTRA_TEXT)
                        ?: context.getString(R.string.hidden_content),
                    bigText = null, lines = emptyList(), subText = null, progress = null,
                    isMedia = false, redacted = true,
                )
            }
            val x = e.n.extras
            var title = x.getCharSequence(Notification.EXTRA_TITLE)
                ?: x.getCharSequence(Notification.EXTRA_TITLE_BIG)
            var text = x.getCharSequence(Notification.EXTRA_TEXT)
            val big = x.getCharSequence(Notification.EXTRA_BIG_TEXT)
            var lines: List<CharSequence> =
                x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.filterNotNull().orEmpty()

            val messages = messages(x)
            if (messages.isNotEmpty()) {
                val conversation = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                val group = conversation != null ||
                    x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
                title = conversation ?: title
                lines = messages.takeLast(MAX_LINES).map { (sender, body) ->
                    if (group && sender != null) "$sender: $body" else body
                }
                text = lines.last()
            }

            val progressMax = x.getInt(Notification.EXTRA_PROGRESS_MAX)
            val indeterminate = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
            val progress = if (progressMax > 0 || indeterminate) {
                Content.Progress(x.getInt(Notification.EXTRA_PROGRESS), progressMax, indeterminate)
            } else null

            return Content(
                appName = appName,
                title = title?.takeIf { it.isNotBlank() } ?: appName,
                text = text?.takeIf { it.isNotBlank() },
                bigText = big?.takeIf { it.isNotBlank() && it.toString() != text?.toString() },
                lines = lines.takeLast(MAX_LINES),
                subText = x.getCharSequence(Notification.EXTRA_SUB_TEXT)
                    ?: x.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
                progress = progress,
                isMedia = x.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true,
                redacted = false,
            )
        }

        /** Changes when the visible content changes; used to tell real arrivals from refreshes. */
        fun signature(n: Notification): Int {
            val x = n.extras
            val last = messages(x).lastOrNull()
            return listOf(
                x.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                x.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size,
                last?.second?.toString(),
            ).hashCode()
        }

        @Suppress("DEPRECATION")
        private fun messages(x: Bundle): List<Pair<CharSequence?, CharSequence>> {
            val arr: Array<Parcelable>? = x.getParcelableArray(Notification.EXTRA_MESSAGES)
            return arr.orEmpty().mapNotNull { p ->
                val b = p as? Bundle ?: return@mapNotNull null
                val body = b.getCharSequence("text") ?: return@mapNotNull null
                val sender = b.getCharSequence("sender")
                    ?: (b.getParcelable("sender_person") as? Person)?.name
                sender to body
            }
        }

        @Suppress("DEPRECATION")
        fun picture(context: Context, e: Entry): Drawable? {
            val x = e.n.extras
            (x.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)?.let {
                return BitmapDrawable(context.resources, it)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                (x.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon)?.let {
                    return runCatching { it.loadDrawable(context) }.getOrNull()
                }
            }
            return null
        }
    }
}

/** App labels and icons, cached; resolved locally via the package manager. */
object Apps {
    private val labels = HashMap<String, CharSequence>()
    private val icons = LruCache<String, Drawable>(48)
    private val large = LruCache<String, Drawable>(48)

    fun label(context: Context, pkg: String): CharSequence = labels.getOrPut(pkg) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun icon(context: Context, e: Entry): Drawable? {
        icons.get(e.stackId)?.let { return it }
        val pm = context.packageManager
        val d = try {
            val plain = pm.getApplicationIcon(e.pkg)
            if (e.sbn.user == Process.myUserHandle()) plain
            else runCatching { pm.getUserBadgedIcon(plain, e.sbn.user) }.getOrDefault(plain)
        } catch (_: PackageManager.NameNotFoundException) {
            runCatching { e.n.smallIcon?.loadDrawable(context) }.getOrNull()
        } ?: return null
        icons.put(e.stackId, d)
        return d
    }

    /** Contact photo / sender avatar etc. Keyed by post time so updates refresh it. */
    fun largeIcon(context: Context, e: Entry): Drawable? {
        val k = "${e.key}@${e.sbn.postTime}"
        large.get(k)?.let { return it }
        val d = runCatching { e.n.getLargeIcon()?.loadDrawable(context) }.getOrNull() ?: return null
        large.put(k, d)
        return d
    }

    fun actionIcon(context: Context, a: Notification.Action): Drawable? =
        runCatching { a.getIcon()?.loadDrawable(context) }.getOrNull()
}
