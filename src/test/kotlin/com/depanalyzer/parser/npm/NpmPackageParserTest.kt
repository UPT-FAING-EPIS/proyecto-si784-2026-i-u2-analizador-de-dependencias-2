package com.depanalyzer.parser.npm

import com.depanalyzer.parser.Ecosystem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpmPackageParserTest {
    @Test
    fun `parses dependencies and scoped packages`() {
        val dir = Files.createTempDirectory("npm-package-parser")
        val file = dir.resolve("package.json").toFile()
        file.writeText(
            """
            {
              "dependencies": {
                "lodash": "^4.17.21",
                "@types/node": "^22.0.0"
              },
              "devDependencies": {
                "typescript": "~5.9.2"
              }
            }
            """.trimIndent()
        )

        val parsed = NpmPackageParser().parse(file)

        assertEquals(3, parsed.size)
        assertTrue(parsed.any { it.groupId == "npm" && it.artifactId == "lodash" && it.ecosystem == Ecosystem.NPM })
        assertTrue(parsed.any { it.groupId == "@types" && it.artifactId == "node" && it.ecosystem == Ecosystem.NPM })
        assertTrue(parsed.any { it.groupId == "npm" && it.artifactId == "typescript" && it.scope == "devDependencies" })
    }
}
