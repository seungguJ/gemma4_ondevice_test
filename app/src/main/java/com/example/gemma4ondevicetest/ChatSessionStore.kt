package com.example.gemma4ondevicetest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ChatSessionStore {
    private const val PREFS_NAME = "chat_sessions"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_ACTIVE_SESSION = "active_session"

    fun loadSessions(context: Context): MutableList<ChatSession> {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return mutableListOf(createSession(ChatKind.GENERAL))
        val sessions = runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index -> array.getJSONObject(index).toSession() }
        }.getOrElse { mutableListOf() }
        return sessions.ifEmpty { mutableListOf(createSession(ChatKind.GENERAL)) }
    }

    fun getActiveSessionId(context: Context): String? {
        return prefs(context).getString(KEY_ACTIVE_SESSION, null)
    }

    fun setActiveSessionId(context: Context, sessionId: String) {
        prefs(context).edit().putString(KEY_ACTIVE_SESSION, sessionId).apply()
    }

    fun createSession(kind: ChatKind): ChatSession {
        return ChatSession(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = kind.defaultTitle
        )
    }

    fun saveSessions(context: Context, sessions: List<ChatSession>, activeSessionId: String?) {
        val array = JSONArray()
        sessions.sortedByDescending { it.updatedAt }.forEach { session ->
            array.put(session.toJson())
        }
        prefs(context).edit()
            .putString(KEY_SESSIONS, array.toString())
            .putString(KEY_ACTIVE_SESSION, activeSessionId)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ChatSession.toJson(): JSONObject {
        val messagesJson = JSONArray()
        messages.forEach { message ->
            messagesJson.put(
                JSONObject()
                    .put("text", message.text)
                    .put("fromUser", message.fromUser)
            )
        }
        return JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("updatedAt", updatedAt)
            .put("messages", messagesJson)
    }

    private fun JSONObject.toSession(): ChatSession {
        val messagesJson = optJSONArray("messages") ?: JSONArray()
        val messages = MutableList(messagesJson.length()) { index ->
            val message = messagesJson.getJSONObject(index)
            ChatMessage(
                text = message.optString("text"),
                fromUser = message.optBoolean("fromUser")
            )
        }
        return ChatSession(
            id = optString("id", UUID.randomUUID().toString()),
            kind = ChatKind.fromId(optString("kind", ChatKind.GENERAL.name)),
            title = optString("title", ChatKind.GENERAL.defaultTitle),
            messages = messages,
            updatedAt = optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
