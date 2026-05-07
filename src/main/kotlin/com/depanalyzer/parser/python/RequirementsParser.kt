package com.depanalyzer.parser.python

import com.depanalyzer.parser.DependencySection
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import java.io.File

class RequirementsParser {
    fun parse(requirementsFile: File): List<ParsedDependency> {
        require(requirementsFile.exists() && requirementsFile.isFile) {
            "Invalid requirements.txt path: ${requirementsFile.absolutePath}"
        }

        return requirementsFile.readLines().mapNotNull { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isBlank()) return@mapNotNull null
            if (line.startsWith("-r ") || line.startsWith("--requirement")) return@mapNotNull null
            if (line.startsWith("-e ")) return@mapNotNull null

            val cleaned = line.substringBefore(';').trim()
            val nameMatch = Regex("""^([A-Za-z0-9_.-]+)""").find(cleaned) ?: return@mapNotNull null
            val rawName = nameMatch.groupValues[1]
            val name = rawName.lowercase().replace('_', '-')
            val remainder = cleaned.removePrefix(rawName).trim().takeIf { it.isNotBlank() }

            ParsedDependency(
                groupId = "pypi",
                artifactId = name,
                version = remainder,
                scope = "main",
                section = DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.PYPI
            )
        }.distinctBy { "${it.groupId}:${it.artifactId}" }
    }
}
