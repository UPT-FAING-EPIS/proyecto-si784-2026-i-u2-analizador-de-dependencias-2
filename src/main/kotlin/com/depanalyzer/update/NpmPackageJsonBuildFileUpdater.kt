package com.depanalyzer.update

import com.depanalyzer.security.InputSafety
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

class NpmPackageJsonBuildFileUpdater : BuildFileUpdater {
    private val jsonMapper = JsonMapper.builder().build()

    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        if (!InputSafety.isSafeVersion(suggestion.newVersion)) return false
        if (!buildFile.exists() || !buildFile.isFile || buildFile.name != "package.json") return false

        val root = runCatching { jsonMapper.readTree(buildFile) as? ObjectNode }.getOrNull() ?: return false
        val packageName = if (suggestion.groupId == "npm") {
            suggestion.artifactId
        } else {
            "${suggestion.groupId}/${suggestion.artifactId}"
        }

        val updated = when (suggestion.targetType) {
            UpdateTargetType.DIRECT -> updateDirect(root, packageName, suggestion.newVersion)
            UpdateTargetType.TRANSITIVE_OVERRIDE -> updateOverride(root, packageName, suggestion.newVersion)
        }

        if (!updated) return false

        val pretty = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"
        buildFile.writeText(pretty)
        return true
    }

    private fun updateDirect(root: ObjectNode, packageName: String, newVersion: String): Boolean {
        val sections = listOf("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")

        sections.forEach { section ->
            val node = root.path(section)
            if (node is ObjectNode && node.has(packageName)) {
                val current = node.path(packageName).asText().trim()
                val replacement = preservePrefix(current, newVersion)
                node.put(packageName, replacement)
                return true
            }
        }

        return false
    }

    private fun updateOverride(root: ObjectNode, packageName: String, newVersion: String): Boolean {
        val overrides = when (val current = root.path("overrides")) {
            is ObjectNode -> current
            else -> root.putObject("overrides")
        }

        val existing = overrides.path(packageName).asText().trim()
        if (existing == newVersion) return false
        overrides.put(packageName, newVersion)
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
