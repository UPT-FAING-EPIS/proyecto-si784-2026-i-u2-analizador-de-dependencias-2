package com.depanalyzer.parser.python

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.DependencySection
import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import java.io.File

object PythonIntegration {
    private val pyprojectParser = PyprojectPoetryParser()
    private val poetryLockParser = PoetryLockParser()
    private val requirementsParser = RequirementsParser()

    fun analyzePoetryProject(projectDir: File): Pair<List<ParsedDependency>, List<DependencyNode>> {
        val pyprojectFile = File(projectDir, "pyproject.toml")
        val directDependencies = if (pyprojectFile.exists()) {
            pyprojectParser.parse(pyprojectFile)
        } else {
            emptyList()
        }

        val lockFile = File(projectDir, "poetry.lock")
        val roots = poetryLockParser.parse(lockFile, directDependencies)

        val dependencies = if (roots.isNotEmpty()) {
            flatten(roots)
        } else {
            directDependencies
        }

        val rootNodes = if (roots.isNotEmpty()) {
            roots
        } else {
            directDependencies.map { dep ->
                DependencyNode(
                    id = "pypi:${dep.artifactId}:${dep.version}",
                    groupId = dep.groupId,
                    artifactId = dep.artifactId,
                    version = dep.version ?: "unknown",
                    scope = dep.scope,
                    ecosystem = Ecosystem.PYPI
                )
            }
        }

        return Pair(dependencies, rootNodes)
    }

    fun analyzeRequirementsProject(projectDir: File): Pair<List<ParsedDependency>, List<DependencyNode>> {
        val requirementsFile = File(projectDir, "requirements.txt")
        val dependencies = requirementsParser.parse(requirementsFile)

        val rootNodes = dependencies.map { dep ->
            DependencyNode(
                id = "pypi:${dep.artifactId}:${dep.version}",
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version ?: "unknown",
                scope = dep.scope,
                ecosystem = Ecosystem.PYPI
            )
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
                scope = node.scope ?: "main",
                section = DependencySection.DEPENDENCIES,
                ecosystem = Ecosystem.PYPI
            )
            node.children.forEach(::visit)
        }

        nodes.forEach(::visit)
        return result
    }
}
