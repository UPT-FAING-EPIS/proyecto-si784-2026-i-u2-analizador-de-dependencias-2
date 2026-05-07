package com.depanalyzer.parser.python

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import java.io.File

class PoetryLockParser {
    fun parse(poetryLockFile: File, directDependencies: List<ParsedDependency>): List<DependencyNode> {
        if (!poetryLockFile.exists() || !poetryLockFile.isFile) return emptyList()
        if (poetryLockFile.name != "poetry.lock") return emptyList()

        val content = poetryLockFile.readText()
        val blocks = extractPackageBlocks(content)
        if (blocks.isEmpty()) return emptyList()

        data class LockPackage(val name: String, val version: String, val dependencies: Set<String>)

        val packageMap = mutableMapOf<String, LockPackage>()
        blocks.forEach { block ->
            val name = Regex("""(?m)^name\s*=\s*['\"]([^'\"]+)['\"]""").find(block)
                ?.groupValues?.get(1)
                ?.trim()
                ?.lowercase()
                ?.replace('_', '-')
                ?: return@forEach
            val version = Regex("""(?m)^version\s*=\s*['\"]([^'\"]+)['\"]""").find(block)
                ?.groupValues?.get(1)
                ?.trim()
                ?: return@forEach

            val dependencies = extractDependencies(block)
            packageMap[name] = LockPackage(name, version, dependencies)
        }

        if (packageMap.isEmpty()) return emptyList()

        val scopeByName = directDependencies.associate { dep ->
            dep.artifactId.lowercase().replace('_', '-') to dep.scope
        }

        val rootNames = if (directDependencies.isNotEmpty()) {
            directDependencies.map { it.artifactId.lowercase().replace('_', '-') }.toSet()
        } else {
            packageMap.keys
        }

        fun buildNode(name: String, visiting: MutableSet<String>): DependencyNode? {
            val lockPackage = packageMap[name] ?: return null
            val key = "$name@${lockPackage.version}"
            if (!visiting.add(key)) return null

            val node = DependencyNode(
                id = "pypi:${lockPackage.name}:${lockPackage.version}",
                groupId = "pypi",
                artifactId = lockPackage.name,
                version = lockPackage.version,
                scope = scopeByName[lockPackage.name] ?: "main",
                ecosystem = Ecosystem.PYPI
            )

            lockPackage.dependencies.forEach { dependencyName ->
                buildNode(dependencyName, visiting)?.let(node::addChild)
            }

            visiting.remove(key)
            return node
        }

        return rootNames.mapNotNull { buildNode(it, mutableSetOf()) }
    }

    private fun extractPackageBlocks(content: String): List<String> {
        val parts = content.split("[[package]]")
        return parts.drop(1).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractDependencies(block: String): Set<String> {
        val dependencies = mutableSetOf<String>()
        var inDependenciesSection = false

        block.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                inDependenciesSection = line == "[package.dependencies]"
                return@forEach
            }

            if (!inDependenciesSection) return@forEach

            val depName = line.substringBefore('=').trim().trim('"', '\'').lowercase().replace('_', '-')
            if (depName.isNotBlank()) {
                dependencies += depName
            }
        }

        return dependencies
    }
}
