package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.core.graph.VulnerabilityChain

class ReportGenerator {
    fun toJson(report: DependencyReport): String {
        return JsonReportWriter().writeReport(report)
    }

    fun toJsonVerbose(report: DependencyReport): String {
        return toJson(report)
    }

    fun toText(report: DependencyReport): String {
        val sb = StringBuilder()
        sb.appendLine("====================================================")
        sb.appendLine("Análisis de Dependencias: ${report.projectName}")
        sb.appendLine("====================================================")
        sb.appendLine()

        if (report.directVulnerable.isNotEmpty() || report.transitiveVulnerable.isNotEmpty()) {
            sb.appendLine("VULNERABILIDADES DETECTADAS")
            sb.appendLine("---------------------------")

            if (report.directVulnerable.isNotEmpty()) {
                sb.appendLine("[Directas]")
                report.directVulnerable.forEach { dep ->
                    sb.appendLine("  - ${dep.groupId}:${dep.artifactId}:${dep.version}")
                    dep.vulnerabilities.forEach { v ->
                        val desc = v.description ?: "No description available"
                        sb.appendLine("    * [${v.severity}] ${v.cveId}: $desc")
                    }
                }
                sb.appendLine()
            }

            if (report.transitiveVulnerable.isNotEmpty()) {
                sb.appendLine("[Transitivas]")
                report.transitiveVulnerable.forEach { dep ->
                    sb.appendLine("  - ${dep.groupId}:${dep.artifactId}:${dep.version}")
                    if (dep.dependencyChain != null) {
                        sb.appendLine("    Ruta: ${dep.dependencyChain.joinToString(" -> ")}")
                    }
                    dep.vulnerabilities.forEach { v ->
                        val desc = v.description ?: "No description available"
                        sb.appendLine("    * [${v.severity}] ${v.cveId}: $desc")
                    }
                }
                sb.appendLine()
            }
        }

        if (report.outdated.isNotEmpty()) {
            sb.appendLine("DEPENDENCIAS DESACTUALIZADAS")
            sb.appendLine("----------------------------")
            report.outdated.forEach { dep ->
                sb.appendLine("  - ${dep.groupId}:${dep.artifactId}: ${dep.currentVersion} -> ${dep.latestVersion}")
            }
            sb.appendLine()
        }

        sb.appendLine("RESUMEN")
        sb.appendLine("-------")
        sb.appendLine("  Al día: ${report.upToDate.size}")
        sb.appendLine("  Desactualizadas: ${report.outdated.size}")
        sb.appendLine("  Vulnerabilidades directas: ${report.directVulnerable.size}")
        sb.appendLine("  Vulnerabilidades transitivas: ${report.transitiveVulnerable.size}")
        sb.appendLine("====================================================")

        return sb.toString()
    }

    private fun renderTreeNode(sb: StringBuilder, node: DependencyTreeNode, level: Int, useAscii: Boolean) {
        val indent = "  ".repeat(level)
        val prefix = if (useAscii) {
            if (level == 0) "" else "|"
        } else {
            if (level == 0) "" else "│"
        }

        val marker = if (node.isDirectDependency) {
            if (useAscii) "[DIRECT]" else "🔴"
        } else {
            if (useAscii) "[TRANSITIVE]" else "🟡"
        }

        val nodeLabel = "${node.groupId}:${node.artifactId}:${node.currentVersion}"
        sb.appendLine("$indent$prefix$marker $nodeLabel")

        if (node.latestVersion != null) {
            val updateMarker = if (useAscii) "[UPDATE]" else "⬆️"
            val updateIndent = if (level == 0) "" else "  ".repeat(level) + "|  "
            sb.appendLine("$updateIndent$updateMarker Disponible: ${node.latestVersion}")
        }

        node.vulnerabilities.forEach { vuln ->
            val vulnMarker = when (vuln.severity) {
                VulnerabilitySeverity.CRITICAL -> if (useAscii) "[CRITICAL]" else "🔴"
                VulnerabilitySeverity.HIGH -> if (useAscii) "[HIGH]" else "🟠"
                VulnerabilitySeverity.MEDIUM -> if (useAscii) "[MEDIUM]" else "🟡"
                VulnerabilitySeverity.LOW -> if (useAscii) "[LOW]" else "🟢"
                VulnerabilitySeverity.UNKNOWN -> if (useAscii) "[UNKNOWN]" else "⚪"
            }
            val cvssStr = vuln.cvssScore?.let { " (${it})" } ?: ""
            val vulnIndent = if (level == 0) "" else "  ".repeat(level) + "|  "
            sb.appendLine("$vulnIndent$vulnMarker [${vuln.cveId}] ${vuln.severity}$cvssStr")
        }

        node.children.forEach { child ->
            renderTreeNode(sb, child, level + 1, useAscii)
        }
    }
}

private class JsonReportWriter {
    private val sb = StringBuilder()
    private var level = 0

    fun writeReport(report: DependencyReport): String {
        obj {
            field("projectName", report.projectName)
            field("upToDate", report.upToDate) { writeDependencyInfo(it) }
            field("outdated", report.outdated) { writeOutdatedDependency(it) }
            field("directVulnerable", report.directVulnerable) { writeVulnerableDependency(it) }
            field("transitiveVulnerable", report.transitiveVulnerable) { writeVulnerableDependency(it) }
            field("vulnerabilityChains", report.vulnerabilityChains) { writeVulnerabilityChain(it) }
            report.dependencyTree?.let { field("dependencyTree", it) { node -> writeDependencyTreeNode(node) } }
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun writeDependencyInfo(dep: DependencyInfo) = obj {
        field("groupId", dep.groupId)
        field("artifactId", dep.artifactId)
        field("version", dep.version)
        field("ecosystem", dep.ecosystem.name)
    }

    private fun writeOutdatedDependency(dep: OutdatedDependency) = obj {
        field("groupId", dep.groupId)
        field("artifactId", dep.artifactId)
        field("currentVersion", dep.currentVersion)
        field("latestVersion", dep.latestVersion)
        field("ecosystem", dep.ecosystem.name)
    }

    private fun writeVulnerableDependency(dep: VulnerableDependency) = obj {
        field("groupId", dep.groupId)
        field("artifactId", dep.artifactId)
        field("version", dep.version)
        field("vulnerabilities", dep.vulnerabilities) { writeVulnerability(it) }
        dep.dependencyChain?.let { field("dependencyChain", it) { value -> string(value) } }
        field("ecosystem", dep.ecosystem.name)
    }

    private fun writeVulnerability(vulnerability: Vulnerability) = obj {
        field("cveId", vulnerability.cveId)
        field("severity", vulnerability.severity.name)
        vulnerability.cvssScore?.let { field("cvssScore", it) }
        vulnerability.description?.let { field("description", it) }
        field("affectedDependency") { writeAffectedDependency(vulnerability.affectedDependency) }
        field("source", vulnerability.source.name)
        vulnerability.retrievedAt?.let { field("retrievedAt", it.toString()) }
        vulnerability.referenceUrl?.let { field("referenceUrl", it) }
    }

    private fun writeAffectedDependency(dep: AffectedDependency) = obj {
        field("groupId", dep.groupId)
        field("artifactId", dep.artifactId)
        field("version", dep.version)
        field("ecosystem", dep.ecosystem.name)
    }

    private fun writeDependencyTreeNode(node: DependencyTreeNode): Unit = obj {
        field("groupId", node.groupId)
        field("artifactId", node.artifactId)
        field("currentVersion", node.currentVersion)
        node.latestVersion?.let { field("latestVersion", it) }
        field("isDirectDependency", node.isDirectDependency)
        field("isDependencyManagement", node.isDependencyManagement)
        node.scope?.let { field("scope", it) }
        field("vulnerabilities", node.vulnerabilities) { writeVulnerability(it) }
        field("children", node.children) { writeDependencyTreeNode(it) }
        node.dependencyChain?.let { field("dependencyChain", it) { value -> string(value) } }
        field("ecosystem", node.ecosystem.name)
        field("coordinate", node.coordinate)
        field("hasOutdated", node.hasOutdated)
        field("hasVulnerabilities", node.hasVulnerabilities)
        field("hasProblems", node.hasProblems)
        node.maxSeverity?.let { field("maxSeverity", it.name) }
        field("depth", node.depth)
    }

    private fun writeVulnerabilityChain(chain: VulnerabilityChain) = obj {
        field("chain", chain.chain) { writeDependencyNode(it) }
        field("vulnerabilities", chain.vulnerabilities) { writeVulnerability(it) }
        field("isShortestPath", chain.isShortestPath)
        field("classification", chain.classification.name)
        field("directDependency") { writeDependencyNode(chain.directDependency) }
        field("vulnerableNode") { writeDependencyNode(chain.vulnerableNode) }
        field("depth", chain.depth)
        field("cveIds", chain.cveIds) { value -> string(value) }
    }

    private fun writeDependencyNode(node: DependencyNode) = obj {
        field("id", node.id)
        field("groupId", node.groupId)
        field("artifactId", node.artifactId)
        field("version", node.version)
        node.scope?.let { field("scope", it) }
        field("isDependencyManagement", node.isDependencyManagement)
        field("ecosystem", node.ecosystem.name)
        field("coordinate", node.coordinate)
    }

    private fun obj(body: JsonObjectScope.() -> Unit) {
        sb.append('{')
        level++
        JsonObjectScope().body()
        level--
        newline()
        sb.append(indent()).append('}')
    }

    private fun <T> array(items: List<T>, writeItem: (T) -> Unit) {
        sb.append('[')
        if (items.isNotEmpty()) {
            level++
            items.forEachIndexed { index, item ->
                newline()
                sb.append(indent())
                writeItem(item)
                if (index != items.lastIndex) sb.append(',')
            }
            level--
            newline()
            sb.append(indent())
        }
        sb.append(']')
    }

    private fun string(value: String) {
        sb.append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u")
                    sb.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    private fun int(value: Int) {
        sb.append(value)
    }

    private fun number(value: Double) {
        require(value.isFinite()) { "JSON does not support non-finite numbers: $value" }
        sb.append(value)
    }

    private fun bool(value: Boolean) {
        sb.append(value)
    }

    private fun newline() {
        sb.append('\n')
    }

    private fun indent(): String = "  ".repeat(level)

    private inner class JsonObjectScope {
        private var first = true

        fun field(name: String, value: String) = field(name) { string(value) }
        fun field(name: String, value: Int) = field(name) { int(value) }
        fun field(name: String, value: Double) = field(name) { number(value) }
        fun field(name: String, value: Boolean) = field(name) { bool(value) }
        fun <T> field(name: String, values: List<T>, writeItem: (T) -> Unit) = field(name) {
            array(values, writeItem)
        }

        fun field(name: String, writeValue: () -> Unit) {
            if (!first) sb.append(',')
            newline()
            sb.append(indent()).append("  ")
            string(name)
            sb.append(" : ")
            writeValue()
            first = false
        }
    }
}
