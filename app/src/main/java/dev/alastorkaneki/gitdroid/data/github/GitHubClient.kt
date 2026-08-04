package dev.alastorkaneki.gitdroid.data

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal class GitHubClient {
    fun search(token: String): List<StoreApp> {
        val query = URLEncoder.encode("topic:android-app archived:false", StandardCharsets.UTF_8.name())
        val root = JSONObject(request("https://api.github.com/search/repositories?q=$query&sort=stars&order=desc&per_page=75", token))
        val items = root.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val fullName = item.optString("full_name")
                if (fullName.isBlank()) continue
                val topics = item.optJSONArray("topics").toStrings()
                val description = item.optString("description")
                add(
                    StoreApp(
                        id = "github:$fullName",
                        name = item.optString("name").humanName(),
                        summary = description,
                        description = description,
                        source = SourceKind.GITHUB,
                        repositoryFullName = fullName,
                        iconUrl = item.optJSONObject("owner")?.optString("avatar_url"),
                        categories = inferCategories(topics, description),
                        license = item.optJSONObject("license")?.optString("spdx_id")?.takeUnless { it == "NOASSERTION" },
                        projectUrl = item.optString("homepage").takeIf(String::isNotBlank) ?: item.optString("html_url"),
                        sourceCodeUrl = item.optString("html_url"),
                        stars = item.optLong("stargazers_count").takeIf { it >= 0L }
                    )
                )
            }
        }
    }

    fun resolveLatestRelease(app: StoreApp, token: String): StoreApp {
        val repository = app.repositoryFullName ?: return app
        val release = JSONObject(request("https://api.github.com/repos/$repository/releases/latest", token))
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val apkAssets = buildList {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    add(Asset(name, url, asset.optLong("size").takeIf { it > 0L }))
                }
            }
        }
        val best = apkAssets.maxByOrNull { rank(it.name) }
        return app.copy(
            versionName = release.optString("tag_name").ifBlank { app.versionName },
            description = release.optString("body").ifBlank { app.description },
            apkUrl = best?.url,
            apkName = best?.name,
            apkSize = best?.size
        )
    }

    private fun request(url: String, token: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "GitDroid/0.1.0")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = BufferedInputStream(stream).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
            }
            check(status in 200..299) { "GitHub HTTP $status: ${text.take(240)}" }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun rank(fileName: String): Int {
        val lower = fileName.lowercase(Locale.ROOT)
        var score = if ("universal" in lower || "noarch" in lower || "all" in lower) 100 else 0
        Build.SUPPORTED_ABIS.forEachIndexed { index, abi ->
            val aliases = when (abi) {
                "arm64-v8a" -> listOf("arm64-v8a", "arm64", "aarch64")
                "armeabi-v7a" -> listOf("armeabi-v7a", "armeabi", "armv7")
                "x86_64" -> listOf("x86_64", "x64")
                else -> listOf(abi)
            }
            if (aliases.any(lower::contains)) score += 80 - index
        }
        if ("debug" in lower) score -= 40
        if ("fdroid" in lower || "foss" in lower) score += 10
        return score
    }

    private fun inferCategories(topics: List<String>, description: String): List<String> {
        val text = (topics + description.lowercase(Locale.ROOT)).joinToString(" ")
        val mappings = linkedMapOf(
            "Development" to listOf("developer", "terminal", "git", "code", "ide"),
            "Games" to listOf("game", "gaming", "emulator"),
            "Internet" to listOf("browser", "network", "internet", "download", "torrent"),
            "Messaging" to listOf("chat", "messaging", "matrix", "xmpp", "irc"),
            "Multimedia" to listOf("music", "audio", "video", "media", "gallery", "camera"),
            "Navigation" to listOf("maps", "navigation", "gps", "location"),
            "Office" to listOf("notes", "calendar", "document", "reader", "pdf"),
            "Security" to listOf("privacy", "security", "password", "authenticator", "vpn"),
            "System" to listOf("launcher", "system", "utility", "tools", "file-manager")
        )
        return mappings.filterValues { words -> words.any(text::contains) }.keys.toList().ifEmpty { listOf("Other") }
    }

    private data class Asset(val name: String, val url: String, val size: Long?)
}

private fun JSONArray?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

private fun String.humanName(): String = replace('-', ' ').replace('_', ' ')
    .split(' ')
    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
