package com.example.gemma4ondevicetest

import android.content.Context
import android.util.Log

object AgentRouter {
    private const val TAG = "AgentRouter"
    private const val MAX_CHUNKS = 6  // 점수 상위 청크 최대 선택 수

    data class Result(
        val categories: List<KnowledgeCategory>,
        val contextPrompt: String,
        val matchDetail: String
    )

    fun route(context: Context, userPrompt: String): Result? =
        route(ManifestLoader.getTools(context), userPrompt)

    fun route(tools: List<KnowledgeTool>, userPrompt: String): Result? {
        val allCategories = tools.flatMap { it.categories }
        val normalizedPrompt = userPrompt.lowercase()

        val scored = allCategories
            .map { cat -> cat to score(cat, normalizedPrompt) }

        val matched = scored
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }

        if (matched.isEmpty()) {
            Log.i(TAG, "No chunk matched — general chat")
            return null
        }

        // 점수 순 개별 청크 선택 (분류 그룹 전체가 아닌 청크 단위)
        val selected = matched
            .take(MAX_CHUNKS)
            .map { (cat, _) -> cat }
            .distinctBy { it.filePath ?: it.assetName }

        val contextPrompt = selected
            .flatMap { it.toolPrompt }
            .distinct()
            .take(3)
            .joinToString("\n")

        val detail = matched.take(4).joinToString { (cat, s) -> "${cat.minor.ifBlank { cat.middle }}=$s" }
        Log.i(TAG, "Selected ${selected.size} chunks by score | $detail")

        return Result(selected, contextPrompt, detail)
    }

    private fun score(cat: KnowledgeCategory, normalizedPrompt: String): Int {
        val keywordHits = cat.keywords.count { normalizedPrompt.contains(it.lowercase()) }
        // minor(섹션 제목)가 질의에 포함되면 가산점
        val titleBonus = if (cat.minor.isNotBlank() &&
            normalizedPrompt.contains(cat.minor.lowercase())) 2 else 0
        return keywordHits + titleBonus
    }
}
