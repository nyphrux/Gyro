package com.gyro.app.core.update

/** The latest GitHub release, as far as the updater needs it. */
data class ReleaseInfo(
    val tag: String,
    /** Tag cleaned to a bare version, e.g. "v1.2.0" → "1.2.0". */
    val version: String,
    /** Release title; often the same as the tag. */
    val name: String,
    /** Release notes as plain text (Markdown markers removed). Empty when unavailable. */
    val notes: String,
    /** Direct download for the attached APK, or null when the release has none. */
    val apkUrl: String?,
    /** APK size in bytes, or 0 when unknown. */
    val apkSize: Long,
    val pageUrl: String,
)

/** Turns GitHub-flavoured Markdown release notes into readable plain text. */
object ReleaseNotes {
    fun toPlainText(markdown: String): String = markdown
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { line ->
            line
                .replace(Regex("""^\s{0,3}#{1,6}\s*"""), "")          // headings
                .replace(Regex("""^(\s*)[-*+]\s+"""), "$1• ")          // bullets
                .replace(Regex("""!\[[^]]*]\([^)]*\)"""), "")          // images
                .replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")       // links → text
                .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")          // bold
                .replace(Regex("""(?<![\w*])[*_](?!\s)(.+?)(?<!\s)[*_](?![\w*])"""), "$1") // italics
                .replace("`", "")
                .trimEnd()
        }
        .replace(Regex("""<!--[\s\S]*?-->"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
