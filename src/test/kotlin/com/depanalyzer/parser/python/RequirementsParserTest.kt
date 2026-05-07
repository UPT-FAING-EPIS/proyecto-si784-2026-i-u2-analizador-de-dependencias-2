package com.depanalyzer.parser.python

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequirementsParserTest {
    @Test
    fun `parses requirements entries`() {
        val dir = Files.createTempDirectory("requirements-parser")
        val file = dir.resolve("requirements.txt").toFile()
        file.writeText(
            """
            requests==2.32.3
            urllib3>=2.2,<3.0
            # comment
            """.trimIndent()
        )

        val parsed = RequirementsParser().parse(file)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.artifactId == "requests" && it.version == "==2.32.3" })
        assertTrue(parsed.any { it.artifactId == "urllib3" && it.version == ">=2.2,<3.0" })
    }
}
