package dev.alastorkaneki.gitdroid.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

internal class FdroidClient(private val cacheDirectory: File) {
    private val catalogDirectory = File(cacheDirectory, "catalogs").apply { mkdirs() }

    fun load(repository: String, source: SourceKind): List<StoreApp> {
        val cache = File(catalogDirectory, "${source.name.lowercase(Locale.ROOT)}-index-v1.jar")
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > CACHE_AGE_MS) {
            download("$repository/index-v1.jar", cache)
        }
        return parse(cache, repository, source)
    }

    private fun download(url: String, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.download")
        connection(url).useInput { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun parse(indexJar: File, repository: String, source: SourceKind): List<StoreApp> {
        val metadata = linkedMapOf<String, Metadata>()
        val packages = linkedMapOf<String, PackageRelease>()
        ZipInputStream(BufferedInputStream(FileInputStream(indexJar))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "index-v1.json") entry = zip.nextEntry
            check(entry != null) { "index-v1.json was missing" }
            JsonReader(InputStreamReader(zip, StandardCharsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "apps" -> readApps(reader, metadata)
                        "packages" -> readPackages(reader, packages)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }

        return metadata.values.mapNotNull { app ->
            val release = packages[app.packageName] ?: return@mapNotNull null
            val icon = app.icon?.takeIf(String::isNotBlank)?.removePrefix("/")
            StoreApp(
                id = "${source.name.lowercase(Locale.ROOT)}:${app.packageName}",
                name = app.name.ifBlank { app.packageName.substringAfterLast('.') },
                summary = app.summary,
                description = app.description.ifBlank { app.summary },
                source = source,
                packageName = app.packageName,
                iconUrl = icon?.let { "$repository/icons-640/$it" },
                versionName = release.versionName,
                categories = app.categories.ifEmpty { listOf("Other") },
                license = app.license,
                projectUrl = app.website,
                sourceCodeUrl = app.sourceCode,
                apkUrl = "$repository/${release.apkName}",
                apkName = release.apkName,
                apkSize = release.size
            )
        }
    }

    private fun readApps(reader: JsonReader, output: MutableMap<String, Metadata>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var packageName = ""
            var name = ""
            var summary = ""
            var description = ""
            var icon: String? = null
            var license: String? = null
            var website: String? = null
            var sourceCode: String? = null
            val categories = mutableListOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "packageName" -> packageName = reader.flexibleString()
                    "name" -> name = reader.flexibleString()
                    "summary" -> summary = reader.flexibleString()
                    "description" -> description = reader.flexibleString()
                    "icon" -> icon = reader.flexibleString().ifBlank { null }
                    "license" -> license = reader.flexibleString().ifBlank { null }
                    "webSite" -> website = reader.flexibleString().ifBlank { null }
                    "sourceCode" -> sourceCode = reader.flexibleString().ifBlank { null }
                    "categories" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.flexibleString().takeIf(String::isNotBlank)?.let(categories::add)
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (packageName.isNotBlank()) {
                output[packageName] = Metadata(
                    packageName = packageName,
                    name = name,
                    summary = summary,
                    description = description,
                    icon = icon,
                    categories = categories,
                    license = license,
                    website = website,
                    sourceCode = sourceCode
                )
            }
        }
        reader.endArray()
    }

    private fun readPackages(reader: JsonReader, output: MutableMap<String, PackageRelease>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val packageName = reader.nextName()
            var newest: PackageRelease? = null
            reader.beginArray()
            while (reader.hasNext()) {
                var apkName = ""
                var versionName = ""
                var versionCode = 0L
                var size: Long? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "apkName" -> apkName = reader.flexibleString()
                        "versionName" -> versionName = reader.flexibleString()
                        "versionCode" -> versionCode = reader.flexibleLong()
                        "size" -> size = reader.flexibleLong().takeIf { it > 0L }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (apkName.isNotBlank() && (newest == null || versionCode > newest.versionCode)) {
                    newest = PackageRelease(apkName, versionName, versionCode, size)
                }
            }
            reader.endArray()
            newest?.let { output[packageName] = it }
        }
        reader.endObject()
    }

    private fun connection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private inline fun <T> HttpURLConnection.useInput(block: (java.io.InputStream) -> T): T {
        try {
            val status = responseCode
            check(status in 200..299) { "HTTP $status" }
            return BufferedInputStream(inputStream).use(block)
        } finally {
            disconnect()
        }
    }

    private data class Metadata(
        val packageName: String,
        val name: String,
        val summary: String,
        val description: String,
        val icon: String?,
        val categories: List<String>,
        val license: String?,
        val website: String?,
        val sourceCode: String?
    )

    private data class PackageRelease(
        val apkName: String,
        val versionName: String,
        val versionCode: Long,
        val size: Long?
    )

    companion object {
        private const val CACHE_AGE_MS = 12L * 60L * 60L * 1000L
        private const val USER_AGENT = "GitDroid/0.1.0 (Android FOSS catalog)"
    }
}

private fun JsonReader.flexibleString(): String = when (peek()) {
    JsonToken.NULL -> {
        nextNull()
        ""
    }
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    else -> {
        skipValue()
        ""
    }
}

private fun JsonReader.flexibleLong(): Long = when (peek()) {
    JsonToken.NUMBER -> runCatching { nextLong() }.getOrDefault(0L)
    JsonToken.STRING -> nextString().toLongOrNull() ?: 0L
    JsonToken.NULL -> {
        nextNull()
        0L
    }
    else -> {
        skipValue()
        0L
    }
}
