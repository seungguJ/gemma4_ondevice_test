package com.example.gemma4ondevicetest

data class ChatSession(
    val id: String,
    val kind: ChatKind,
    var title: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis()
)
