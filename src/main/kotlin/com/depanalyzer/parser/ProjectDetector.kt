package com.depanalyzer.parser

import java.io.File
import java.nio.file.Path

class ProjectDetector {
    fun detect(directory: Path): ProjectType {
        val dirFile = directory.toFile()
        if (!dirFile.exists() || !dirFile.isDirectory) {
            throw IllegalArgumentException("The path provided is not a valid directory: $directory")
        }

        val gradleKotlinFiles = listOf("build.gradle.kts", "settings.gradle.kts")
        if (gradleKotlinFiles.any { File(dirFile, it).exists() }) {
            return ProjectType.GRADLE_KOTLIN
        }

        val gradleGroovyFiles = listOf("build.gradle", "settings.gradle")
        if (gradleGroovyFiles.any { File(dirFile, it).exists() }) {
            return ProjectType.GRADLE_GROOVY
        }

        if (File(dirFile, "pom.xml").exists()) {
            return ProjectType.MAVEN
        }

        if (File(dirFile, "package.json").exists()) {
            return ProjectType.NPM
        }

        if (File(dirFile, "pyproject.toml").exists() || File(dirFile, "poetry.lock").exists()) {
            return ProjectType.PYTHON_POETRY
        }

        if (File(dirFile, "requirements.txt").exists()) {
            return ProjectType.PYTHON_REQUIREMENTS
        }

        throw IllegalStateException(
            "No known build files (pom.xml, build.gradle, build.gradle.kts, package.json, pyproject.toml, requirements.txt) found in $directory"
        )
    }
}
