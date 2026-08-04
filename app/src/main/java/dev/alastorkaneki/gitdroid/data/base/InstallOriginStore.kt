package dev.alastorkaneki.gitdroid.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class InstallOriginStore(context: Context) {
    private val preferences = context.getSharedPreferences("gitdroid_install_origins", Context.MODE_PRIVATE)

    fun record(packageName: String, app: StoreApp) {
        if (packageName.isBlank()) return
        val json = JSONObject().apply {
            put("id", app.id)
            put("name", app.name)
            put("summary", app.summary)
            put("description", app.description)
            put("source", app.source.name)
            put("packageName", packageName)
            putNullable("repositoryFullName", app.repositoryFullName)
            putNullable("iconUrl", app.iconUrl)
            putNullable("featureGraphicUrl", app.featureGraphicUrl)
            put("previewUrls", JSONArray(app.previewUrls))
            putNullable("versionName", app.versionName)
            put("categories", JSONArray(app.categories))
            putNullable("license", app.license)
            putNullable("projectUrl", app.projectUrl)
            putNullable("sourceCodeUrl", app.sourceCodeUrl)
            putNullable("apkUrl", app.apkUrl)
            putNullable("apkName", app.apkName)
            app.apkSize?.let { put("apkSize", it) }
            app.stars?.let { put("stars", it) }
        }
        preferences.edit().putString(packageName, json.toString()).apply()
    }

    fun records(): List<StoreApp> = preferences.all.values.mapNotNull { raw ->
        runCatching { parse(JSONObject(raw as? String ?: return@mapNotNull null)) }.getOrNull()
    }

    private fun parse(json: JSONObject): StoreApp {
        val source = runCatching { SourceKind.valueOf(json.getString("source")) }.getOrDefault(SourceKind.GITHUB)
        return StoreApp(
            id = json.optString("id").ifBlank { "${source.name.lowercase()}:${json.getString("packageName")}" },
            name = json.optString("name").ifBlank { json.getString("packageName").substringAfterLast('.') },
            summary = json.optString("summary"),
            description = json.optString("description"),
            source = source,
            packageName = json.getString("packageName"),
            repositoryFullName = json.optNullableString("repositoryFullName"),
            iconUrl = json.optNullableString("iconUrl"),
            featureGraphicUrl = json.optNullableString("featureGraphicUrl"),
            previewUrls = json.optJSONArray("previewUrls").toStringList(),
            versionName = json.optNullableString("versionName"),
            categories = json.optJSONArray("categories").toStringList().ifEmpty { listOf("Other") },
            license = json.optNullableString("license"),
            projectUrl = json.optNullableString("projectUrl"),
            sourceCodeUrl = json.optNullableString("sourceCodeUrl"),
            apkUrl = json.optNullableString("apkUrl"),
            apkName = json.optNullableString("apkName"),
            apkSize = json.optLong("apkSize").takeIf { json.has("apkSize") && it > 0L },
            stars = json.optLong("stars").takeIf { json.has("stars") && it >= 0L }
        )
    }
}

private fun JSONObject.putNullable(key: String, value: String?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}
