package com.depanalyzer.update

import com.depanalyzer.security.InputSafety
import java.io.File

class PyprojectBuildFileUpdater : BuildFileUpdater {
    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        if (!InputSafety.isSafeVersion(suggestion.newVersion)) return false
        if (!buildFile.exists() || !buildFile.isFile || buildFile.name != "pyproject.toml") return false

        val packageName = suggestion.artifactId
        val lines = buildFile.readLines().toMutableList()

        val updatedDirect = updateExistingDependency(lines, packageName, suggestion.newVersion)
        if (updatedDirect) {
            buildFile.writeText(lines.joinToString("\n") + "\n")
            return true
        }

        if (suggestion.targetType == UpdateTargetType.TRANSITIVE_OVERRIDE) {
            val inserted = insertInPoetryMainSection(lines, packageName, suggestion.newVersion)
            if (inserted) {
                buildFile.writeText(lines.joinToString("\n") + "\n")
                return true
            }
        }

        return false
    }

    private fun updateExistingDependency(lines: MutableList<String>, packageName: String, newVersion: String): Boolean {
        var section = ""
        for (index in lines.indices) {
            val line = lines[index]
            val clean = line.substringBefore('#').trim()
            if (clean.startsWith("[") && clean.endsWith("]")) {
                section = clean.removePrefix("[").removeSuffix("]")
                continue
            }

            val inPoetryDeps = section == "tool.poetry.dependencies" ||
                    (section.startsWith("tool.poetry.group.") && section.endsWith(".dependencies"))
            if (!inPoetryDeps) continue

            val key = clean.substringBefore('=', "").trim().trim('"', '\'')
            if (!key.equals(packageName, ignoreCase = true)) continue

            lines[index] = updateLineValue(line, newVersion)
            return true
        }

        return false
    }

    private fun updateLineValue(line: String, newVersion: String): String {
        val comment = if (line.contains('#')) line.substringAfter('#') else ""
        val beforeComment = line.substringBefore('#')

        val quotedValueRegex = Regex("""^\s*([^=]+)=\s*['\"]([^'\"]+)['\"]\s*$""")
        quotedValueRegex.matchEntire(beforeComment)?.let { match ->
            val keyPart = match.groupValues[1].trim()
            val currentValue = match.groupValues[2].trim()
            val replacement = preservePrefix(currentValue, newVersion)
            return "$keyPart = \"$replacement\"" + if (comment.isNotBlank()) " #$comment" else ""
        }

        val inlineVersionRegex = Regex("""(version\s*=\s*['\"])([^'\"]+)(['\"])""")
        if (inlineVersionRegex.containsMatchIn(beforeComment)) {
            val replaced = inlineVersionRegex.replace(beforeComment) { match ->
                val currentValue = match.groupValues[2].trim()
                val replacement = preservePrefix(currentValue, newVersion)
                "${match.groupValues[1]}$replacement${match.groupValues[3]}"
            }
            return replaced + if (comment.isNotBlank()) " #$comment" else ""
        }

        return line
    }

    private fun insertInPoetryMainSection(lines: MutableList<String>, packageName: String, newVersion: String): Boolean {
        var sectionStart = -1
        var insertIndex = -1

        for (index in lines.indices) {
            val clean = lines[index].substringBefore('#').trim()
            if (clean == "[tool.poetry.dependencies]") {
                sectionStart = index
                insertIndex = index + 1
                continue
            }

            if (sectionStart != -1) {
                if (clean.startsWith("[") && clean.endsWith("]")) {
                    break
                }
                insertIndex = index + 1
            }
        }

        if (sectionStart == -1 || insertIndex == -1) return false

        val newLine = "$packageName = \"$newVersion\""
        lines.add(insertIndex, newLine)
        return true
    }

    private fun preservePrefix(currentSpec: String, newVersion: String): String {
        val trimmed = currentSpec.trim()
        return when {
            trimmed.startsWith("^") -> "^$newVersion"
            trimmed.startsWith("~") -> "~$newVersion"
            trimmed.startsWith(">=") -> ">=$newVersion"
            trimmed.startsWith("<=") -> "<=$newVersion"
            trimmed.startsWith(">") -> ">$newVersion"
            trimmed.startsWith("<") -> "<$newVersion"
            trimmed.startsWith("=") -> "=$newVersion"
            else -> newVersion
        }
    }
}
