package com.craftanything.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String)

object AiClient {
    suspend fun send(
        profile: ProviderProfile,
        messages: List<ChatMessage>,
        projectContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("HTTPS required"))
        }
        val systemPrompt = buildString {
            appendLine("You are Craft Anything AI Coding Agent.")
            appendLine("When creating or editing project files, wrap each file strictly as:")
            appendLine("```file:relative/path.ext")
            appendLine("<full file content>")
            appendLine("```")
            if (projectContext.isNotBlank()) {
                appendLine("\nCurrent project files:\n$projectContext")
            }
        }

        try {
            val endpoint = if (profile.kind == "anthropic") "$base/messages" else "$base/chat/completions"
            val payload = JSONObject()
            payload.put("model", profile.model.trim())

            if (profile.kind == "anthropic") {
                payload.put("max_tokens", 4096)
                payload.put("system", systemPrompt)
                val arr = JSONArray()
                messages.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("role", if (m.role == "assistant") "assistant" else "user")
                        put("content", m.content)
                    })
                }
                payload.put("messages", arr)
            } else {
                val arr = JSONArray()
                arr.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                messages.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
                payload.put("messages", arr)
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/json")
                if (profile.kind == "anthropic") {
                    setRequestProperty("x-api-key", profile.apiKey.trim())
                    setRequestProperty("anthropic-version", "2023-06-01")
                } else {
                    setRequestProperty("Authorization", "Bearer ${profile.apiKey.trim()}")
                }
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            if (code !in 200..299) {
                return@withContext Result.failure(RuntimeException("HTTP $code: ${body.take(300)}"))
            }

            val json = JSONObject(body)
            val reply = if (profile.kind == "anthropic") {
                json.getJSONArray("content").getJSONObject(0).getString("text")
            } else {
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
            Result.success(reply)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
