package com.depanalyzer.parser.npm

import com.depanalyzer.parser.DependencySection
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

class NpmPackageParser {
    private val jsonMapper = JsonMapper.builder().build()

    fun parse(packageJsonFile: File): List<ParsedDependency> {
        require(packageJsonFile.exists() && packageJsonFile.isFile) {
            "Invalid package.json path: ${packageJsonFile.absolutePath}"
        }
        require(packageJsonFile.name == "package.json") { "Expected package.json, got ${packageJsonFile.name}" }

        val root = jsonMapper.readTree(packageJsonFile)
        val result = mutableListOf<ParsedDependency>()

        result += parseSection(root.path("dependencies"), "dependencies")
        result += parseSection(root.path("devDependencies"), "devDependencies")
        result += parseSection(root.path("peerDependencies"), "peerDependencies")
        result += parseSection(root.path("optionalDependencies"), "optionalDependencies")

        return result.distinctBy { "${it.groupId}:${it.artifactId}" }
    }

    private fun parseSection(sectionNode: JsonNode, scope: String): List<ParsedDependency> {
        if (!sectionNode.isObject) return emptyList()

        return sectionNode.properties().asSequence().mapNotNull { entry ->
            val packageName = entry.key.trim()
            if (packageName.isBlank()) return@mapNotNull null

            val versionSpec = entry.value.textOrEmpty().takeIf { it.isNotEmpty() }
            val (groupId, artifactId) = packageName.toGroupArtifact()

            ParsedDependency(
                groupId = groupId,
                artifactId = artifactId,
                version = versionSpec,
                scope = scope,
                section = DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.NPM
            )
        }.toList()
    }

    private fun String.toGroupArtifact(): Pair<String, String> {
        return if (startsWith("@") && contains('/')) {
            substringBefore('/') to substringAfter('/')
        } else {
            "npm" to this
        }
    }

    private fun JsonNode.textOrEmpty(): String = scalarText().trim()

    private fun JsonNode.scalarText(): String = when {
        isNull || isMissingNode -> ""
        else -> toString().removeSurrounding("\"")
    }
}
