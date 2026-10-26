// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.core

/** Release versions as tagged on GitHub: `v1.2.3`, optionally with a `-suffix` pre-release. */
object Version {
    /** Numeric parts of [v], ignoring a leading `v` and any `-suffix`/`+build`; null if not a version. */
    fun parse(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() && it.all { p -> p >= 0 } }
    }

    /** True if [candidate] is strictly newer than [current]. Unparseable versions are never newer. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        val nightly = Regex("-nightly\\.(\\d+)$")
        val aNightly = nightly.find(candidate)?.groupValues?.get(1)?.toIntOrNull()
        val bNightly = nightly.find(current)?.groupValues?.get(1)?.toIntOrNull()
        if (aNightly != null && bNightly != null) return aNightly > bNightly
        // Same numbers: a release beats its own pre-release (1.0.0 > 1.0.0-rc1).
        return '-' !in candidate && '-' in current
    }

    /** Channel tags deliberately keep nightlies invisible to older stable updaters. */
    fun releaseVersion(tag: String, nightly: Boolean): String? = if (nightly) {
        Regex("nightly-(\\d+\\.\\d+\\.\\d+)-(\\d+)").matchEntire(tag)?.let {
            "${it.groupValues[1]}-nightly.${it.groupValues[2]}"
        }
    } else {
        Regex("v\\d+\\.\\d+\\.\\d+").matchEntire(tag)?.value?.removePrefix("v")
    }

    /** The hex digest from a `sha256sum` line (`<hash>  <file>`), or null if malformed. */
    fun sha256Of(sumLine: String): String? =
        sumLine.trim().substringBefore(' ').lowercase().takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }
}
