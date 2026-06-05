package com.example.gemma4ondevicetest

enum class ChatKind(
    val label: String,
    val defaultTitle: String,
    val systemInstruction: String
) {
    GENERAL(
        label = "대화",
        defaultTitle = "새 대화",
        systemInstruction = "반드시 한국어로만 답하라. You must respond only in Korean. 너는 간결하고 자연스러운 한국어 도우미다."
    );

    companion object {
        fun fromId(id: String): ChatKind = entries.firstOrNull { it.name == id } ?: GENERAL
    }
}
