package com.craftanything.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProviderProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: String, // "openai" or "anthropic"
    val baseUrl: String,
    val model: String,
    val apiKey: String
)

data class ProviderPreset(
    val name: String,
    val kind: String,
    val baseUrl: String,
    val defaultModel: String
)

object SecureKeyStore {
    private const val PREFS = "craft_secure_prefs"
    private const val KEY_PROFILES = "encrypted_profiles"
    private const val KEY_ACTIVE_ID = "active_profile_id"
    private const val KEY_WORKER_URL = "cloud_worker_url"
    private const val ALIAS = "craft_anything_aes_key"

    val presets = listOf(
        ProviderPreset("OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o-mini"),
        ProviderPreset("Anthropic Claude", "anthropic", "https://api.anthropic.com/v1", "claude-3-5-sonnet-latest"),
        ProviderPreset("Google Gemini", "openai", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        ProviderPreset("Groq", "openai", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        ProviderPreset("OpenRouter", "openai", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        ProviderPreset("Custom OpenAI-Compatible", "openai", "https://api.example.com/v1", "custom-model")
    )

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val enc = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$enc"
    }

    private fun decrypt(packed: String): String {
        if (packed.isEmpty() || !packed.contains(":")) return ""
        return try {
            val parts = packed.split(":", limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    fun loadProfiles(context: Context): List<ProviderProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ProviderProfile(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            kind = o.optString("kind", "openai"),
                            baseUrl = o.getString("baseUrl"),
                            model = o.getString("model"),
                            apiKey = decrypt(o.optString("encKey", ""))
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveProfiles(context: Context, list: List<ProviderProfile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("kind", p.kind)
                    put("baseUrl", p.baseUrl.trim())
                    put("model", p.model.trim())
                    put("encKey", encrypt(p.apiKey.trim()))
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun getActiveId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_ID, null)

    fun setActiveId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getWorkerUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WORKER_URL, "") ?: ""

    fun setWorkerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_WORKER_URL, url.trim()).apply()
    }

    suspend fun testConnection(profile: ProviderProfile): String = withContext(Dispatchers.IO) {
        val base = profile.baseUrl.trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            return@withContext "Error: Only HTTPS endpoints are allowed for API keys."
        }
        if (profile.apiKey.isBlank()) {
            return@withContext "Error: API key is empty."
        }
        val t0 = System.currentTimeMillis()
        try {
            val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                if (profile.kind == "anthropic") {
                    setRequestProperty("x-api-key", profile.apiKey.trim())
                    setRequestProperty("anthropic-version", "2023-06-01")
                } else {
                    setRequestProperty("Authorization", "Bearer ${profile.apiKey.trim()}")
                }
            }
            val code = conn.responseCode
            val ms = System.currentTimeMillis() - t0
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
            conn.disconnect()
            if (code in 200..299) {
                "Connected (HTTP $code in ${ms}ms)"
            } else {
                "HTTP $code (${ms}ms): $body"
            }
        } catch (t: Throwable) {
            "Failed: ${t.message}"
        }
    }
}
