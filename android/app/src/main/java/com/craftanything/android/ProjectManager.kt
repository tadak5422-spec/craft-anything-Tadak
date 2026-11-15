package com.craftanything.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectManager {
    private fun rootDir(context: Context): File =
        File(context.filesDir, "projects").apply { mkdirs() }

    fun listProjects(context: Context): List<String> =
        rootDir(context).listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun projectDir(context: Context, name: String): File {
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "default-app" }
        return File(rootDir(context), safe).apply { mkdirs() }
    }

    fun createWebStarter(context: Context, name: String): String {
        val dir = projectDir(context, name)
        val index = File(dir, "index.html")
        if (!index.exists()) {
            index.writeText(
                """<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${dir.name}</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <main class="card">
    <h1>${dir.name}</h1>
    <p>Crafted inside Craft Anything Android.</p>
    <button id="btn">Test Action</button>
    <p id="out"></p>
  </main>
  <script src="app.js"></script>
</body>
</html>"""
            )
            File(dir, "styles.css").writeText(
                "body{font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:24px}" +
                    ".card{background:#1e293b;padding:20px;border-radius:12px}" +
                    "button{background:#38bdf8;border:none;padding:10px 16px;border-radius:8px;font-weight:bold}"
            )
            File(dir, "app.js").writeText(
                "document.getElementById('btn').onclick=()=>{document.getElementById('out').innerText='Interactive JS working!';};"
            )
            recordHistory(context, dir.name, "Created starter project")
        }
        return dir.name
    }

    fun listFiles(context: Context, project: String): List<String> {
        val base = projectDir(context, project)
        return base.walkTopDown()
            .filter { it.isFile && it.name != ".craft_history.json" && it.name != ".craft_meta.json" }
            .map { it.relativeTo(base).path }
            .sorted()
            .toList()
    }

    fun readFile(context: Context, project: String, relPath: String): String {
        val base = projectDir(context, project)
        val target = File(base, relPath).canonicalFile
        if (!target.path.startsWith(base.canonicalPath) || !target.exists()) return ""
        return target.readText()
    }

    fun writeFile(context: Context, project: String, relPath: String, content: String): Boolean {
        val base = projectDir(context, project)
        val clean = relPath.trim().trimStart('/').replace("..", "")
        if (clean.isBlank()) return false
        val target = File(base, clean).canonicalFile
        if (!target.path.startsWith(base.canonicalPath)) return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        recordHistory(context, project, "Saved $clean")
        return true
    }

    fun applyAiFiles(context: Context, project: String, reply: String): List<String> {
        val regex = Regex("```file:([^\\n]+)\\n([\\s\\S]*?)```")
        val updated = mutableListOf<String>()
        regex.findAll(reply).forEach { m ->
            val path = m.groupValues[1].trim()
            val body = m.groupValues[2]
            if (writeFile(context, project, path, body)) {
                updated.add(path)
            }
        }
        return updated
    }

    fun buildContextSummary(context: Context, project: String): String {
        return listFiles(context, project).take(8).joinToString("\n") { path ->
            "--- $path ---\n${readFile(context, project, path).take(1200)}"
        }
    }

    fun setProjectProfileId(context: Context, project: String, profileId: String) {
        val f = File(projectDir(context, project), ".craft_meta.json")
        val obj = if (f.exists()) JSONObject(f.readText()) else JSONObject()
        obj.put("profileId", profileId)
        f.writeText(obj.toString())
    }

    fun getProjectProfileId(context: Context, project: String): String? {
        val f = File(projectDir(context, project), ".craft_meta.json")
        if (!f.exists()) return null
        return runCatching { JSONObject(f.readText()).optString("profileId").ifBlank { null } }.getOrNull()
    }

    fun recordHistory(context: Context, project: String, action: String) {
        val f = File(projectDir(context, project), ".craft_history.json")
        val arr = if (f.exists()) runCatching { JSONArray(f.readText()) }.getOrElse { JSONArray() } else JSONArray()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        arr.put(JSONObject().apply {
            put("time", ts)
            put("action", action)
        })
        f.writeText(arr.toString())
    }

    fun getHistory(context: Context, project: String): List<String> {
        val f = File(projectDir(context, project), ".craft_history.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            buildList {
                for (i in arr.length() - 1 downTo 0) {
                    val o = arr.getJSONObject(i)
                    add("${o.optString("time")} — ${o.optString("action")}")
                }
            }
        }.getOrElse { emptyList() }
    }

    fun importFile(context: Context, project: String, uri: Uri): String? {
        var fileName = "imported_file.txt"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) fileName = cursor.getString(idx)
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val target = File(projectDir(context, project), fileName)
        target.writeBytes(bytes)
        recordHistory(context, project, "Imported $fileName (${bytes.size} bytes)")
        return "$fileName (${bytes.size} bytes)"
    }

    fun exportZip(context: Context, project: String, uri: Uri): Boolean {
        val base = projectDir(context, project)
        val out = context.contentResolver.openOutputStream(uri) ?: return false
        ZipOutputStream(out).use { zos ->
            base.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(base).path
                zos.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        recordHistory(context, project, "Exported ZIP")
        return true
    }
}
