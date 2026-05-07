package com.depanalyzer.parser

enum class DependencySection {
    DEPENDENCIES,
    DEPENDENCY_MANAGEMENT
}

data class ParsedDependency(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String,
    val section: DependencySection,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
) {
    val coordinateKey: String
        get() = "$groupId:$artifactId:$version"

    val packageName: String
        get() = when (ecosystem) {
            Ecosystem.MAVEN -> "$groupId:$artifactId"
            Ecosystem.NPM -> if (groupId == "npm") artifactId else "$groupId/$artifactId"
            Ecosystem.PYPI -> artifactId
        }
}
