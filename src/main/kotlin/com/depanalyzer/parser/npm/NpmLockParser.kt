package com.depanalyzer.parser.npm

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.Ecosystem
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

class NpmLockParser {
    private val jsonMapper = JsonMapper.builder().build()

    fun parse(
        packageLockFile: File,
        directPackageNames: Set<String>
    ): List<DependencyNode> {
        if (!packageLockFile.exists() || !packageLockFile.isFile) return emptyList()
        if (packageLockFile.name != "package-lock.json") return emptyList()

        val root = runCatching { jsonMapper.readTree(packageLockFile) }.getOrNull() ?: return emptyList()

        val packagesNode = root.path("packages")
        return if (packagesNode.isObject) {
            parsePackagesFormat(packagesNode, directPackageNames)
        } else {
            parseLegacyFormat(root.path("dependencies"), directPackageNames)
        }
    }

    private fun parsePackagesFormat(
        packagesNode: JsonNode,
        directPackageNames: Set<String>
    ): List<DependencyNode> {
        data class LockEntry(
            val packageName: String,
            val version: String,
            val dependencies: Set<String>
        )

        val byPath = mutableMapOf<String, LockEntry>()
        val preferredByName = mutableMapOf<String, LockEntry>()

        packagesNode.properties().forEach { (path, value) ->
            if (!value.isObject || path.isBlank()) return@forEach

            val packageName = value.path("name").textOrEmpty().ifBlank {
                extractPackageNameFromPath(path)
            }
            val version = value.path("version").textOrEmpty()
            if (packageName.isBlank() || version.isBlank()) return@forEach

            val deps = mutableSetOf<String>()
            deps += value.path("dependencies").propertyNames().toSet()
            deps += value.path("optionalDependencies").propertyNames().toSet()
            deps += value.path("peerDependencies").propertyNames().toSet()

            val entry = LockEntry(packageName = packageName, version = version, dependencies = deps)
            byPath[path] = entry

            val topLevelPath = "node_modules/$packageName"
            if (path == topLevelPath || packageName !in preferredByName) {
                preferredByName[packageName] = entry
            }
        }

        val rootDeclaredDeps = mutableSetOf<String>()
        val rootPackage = packagesNode.path("")
        if (rootPackage.isObject) {
            rootDeclaredDeps += rootPackage.path("dependencies").propertyNames().toSet()
            rootDeclaredDeps += rootPackage.path("devDependencies").propertyNames().toSet()
            rootDeclaredDeps += rootPackage.path("optionalDependencies").propertyNames().toSet()
            rootDeclaredDeps += rootPackage.path("peerDependencies").propertyNames().toSet()
        }

        val roots = when {
            directPackageNames.isNotEmpty() -> directPackageNames
            rootDeclaredDeps.isNotEmpty() -> rootDeclaredDeps
            else -> preferredByName.keys
        }

        fun buildNode(name: String, visiting: MutableSet<String>): DependencyNode? {
            val entry = preferredByName[name] ?: return null
            val key = "$name@${entry.version}"
            if (!visiting.add(key)) return null

            val (groupId, artifactId) = toGroupArtifact(name)
            val node = DependencyNode(
                id = "$groupId:$artifactId:${entry.version}",
                groupId = groupId,
                artifactId = artifactId,
                version = entry.version,
                scope = "dependencies",
                ecosystem = Ecosystem.NPM
            )

            entry.dependencies.forEach { childName ->
                buildNode(childName, visiting)?.let(node::addChild)
            }

            visiting.remove(key)
            return node
        }

        return roots.mapNotNull { buildNode(it, mutableSetOf()) }
    }

    private fun parseLegacyFormat(
        dependenciesNode: JsonNode,
        directPackageNames: Set<String>
    ): List<DependencyNode> {
        if (!dependenciesNode.isObject) return emptyList()

        fun parseNode(name: String, node: JsonNode, visiting: MutableSet<String>): DependencyNode? {
            if (!node.isObject) return null
            val version = node.path("version").textOrEmpty()
            if (version.isBlank()) return null

            val key = "$name@$version"
            if (!visiting.add(key)) return null

            val (groupId, artifactId) = toGroupArtifact(name)
            val result = DependencyNode(
                id = "$groupId:$artifactId:$version",
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                scope = "dependencies",
                ecosystem = Ecosystem.NPM
            )

            node.path("dependencies").properties().forEach { (childName, childNode) ->
                parseNode(childName, childNode, visiting)?.let(result::addChild)
            }

            visiting.remove(key)
            return result
        }

        val roots = directPackageNames.ifEmpty {
            dependenciesNode.propertyNames().toSet()
        }

        return roots.mapNotNull { name ->
            val node = dependenciesNode.path(name)
            if (node.isMissingNode) null else parseNode(name, node, mutableSetOf())
        }
    }

    private fun extractPackageNameFromPath(path: String): String {
        if (!path.contains("node_modules/")) return ""
        val lastSegment = path.substringAfterLast("node_modules/")
        return if (lastSegment.startsWith("@")) {
            val parts = lastSegment.split('/')
            if (parts.size >= 2) "${parts[0]}/${parts[1]}" else lastSegment
        } else {
            lastSegment.substringBefore('/')
        }
    }

    private fun JsonNode.textOrEmpty(): String = scalarText().trim()

    private fun JsonNode.scalarText(): String = when {
        isNull || isMissingNode -> ""
        else -> toString().removeSurrounding("\"")
    }

    private fun toGroupArtifact(packageName: String): Pair<String, String> {
        return if (packageName.startsWith("@") && packageName.contains('/')) {
            packageName.substringBefore('/') to packageName.substringAfter('/')
        } else {
            "npm" to packageName
        }
    }
}
