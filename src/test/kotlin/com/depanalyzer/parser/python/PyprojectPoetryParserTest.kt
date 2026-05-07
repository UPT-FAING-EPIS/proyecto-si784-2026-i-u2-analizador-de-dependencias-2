package com.depanalyzer.parser.python

import com.depanalyzer.parser.Ecosystem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PyprojectPoetryParserTest {
    @Test
    fun `parses poetry dependencies and groups`() {
        val dir = Files.createTempDirectory("poetry-parser")
        val file = dir.resolve("pyproject.toml").toFile()
        file.writeText(
            """
            [tool.poetry.dependencies]
            python = "^3.11"
            requests = "^2.32.0"

            [tool.poetry.group.dev.dependencies]
            pytest = "^8.0.0"
            """.trimIndent()
        )

        val parsed = PyprojectPoetryParser().parse(file)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.artifactId == "requests" && it.scope == "main" && it.ecosystem == Ecosystem.PYPI })
        assertTrue(parsed.any { it.artifactId == "pytest" && it.scope == "dev" && it.ecosystem == Ecosystem.PYPI })
    }
}
