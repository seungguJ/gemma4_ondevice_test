package com.example.gemma4ondevicetest

import android.content.Context
import java.io.File

object KnowledgePromptBuilder {
    private const val MAX_SECTION_CHARS = 4000  // 전체 참고문 합산 상한 (~2K 토큰)
    private val textCache = mutableMapOf<String, String>()

    fun buildAgentPromptResult(
        context: Context,
        routerResult: AgentRouter.Result,
        userPrompt: String,
        history: String = "",
        internalDataContext: String = ""
    ): KnowledgePromptResult {
        val rawSections = routerResult.categories.mapNotNull { category ->
            runCatching { loadText(context, category) }.getOrNull()
                ?.let { category to it }
        }
        if (rawSections.isEmpty()) {
            val fallbackPrompt = buildString {
                appendLine("반드시 한국어로만 답하라.")
                if (internalDataContext.isNotBlank()) {
                    appendLine()
                    appendLine(internalDataContext)
                }
                if (history.isNotBlank()) {
                    appendLine()
                    appendLine(history)
                }
                appendLine()
                append("현재 질문: $userPrompt")
            }
            return KnowledgePromptResult(fallbackPrompt, null)
        }

        // 동일 분류 문서 그룹화 후 character budget 적용
        val sections = buildSectionsWithBudget(rawSections)

        val selection = KnowledgeSelection(
            tool          = null,
            categories    = routerResult.categories,
            sections      = sections,
            routingSource = "Tool Calling",
            routingDetail = routerResult.matchDetail
        )
        val prompt = buildString {
            appendLine("반드시 한국어로만 답하라.")
            appendLine()
            if (routerResult.contextPrompt.isNotBlank()) {
                appendLine(routerResult.contextPrompt)
                appendLine()
            }
            if (internalDataContext.isNotBlank()) {
                appendLine(internalDataContext)
                appendLine()
            }
            appendLine("Markdown 제목(#, ##)이나 표는 쓰지 말고, 모바일에서 읽기 쉬운 짧은 문단과 간단한 불릿만 사용하라.")
            appendLine()
            appendLine("참고 문서:")
            appendLine(sections.joinToString("\n\n"))
            appendLine()
            if (history.isNotBlank()) {
                appendLine(history)
                appendLine()
            }
            appendLine("현재 질문:")
            append(userPrompt)
        }.trimIndent()
        return KnowledgePromptResult(prompt, selection)
    }

    fun loadDocumentText(context: Context, category: KnowledgeCategory): String? =
        runCatching { loadText(context, category) }.getOrNull()

    fun invalidateCache() { textCache.clear() }

    private fun buildSectionsWithBudget(
        rawSections: List<Pair<KnowledgeCategory, String>>
    ): List<String> {
        // 분류별로 그룹화 (같은 major+middle은 묶어서 처리)
        val grouped = rawSections.groupBy { (cat, _) -> cat.major to cat.middle }

        val result = mutableListOf<String>()
        var remaining = MAX_SECTION_CHARS

        for ((_, docsInGroup) in grouped) {
            if (remaining <= 0) break

            if (docsInGroup.size == 1) {
                val (cat, text) = docsInGroup.first()
                val capped = if (text.length > remaining) text.take(remaining) + "\n[이하 생략]" else text
                result.add("[${cat.label}]\n$capped")
                remaining -= capped.length
            } else {
                // 동일 분류 문서가 여러 개 → 순서대로 포함, 공간이 남는 만큼
                docsInGroup.forEachIndexed { idx, (cat, text) ->
                    if (remaining <= 0) return@forEachIndexed
                    val suffix = " (${idx + 1}/${docsInGroup.size})"
                    val capped = if (text.length > remaining) text.take(remaining) + "\n[이하 생략]" else text
                    result.add("[${cat.label}$suffix]\n$capped")
                    remaining -= capped.length
                }
            }
        }
        return result
    }

    private fun loadText(context: Context, category: KnowledgeCategory): String {
        val key = category.filePath ?: category.assetName
        textCache[key]?.let { return it }
        val text = if (category.filePath != null) {
            File(category.filePath).readText()
        } else {
            context.assets.open(category.assetName).bufferedReader().readText()
        }
        return text.also { textCache[key] = it }
    }
}
