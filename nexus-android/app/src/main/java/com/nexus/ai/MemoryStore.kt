package com.nexus.ai

import android.content.Context
import org.json.JSONArray

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_memory_v2", Context.MODE_PRIVATE)

    fun all(): List<String> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val s = a.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val current = all().toMutableList()
        current.removeAll { it.equals(clean, ignoreCase = true) }
        current += clean
        save(current.takeLast(40))
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }

    fun summary(maxChars: Int = 1200): String {
        if (all().isEmpty()) return ""
        val out = StringBuilder()
        for (item in all().asReversed()) {
            if (out.length + item.length + 3 > maxChars) break
            if (out.isNotEmpty()) out.append("\n")
            out.append("- ").append(item)
        }
        return out.toString()
    }

    private fun save(items: List<String>) {
        val a = JSONArray()
        items.forEach { a.put(it) }
        prefs.edit().putString("items", a.toString()).apply()
    }
}
