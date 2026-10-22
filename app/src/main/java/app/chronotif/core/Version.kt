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
        // Same numbers: a release beats its own pre-release (1.0.0 > 1.0.0-rc1).
        return '-' !in candidate && '-' in current
    }

    /** The hex digest from a `sha256sum` line (`<hash>  <file>`), or null if malformed. */
    fun sha256Of(sumLine: String): String? =
        sumLine.trim().substringBefore(' ').lowercase().takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }
}
