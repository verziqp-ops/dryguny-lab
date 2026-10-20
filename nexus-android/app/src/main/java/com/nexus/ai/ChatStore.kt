package com.nexus.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(val role: String, val text: String, val attachmentNames: List<String> = emptyList())

class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_chat", Context.MODE_PRIVATE)

    fun load(): MutableList<StoredMessage> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        val out = mutableListOf<StoredMessage>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val names = mutableListOf<String>()
                val a = o.optJSONArray("attachments") ?: JSONArray()
                for (j in 0 until a.length()) names += a.optString(j)
                out += StoredMessage(o.optString("role"), o.optString("text"), names)
            }
        } catch (_: Exception) {}
        return out
    }

    fun save(messages: List<StoredMessage>) {
        val arr = JSONArray()
        messages.takeLast(80).forEach { m ->
            val o = JSONObject()
            o.put("role", m.role)
            o.put("text", m.text)
            val a = JSONArray()
            m.attachmentNames.forEach { a.put(it) }
            o.put("attachments", a)
            arr.put(o)
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove("messages").apply()
}
