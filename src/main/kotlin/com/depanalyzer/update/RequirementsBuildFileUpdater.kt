package com.depanalyzer.update

import com.depanalyzer.security.InputSafety
import java.io.File

class RequirementsBuildFileUpdater : BuildFileUpdater {
    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        if (!InputSafety.isSafeVersion(suggestion.newVersion)) return false
        if (!buildFile.exists() || !buildFile.isFile || buildFile.name != "requirements.txt") return false

        val packageName = suggestion.artifactId
        val lines = buildFile.readLines().toMutableList()
        var changed = false

        for (index in lines.indices) {
            val original = lines[index]
            val clean = original.substringBefore('#').trim()
            if (clean.isBlank()) continue

            val nameMatch = Regex("""^([A-Za-z0-9_.-]+)""").find(clean) ?: continue
            val currentName = nameMatch.groupValues[1].lowercase().replace('_', '-')
            if (!currentName.equals(packageName, ignoreCase = true)) continue

            val suffixComment = if (original.contains('#')) " #${original.substringAfter('#').trim()}" else ""
            lines[index] = "$currentName==${suggestion.newVersion}$suffixComment"
            changed = true
            break
        }

        if (!changed && suggestion.targetType == UpdateTargetType.TRANSITIVE_OVERRIDE) {
            lines.add("$packageName==${suggestion.newVersion}")
            changed = true
        }

        if (!changed) return false
        buildFile.writeText(lines.joinToString("\n") + "\n")
        return true
    }
}
