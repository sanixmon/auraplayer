package com.auraplayer.app.data.updater

data class ChangelogSection(
    val title: String,
    val items: List<String>
)

data class ChangelogRelease(
    val version: String,
    val date: String = "",
    val sections: List<ChangelogSection> = emptyList()
)

/**
 * Wraps Keep-a-Changelog Markdown (`CHANGELOG.md`) and GitHub Release notes into structured
 * release entries for clean rendering inside the Settings Updates & Changelog UI.
 */
object ChangelogParser {

    private val versionHeaderRegex = Regex("""^##\s+\[?v?([^\]\s]+)\]?(?:\s*[-–—]\s*(.+))?$""")
    private val sectionHeaderRegex = Regex("""^###\s+(.+)$""")
    private val bulletRegex = Regex("""^[-*+]\s+(.+)$""")

    fun parseChangelog(markdown: String): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var currentVersion: String? = null
        var currentDate = ""
        var currentSections = mutableListOf<ChangelogSection>()
        var currentSectionTitle: String? = null
        var currentItems = mutableListOf<String>()

        fun flushSection() {
            val title = currentSectionTitle
            if (title != null && currentItems.isNotEmpty()) {
                currentSections += ChangelogSection(title = title, items = currentItems.toList())
            }
            currentSectionTitle = null
            currentItems = mutableListOf()
        }

        fun flushRelease() {
            flushSection()
            val ver = currentVersion
            if (ver != null && currentSections.isNotEmpty()) {
                releases += ChangelogRelease(
                    version = ver,
                    date = currentDate,
                    sections = currentSections.toList()
                )
            }
            currentVersion = null
            currentDate = ""
            currentSections = mutableListOf()
        }

        for (rawLine in markdown.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("# ") || line == "---") continue

            val verMatch = versionHeaderRegex.matchEntire(line)
            if (verMatch != null) {
                flushRelease()
                currentVersion = verMatch.groupValues[1].trim()
                currentDate = verMatch.groupValues.getOrNull(2)?.trim().orEmpty()
                continue
            }

            if (currentVersion == null) continue

            val secMatch = sectionHeaderRegex.matchEntire(line)
            if (secMatch != null) {
                flushSection()
                val title = secMatch.groupValues[1].trim()
                if (!title.equals("Release Artifact", ignoreCase = true)) {
                    currentSectionTitle = title
                }
                continue
            }

            val bulletMatch = bulletRegex.matchEntire(line)
            if (bulletMatch != null && currentSectionTitle != null) {
                currentItems += cleanMarkdownInline(bulletMatch.groupValues[1].trim())
            }
        }

        flushRelease()
        return releases
    }

    fun parseReleaseNotes(
        version: String,
        body: String,
        publishedDate: String = ""
    ): ChangelogRelease {
        val sections = mutableListOf<ChangelogSection>()
        var currentSectionTitle = "Highlights"
        var currentItems = mutableListOf<String>()
        var skipSection = false

        fun flushSection() {
            if (!skipSection && currentItems.isNotEmpty()) {
                sections += ChangelogSection(title = currentSectionTitle, items = currentItems.toList())
            }
            currentItems = mutableListOf()
        }

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line == "---" || line.startsWith("|") || line.startsWith(">")) continue
            if (line.startsWith("## ")) continue

            val secMatch = sectionHeaderRegex.matchEntire(line)
            if (secMatch != null) {
                flushSection()
                val heading = secMatch.groupValues[1].trim()
                skipSection = heading.equals("Release Artifact", ignoreCase = true)
                if (!skipSection) {
                    currentSectionTitle = heading
                }
                continue
            }

            if (skipSection) continue

            val bulletMatch = bulletRegex.matchEntire(line)
            if (bulletMatch != null) {
                currentItems += cleanMarkdownInline(bulletMatch.groupValues[1].trim())
            }
        }

        flushSection()
        return ChangelogRelease(
            version = version.removePrefix("v"),
            date = publishedDate.take(10),
            sections = sections
        )
    }

    private fun cleanMarkdownInline(text: String): String {
        return text
            .replace("**", "")
            .replace("`", "")
            .trim()
    }
}
