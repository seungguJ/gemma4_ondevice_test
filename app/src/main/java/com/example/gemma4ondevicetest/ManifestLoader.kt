package com.example.gemma4ondevicetest

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ManifestLoader {
    private const val TAG = "ManifestLoader"
    private const val MANIFEST_ASSET = "knowledge/manifest.json"
    private const val OVERRIDE_FILE = "knowledge/manifest_override.json"

    @Volatile private var cachedTools: List<KnowledgeTool>? = null

    fun getTools(context: Context): List<KnowledgeTool> =
        cachedTools ?: reloadTools(context)

    fun reloadTools(context: Context): List<KnowledgeTool> {
        val tools = mergeTools(loadBase(context), loadOverrides(context))
        cachedTools = tools
        return tools
    }

    fun invalidate() { cachedTools = null }

    fun addOverrideCategories(context: Context, toolId: String, categories: List<KnowledgeCategory>) {
        val file = File(context.filesDir, OVERRIDE_FILE)
        file.parentFile?.mkdirs()
        val root = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
                   else JSONObject()
        val arr = root.optJSONArray("categories") ?: JSONArray().also { root.put("categories", it) }
        categories.forEach { cat ->
            arr.put(JSONObject().apply {
                put("toolId", toolId)
                put("major", cat.major)
                put("middle", cat.middle)
                put("minor", cat.minor)
                if (cat.assetName.isNotBlank()) put("assetName", cat.assetName)
                cat.filePath?.let { put("filePath", it) }
                put("keywords", JSONArray(cat.keywords))
                if (cat.toolPrompt.isNotEmpty()) put("toolPrompt", JSONArray(cat.toolPrompt))
                put("fallback", cat.fallback)
                put("source", "user_upload")
            })
        }
        file.writeText(root.toString(2))
        invalidate()
    }

    fun listUserDocuments(context: Context): List<UserDocument> {
        val file = File(context.filesDir, OVERRIDE_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("categories") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val fp = obj.optString("filePath").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                UserDocument(
                    toolId   = obj.getString("toolId"),
                    major    = obj.getString("major"),
                    middle   = obj.getString("middle"),
                    minor    = obj.optString("minor"),
                    filePath = fp,
                    fileName = File(fp).name
                )
            }
        }.getOrElse { emptyList() }
    }

    fun deleteUserDocument(context: Context, filePath: String) {
        File(filePath).delete()
        val file = File(context.filesDir, OVERRIDE_FILE)
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("categories") ?: return
            val filtered = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("filePath") != filePath) filtered.put(obj)
            }
            root.put("categories", filtered)
            file.writeText(root.toString(2))
            invalidate()
        }
    }

    private fun loadBase(context: Context): List<KnowledgeTool> = runCatching {
        val json = context.assets.open(MANIFEST_ASSET).bufferedReader().readText()
        parseTools(JSONObject(json))
    }.getOrElse { e ->
        Log.e(TAG, "Failed to load manifest", e)
        emptyList()
    }

    private fun loadOverrides(context: Context): Map<String, List<KnowledgeCategory>> {
        val file = File(context.filesDir, OVERRIDE_FILE)
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray("categories") ?: return emptyMap()
            buildMap<String, MutableList<KnowledgeCategory>> {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val toolId = obj.getString("toolId")
                    getOrPut(toolId) { mutableListOf() }.add(parseCategory(obj, "user_upload"))
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to load overrides", e)
            emptyMap()
        }
    }

    private fun mergeTools(
        base: List<KnowledgeTool>,
        overrides: Map<String, List<KnowledgeCategory>>
    ): List<KnowledgeTool> {
        if (overrides.isEmpty()) return base
        return base.map { tool ->
            val extra = overrides[tool.id] ?: return@map tool
            tool.copy(categories = tool.categories + extra)
        }
    }

    private fun parseTools(root: JSONObject): List<KnowledgeTool> {
        val arr = root.optJSONArray("tools") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val cats = obj.optJSONArray("categories")
            KnowledgeTool(
                id = obj.getString("id"),
                displayName = obj.getString("displayName"),
                categories = if (cats != null) (0 until cats.length()).map {
                    parseCategory(cats.getJSONObject(it), "bundled")
                } else emptyList()
            )
        }
    }

    private fun parseCategory(obj: JSONObject, source: String): KnowledgeCategory {
        val kwArr = obj.optJSONArray("keywords")
        val keywords = if (kwArr != null) (0 until kwArr.length()).map { kwArr.getString(it) } else emptyList()
        val tpArr = obj.optJSONArray("toolPrompt")
        val toolPrompt = if (tpArr != null) (0 until tpArr.length()).map { tpArr.getString(it) } else emptyList()
        return KnowledgeCategory(
            major     = obj.getString("major"),
            middle    = obj.getString("middle"),
            minor     = obj.optString("minor", ""),
            assetName = obj.optString("assetName", ""),
            filePath = obj.optString("filePath").takeIf { it.isNotBlank() },
            keywords = keywords,
            toolPrompt = toolPrompt,
            fallback = obj.optBoolean("fallback", false),
            source   = source
        )
    }
}

data class UserDocument(
    val toolId: String,
    val major: String,
    val middle: String,
    val minor: String,
    val filePath: String,
    val fileName: String
)
