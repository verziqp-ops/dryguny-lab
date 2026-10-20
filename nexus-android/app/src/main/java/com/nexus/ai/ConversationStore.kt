package com.nexus.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatSession(
    val id: String,
    var title: String,
    val messages: MutableList<StoredMessage>,
    var updatedAt: Long,
)

class ConversationStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexus_conversations", Context.MODE_PRIVATE)

    fun all(): MutableList<ChatSession> {
        val raw = prefs.getString("chats", "[]") ?: "[]"
        val out = mutableListOf<ChatSession>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val msgs = mutableListOf<StoredMessage>()
                val ma = o.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until ma.length()) {
                    val m = ma.getJSONObject(j)
                    val aa = m.optJSONArray("attachments") ?: JSONArray()
                    val names = mutableListOf<String>()
                    for (k in 0 until aa.length()) names += aa.optString(k)
                    msgs += StoredMessage(m.optString("role"), m.optString("text"), names)
                }
                out += ChatSession(
                    o.optString("id"),
                    o.optString("title", "Новий чат"),
                    msgs,
                    o.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) {}
        return out.sortedByDescending { it.updatedAt }.toMutableList()
    }

    fun migrateLegacyIfNeeded(): MutableList<ChatSession> {
        val existing = all()
        if (existing.isNotEmpty()) return existing
        val legacy = ChatStore(context).load()
        if (legacy.isNotEmpty()) {
            val title = legacy.firstOrNull { it.role == "user" }?.text?.take(42)?.ifBlank { "Чат" } ?: "Чат"
            val chat = ChatSession(System.currentTimeMillis().toString(), title, legacy, System.currentTimeMillis())
            save(listOf(chat))
            setActive(chat.id)
            return mutableListOf(chat)
        }
        val chat = create()
        save(listOf(chat))
        setActive(chat.id)
        return mutableListOf(chat)
    }

    fun create(): ChatSession = ChatSession(
        id = System.currentTimeMillis().toString(),
        title = "Новий чат",
        messages = mutableListOf(),
        updatedAt = System.currentTimeMillis()
    )

    fun save(chats: List<ChatSession>) {
        val array = JSONArray()
        chats.sortedByDescending { it.updatedAt }.take(60).forEach { chat ->
            val o = JSONObject()
            o.put("id", chat.id)
            o.put("title", chat.title)
            o.put("updatedAt", chat.updatedAt)
            val ma = JSONArray()
            chat.messages.takeLast(100).forEach { m ->
                val mo = JSONObject()
                mo.put("role", m.role)
                mo.put("text", m.text)
                val aa = JSONArray()
                m.attachmentNames.forEach { aa.put(it) }
                mo.put("attachments", aa)
                ma.put(mo)
            }
            o.put("messages", ma)
            array.put(o)
        }
        prefs.edit().putString("chats", array.toString()).apply()
    }

    fun activeId(): String? = prefs.getString("active_id", null)
    fun setActive(id: String) = prefs.edit().putString("active_id", id).apply()
}
