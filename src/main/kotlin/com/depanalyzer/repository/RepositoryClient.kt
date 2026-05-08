package com.depanalyzer.repository

import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.security.InputSafety
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class RepositoryClient(
    connectTimeoutSeconds: Long = 10,
    readTimeoutSeconds: Long = 10,
    private val trustedCredentialHosts: Set<String> =
        InputSafety.parseTrustedCredentialHosts(System.getenv(InputSafety.CREDENTIAL_HOST_ALLOWLIST_ENV)),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
) {
    private data class MavenMetadataValues(
        val latest: String?,
        val release: String?,
        val versions: List<String>
    )

    private val jsonMapper = JsonMapper.builder().build()

    fun getLatestVersion(dependency: ParsedDependency, repositories: List<ProjectRepository> = emptyList()): String? {
        return when (dependency.ecosystem) {
            Ecosystem.MAVEN -> repositories.firstNotNullOfOrNull { repo ->
                getLatestVersion(repo, dependency.groupId, dependency.artifactId)
            }

            Ecosystem.NPM -> {
                val packageName = dependency.packageName
                getLatestNpmVersion(packageName)
            }

            Ecosystem.PYPI -> {
                getLatestPypiVersion(dependency.packageName)
            }
        }
    }

    fun getLatestVersion(repository: ProjectRepository, groupId: String, artifactId: String): String? {
        val metadata = fetchMetadata(repository, groupId, artifactId) ?: return null
        val candidates = buildList {
            metadata.release?.let(::add)
            metadata.latest?.let(::add)
            metadata.versions.asReversed().forEach(::add)
        }

        return candidates
            .map(String::trim)
            .firstOrNull(InputSafety::isSafeVersion)
    }

    private fun getLatestNpmVersion(packageName: String): String? {
        val encodedName = URLEncoder.encode(packageName, Charsets.UTF_8).replace("+", "%20")
        val url = "https://registry.npmjs.org/$encodedName"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                val root = jsonMapper.readTree(body)
                val latest = root.path("dist-tags").path("latest").textOrEmpty()
                latest.takeIf(InputSafety::isSafeVersion)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getLatestPypiVersion(packageName: String): String? {
        val encodedName = URLEncoder.encode(packageName, Charsets.UTF_8).replace("+", "%20")
        val url = "https://pypi.org/pypi/$encodedName/json"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                val root = jsonMapper.readTree(body)
                val latest = root.path("info").path("version").textOrEmpty()
                latest.takeIf(InputSafety::isSafeVersion)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchMetadata(
        repository: ProjectRepository,
        groupId: String,
        artifactId: String
    ): MavenMetadataValues? {
        if (!InputSafety.isAllowedRepositoryUrl(repository.url)) return null

        val url = buildMetadataUrl(repository.url, groupId, artifactId)
        if (!InputSafety.isAllowedRepositoryUrl(url)) return null

        val requestBuilder = runCatching { Request.Builder().url(url) }.getOrNull() ?: return null

        val allowCredentials = InputSafety.isTrustedCredentialDestination(url, trustedCredentialHosts)
        if (allowCredentials && repository.username != null && repository.password != null) {
            requestBuilder.header("Authorization", Credentials.basic(repository.username, repository.password))
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                return parseMavenMetadata(body)
            }
        } catch (_: IOException) {
            return null
        }
    }

    private fun parseMavenMetadata(xml: String): MavenMetadataValues? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }

            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val root = document.documentElement ?: return null

            val latest = firstText(root, "latest")
            val release = firstText(root, "release")
            val versions = allVersionTexts(root)

            MavenMetadataValues(
                latest = latest,
                release = release,
                versions = versions
            )
        }.getOrNull()
    }

    private fun firstText(root: Element, tagName: String): String? {
        val nodes = root.getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun allVersionTexts(root: Element): List<String> {
        val nodes = root.getElementsByTagName("version")
        val values = mutableListOf<String>()
        for (index in 0 until nodes.length) {
            val value = nodes.item(index)?.textContent?.trim()
            if (!value.isNullOrEmpty()) {
                values += value
            }
        }
        return values
    }

    private fun tools.jackson.databind.JsonNode.textOrEmpty(): String = scalarText().trim()

    private fun tools.jackson.databind.JsonNode.scalarText(): String = when {
        isNull || isMissingNode -> ""
        else -> toString().removeSurrounding("\"")
    }

    private fun buildMetadataUrl(baseUrl: String, groupId: String, artifactId: String): String {
        val groupPath = groupId.replace('.', '/')
        val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "$cleanBaseUrl$groupPath/$artifactId/maven-metadata.xml"
    }
}
