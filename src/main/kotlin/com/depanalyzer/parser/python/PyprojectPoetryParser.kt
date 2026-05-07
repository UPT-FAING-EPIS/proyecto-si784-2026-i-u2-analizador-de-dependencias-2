package com.depanalyzer.parser.python

import com.depanalyzer.parser.DependencySection
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import java.io.File

class PyprojectPoetryParser {
    fun parse(pyprojectFile: File): List<ParsedDependency> {
        require(pyprojectFile.exists() && pyprojectFile.isFile) {
            "Invalid pyproject.toml path: ${pyprojectFile.absolutePath}"
        }
        require(pyprojectFile.name == "pyproject.toml") { "Expected pyproject.toml, got ${pyprojectFile.name}" }

        val lines = pyprojectFile.readLines()
        val result = mutableListOf<ParsedDependency>()

        var section = ""
        lines.forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isBlank()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removePrefix("[").removeSuffix("]").trim()
                return@forEach
            }

            val scope = when {
                section == "tool.poetry.dependencies" -> "main"
                section.startsWith("tool.poetry.group.") && section.endsWith(".dependencies") -> {
                    section.removePrefix("tool.poetry.group.").removeSuffix(".dependencies")
                }

                else -> null
            } ?: return@forEach

            val name = line.substringBefore('=').trim().trim('"', '\'')
            if (name.isBlank() || name.equals("python", ignoreCase = true)) return@forEach

            val value = line.substringAfter('=', "").trim()
            val version = extractPoetryVersion(value)
            val normalizedName = normalizePackageName(name)

            result += ParsedDependency(
                groupId = "pypi",
                artifactId = normalizedName,
                version = version,
                scope = scope,
                section = DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.PYPI
            )
        }

        val pep621Deps = parsePep621Dependencies(pyprojectFile.readText())
        result += pep621Deps

        return result.distinctBy { "${it.groupId}:${it.artifactId}" }
    }

    private fun parsePep621Dependencies(content: String): List<ParsedDependency> {
        val dependenciesArrayRegex = Regex(
            """(?s)\[project]\s*.*?dependencies\s*=\s*\[(.*?)]"""
        )
        val match = dependenciesArrayRegex.find(content) ?: return emptyList()
        val body = match.groupValues[1]

        val itemRegex = Regex("""['\"]([^'\"]+)['\"]""")
        return itemRegex.findAll(body).mapNotNull { item ->
            val declaration = item.groupValues[1].trim()
            val splitIndex = declaration.indexOfFirst { it in charArrayOf('<', '>', '=', '!', '~', ' ') }
            val rawName = if (splitIndex >= 0) declaration.substring(0, splitIndex) else declaration
            val name = rawName.trim().substringBefore('[')
            if (name.isBlank()) return@mapNotNull null

            val version = declaration.removePrefix(name).trim().takeIf { it.isNotBlank() }
            ParsedDependency(
                groupId = "pypi",
                artifactId = normalizePackageName(name),
                version = version,
                scope = "main",
                section = DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.PYPI
            )
        }.toList()
    }

    private fun extractPoetryVersion(rawValue: String): String? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null

        if (value.startsWith("\"") || value.startsWith("'")) {
            return value.trim('"', '\'').takeIf { it.isNotBlank() }
        }

        val versionRegex = Regex("""version\s*=\s*['\"]([^'\"]+)['\"]""")
        return versionRegex.find(value)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun normalizePackageName(raw: String): String {
        return raw.trim().lowercase().replace('_', '-')
    }
}
