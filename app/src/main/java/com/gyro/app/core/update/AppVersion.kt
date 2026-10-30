package com.gyro.app.core.update

/**
 * Semantic-version comparison for release tags. Accepts the forms GitHub tags tend to take
 * ("1.2.0", "v1.2", "Gyro 1.3.0-beta.2") by reading the first dotted number in the string.
 * A pre-release ("1.2.0-beta.1") sorts before its final release ("1.2.0").
 */
object AppVersion {
    private val pattern = Regex("""(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.\-]+))?""")

    /** True only when [candidate] parses and is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return true
        return compare(a, b) > 0
    }

    /** "v1.2.0" → "1.2.0"; unparseable input is returned trimmed. */
    fun clean(tag: String): String = pattern.find(tag)?.value ?: tag.trim()

    private data class Parsed(val numbers: List<Int>, val preRelease: List<String>?)

    private fun parse(version: String): Parsed? {
        val match = pattern.find(version) ?: return null
        val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }
        val pre = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')
        return Parsed(numbers, pre)
    }

    private fun compare(a: Parsed, b: Parsed): Int {
        val length = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until length) {
            val diff = a.numbers.getOrElse(i) { 0 } - b.numbers.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return when {
            a.preRelease == null && b.preRelease == null -> 0
            a.preRelease == null -> 1 // final release beats its pre-releases
            b.preRelease == null -> -1
            else -> comparePreRelease(a.preRelease, b.preRelease)
        }
    }

    /** SemVer §11: numeric identifiers compare numerically and rank below alphanumeric ones. */
    private fun comparePreRelease(a: List<String>, b: List<String>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val diff = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
