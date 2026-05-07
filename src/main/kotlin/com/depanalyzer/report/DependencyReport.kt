package com.depanalyzer.report

import com.depanalyzer.core.graph.VulnerabilityChain
import com.depanalyzer.parser.Ecosystem
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyReport(
    val projectName: String,
    val upToDate: List<DependencyInfo> = emptyList(),
    val outdated: List<OutdatedDependency> = emptyList(),
    val directVulnerable: List<VulnerableDependency> = emptyList(),
    val transitiveVulnerable: List<VulnerableDependency> = emptyList(),
    val vulnerabilityChains: List<VulnerabilityChain> = emptyList(),
    val dependencyTree: List<DependencyTreeNode>? = null  // Tree structure (nuevo)
)

data class DependencyInfo(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
)

data class OutdatedDependency(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val latestVersion: String,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
)

data class VulnerableDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val vulnerabilities: List<Vulnerability>,
    val dependencyChain: List<String>? = null,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
)
