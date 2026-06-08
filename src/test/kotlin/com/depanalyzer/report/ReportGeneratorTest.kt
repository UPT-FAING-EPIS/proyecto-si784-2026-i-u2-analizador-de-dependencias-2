package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.core.graph.VulnerabilityChain
import com.depanalyzer.parser.Ecosystem
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportGeneratorTest {

    private val generator = ReportGenerator()

    @Test
    fun `generates text report correctly`() {
        val affectedDep1 = AffectedDependency("com.h2database", "h2", "1.4.199")
        val affectedDep2 = AffectedDependency("org.yaml", "snakeyaml", "1.26")

        val report = DependencyReport(
            projectName = "TestProject",
            upToDate = listOf(DependencyInfo("org.slf4j", "slf4j-api", "2.0.13")),
            outdated = listOf(OutdatedDependency("junit", "junit", "4.12", "4.13.2")),
            directVulnerable = listOf(
                VulnerableDependency(
                    "com.h2database", "h2", "1.4.199",
                    listOf(
                        Vulnerability(
                            cveId = "CVE-2021-23463",
                            severity = VulnerabilitySeverity.CRITICAL,
                            cvssScore = 9.8,
                            description = "Remote Code Execution",
                            affectedDependency = affectedDep1,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = null
                        )
                    )
                )
            ),
            transitiveVulnerable = listOf(
                VulnerableDependency(
                    "org.yaml", "snakeyaml", "1.26",
                    listOf(
                        Vulnerability(
                            cveId = "CVE-2022-25857",
                            severity = VulnerabilitySeverity.HIGH,
                            cvssScore = 7.5,
                            description = "Denial of Service",
                            affectedDependency = affectedDep2,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = null
                        )
                    ),
                    dependencyChain = listOf("direct-dep", "snakeyaml")
                )
            )
        )

        val text = generator.toText(report)

        assertTrue(text.contains("TestProject"))
        assertTrue(text.contains("VULNERABILIDADES DETECTADAS"))
        assertTrue(text.contains("CVE-2021-23463"))
        assertTrue(text.contains("junit:junit: 4.12 -> 4.13.2"))
        assertTrue(text.contains("Ruta: direct-dep -> snakeyaml"))
        assertTrue(text.contains("Al día: 1"))
    }

    @Test
    fun `generates json report correctly`() {
        val report = DependencyReport(
            projectName = "TestProject",
            upToDate = listOf(DependencyInfo("g", "a", "1.0"))
        )

        val json = generator.toJson(report)

        assertTrue(json.contains("\"projectName\" : \"TestProject\""))
        assertTrue(json.contains("\"upToDate\" : ["))
    }

    @Test
    fun `generates parseable json for full report without null fields`() {
        val affectedDependency = AffectedDependency(
            groupId = "org.example",
            artifactId = "vulnerable-lib",
            version = "1.0.0",
            ecosystem = Ecosystem.MAVEN
        )
        val vulnerability = Vulnerability(
            cveId = "CVE-2026-0001",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 9.8,
            description = "Critical \"quoted\" vulnerability\nwith newline",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.BOTH,
            retrievedAt = Instant.parse("2026-06-08T12:30:00Z"),
            referenceUrl = "https://example.com/cve?id=1"
        )
        val rootNode = DependencyNode(
            id = "root",
            groupId = "org.example",
            artifactId = "root-lib",
            version = "2.0.0"
        )
        val childNode = DependencyNode(
            id = "child",
            groupId = "org.example",
            artifactId = "vulnerable-lib",
            version = "1.0.0",
            parent = rootNode,
            vulnerabilities = listOf(vulnerability)
        )
        rootNode.addChild(childNode)

        val report = DependencyReport(
            projectName = "Native JSON Project",
            upToDate = listOf(DependencyInfo("org.safe", "safe-lib", "1.0.0", Ecosystem.MAVEN)),
            outdated = listOf(OutdatedDependency("org.old", "old-lib", "1.0.0", "1.2.0", Ecosystem.NPM)),
            directVulnerable = listOf(
                VulnerableDependency(
                    groupId = "org.example",
                    artifactId = "vulnerable-lib",
                    version = "1.0.0",
                    vulnerabilities = listOf(vulnerability),
                    dependencyChain = listOf("org.example:root-lib:2.0.0", "org.example:vulnerable-lib:1.0.0"),
                    ecosystem = Ecosystem.MAVEN
                )
            ),
            vulnerabilityChains = listOf(
                VulnerabilityChain(
                    chain = listOf(rootNode, childNode),
                    vulnerabilities = listOf(vulnerability),
                    isShortestPath = true
                )
            ),
            dependencyTree = listOf(
                DependencyTreeNode(
                    groupId = "org.example",
                    artifactId = "root-lib",
                    currentVersion = "2.0.0",
                    latestVersion = "2.1.0",
                    isDirectDependency = true,
                    children = listOf(
                        DependencyTreeNode(
                            groupId = "org.example",
                            artifactId = "vulnerable-lib",
                            currentVersion = "1.0.0",
                            vulnerabilities = listOf(vulnerability)
                        )
                    )
                )
            )
        )

        val json = generator.toJson(report)
        val root = JsonMapper.builder().build().readTree(json)

        assertEquals("Native JSON Project", root.path("projectName").asText())
        assertEquals("org.safe", root.path("upToDate").get(0).path("groupId").asText())
        assertEquals("1.2.0", root.path("outdated").get(0).path("latestVersion").asText())
        assertEquals("CVE-2026-0001", root.path("directVulnerable").get(0).path("vulnerabilities").get(0).path("cveId").asText())
        assertEquals("2026-06-08T12:30:00Z", root.path("directVulnerable").get(0).path("vulnerabilities").get(0).path("retrievedAt").asText())
        assertEquals("child", root.path("vulnerabilityChains").get(0).path("chain").get(1).path("id").asText())
        assertEquals("org.example:vulnerable-lib:1.0.0", root.path("vulnerabilityChains").get(0).path("vulnerableNode").path("coordinate").asText())
        assertEquals("CVE-2026-0001", root.path("vulnerabilityChains").get(0).path("cveIds").get(0).asText())
        assertEquals("vulnerable-lib", root.path("dependencyTree").get(0).path("children").get(0).path("artifactId").asText())
        assertEquals("org.example:root-lib:2.0.0", root.path("dependencyTree").get(0).path("coordinate").asText())
        assertTrue(root.path("dependencyTree").get(0).path("hasOutdated").booleanValue())
        assertFalse(json.contains("\"referenceUrl\" : null"))
    }

    @Test
    fun `omits nullable dependency tree when absent`() {
        val json = generator.toJson(DependencyReport(projectName = "No Tree"))
        val root = JsonMapper.builder().build().readTree(json)

        assertEquals("No Tree", root.path("projectName").asText())
        assertTrue(root.path("dependencyTree").isMissingNode)
    }
}
