package com.example.gemma4ondevicetest

data class KnowledgeCategory(
    val major: String,
    val middle: String,
    val minor: String = "",
    val assetName: String,
    val filePath: String? = null,
    val keywords: List<String> = emptyList(),
    val toolPrompt: List<String> = emptyList(),
    val fallback: Boolean = false,
    val source: String = "bundled"
) {
    val label: String get() = if (minor.isNotBlank()) "$major > $middle > $minor" else "$major > $middle"
}

data class KnowledgeTool(
    val id: String,
    val displayName: String,
    val categories: List<KnowledgeCategory>
) {
    val categoryLabels: List<String> get() = categories.map { it.label }
}

data class KnowledgeSelection(
    val tool: KnowledgeTool?,
    val categories: List<KnowledgeCategory>,
    val sections: List<String>,
    val routingSource: String,
    val routingDetail: String
)

data class KnowledgePromptResult(
    val prompt: String,
    val selection: KnowledgeSelection?
)
