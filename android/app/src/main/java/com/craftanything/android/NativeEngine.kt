package com.craftanything.android

import org.json.JSONObject

data class EngineSummary(
    val engineVersion: String,
    val toolCount: Int,
    val toolsetCount: Int,
    val cleanRoom: Boolean,
    val loaded: Boolean,
    val error: String? = null
)

data class EngineTool(
    val id: String,
    val title: String,
    val summary: String,
    val beginner: String,
    val schema: String
)

object NativeEngine {
    private var libLoaded = false
    private var libError: String? = null

    init {
        try {
            System.loadLibrary("craft_anything_android")
            libLoaded = true
        } catch (t: Throwable) {
            libError = t.message ?: "Native Rust engine not loaded"
        }
    }

    private external fun nativeEngineSummary(dataDir: String): String
    private external fun nativeListToolsJson(): String

    fun getSummary(dataDir: String): EngineSummary {
        if (!libLoaded) {
            return EngineSummary("0.6.1", 0, 0, false, false, libError)
        }
        return try {
            val obj = JSONObject(nativeEngineSummary(dataDir))
            EngineSummary(
                engineVersion = obj.optString("engineVersion", "0.6.1"),
                toolCount = obj.optInt("toolCount", 0),
                toolsetCount = obj.optInt("toolsetCount", 0),
                cleanRoom = obj.optBoolean("cleanRoom", false),
                loaded = true
            )
        } catch (t: Throwable) {
            EngineSummary("0.6.1", 0, 0, false, false, t.message)
        }
    }

    fun listTools(): List<EngineTool> {
        if (!libLoaded) return emptyList()
        return try {
            val obj = JSONObject(nativeListToolsJson())
            val arr = obj.optJSONArray("tools") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        EngineTool(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            summary = item.optString("summary"),
                            beginner = item.optString("beginner"),
                            schema = item.optJSONObject("schema")?.toString(2) ?: "{}"
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
