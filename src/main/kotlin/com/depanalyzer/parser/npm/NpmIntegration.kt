package com.depanalyzer.parser.npm

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import java.io.File

object NpmIntegration {
    private val packageParser = NpmPackageParser()
    private val lockParser = NpmLockParser()

    fun analyzeNpmProject(projectDir: File): Pair<List<ParsedDependency>, List<DependencyNode>> {
        val packageJsonFile = File(projectDir, "package.json")
        val directDeclared = packageParser.parse(packageJsonFile)
        val directPackageNames = directDeclared.map { it.packageName }.toSet()

        val lockFile = File(projectDir, "package-lock.json")
        val roots = lockParser.parse(lockFile, directPackageNames)

        val dependencies = if (roots.isNotEmpty()) {
            flatten(roots)
        } else {
            directDeclared.map {
                ParsedDependency(
                    groupId = it.groupId,
                    artifactId = it.artifactId,
                    version = it.version,
                    scope = it.scope,
                    section = it.section,
                    ecosystem = Ecosystem.NPM
                )
            }
        }

        val rootNodes = if (roots.isNotEmpty()) {
            roots
        } else {
            directDeclared.map { dep ->
                DependencyNode(
                    id = "${dep.groupId}:${dep.artifactId}:${dep.version}",
                    groupId = dep.groupId,
                    artifactId = dep.artifactId,
                    version = dep.version ?: "unknown",
                    scope = dep.scope,
                    ecosystem = Ecosystem.NPM
                )
            }
        }

        return Pair(dependencies, rootNodes)
    }

    private fun flatten(nodes: List<DependencyNode>): List<ParsedDependency> {
        val result = mutableListOf<ParsedDependency>()

        fun visit(node: DependencyNode) {
            result += ParsedDependency(
                groupId = node.groupId,
                artifactId = node.artifactId,
                version = node.version,
                scope = node.scope ?: "dependencies",
                section = com.depanalyzer.parser.DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.NPM
            )
            node.children.forEach(::visit)
        }

        nodes.forEach(::visit)
        return result
    }
}
