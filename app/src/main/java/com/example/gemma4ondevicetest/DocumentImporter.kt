package com.example.gemma4ondevicetest

import android.content.Context
import android.net.Uri
import java.io.File

object DocumentImporter {

    data class DetectedSection(
        val index: Int,
        val previewText: String,
        val fullText: String,
        val suggestedToolId: String,
        val suggestedMajor: String,
        val suggestedMiddle: String,
        val confirmedToolId: String,
        val confirmedMajor: String,
        val confirmedMiddle: String,
        val confirmedMinor: String
    )

    fun readTextFromUri(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

    fun detectSections(text: String, tools: List<KnowledgeTool>): List<DetectedSection> {
        return splitIntoSections(text).mapIndexed { index, para ->
            val (toolId, cat) = scoreSection(para, tools)
            val heading = extractHeading(para)
            val preview = if (heading.isNotBlank()) {
                "[$heading] " + para.lines()
                    .filter { it.isNotBlank() }.drop(1).take(3)
                    .joinToString(" ").take(100)
            } else {
                para.take(150).replace('\n', ' ')
            }
            val autoMinor = if (heading.isNotBlank() && heading.length <= 40) heading else ""
            DetectedSection(
                index           = index,
                previewText     = preview,
                fullText        = para,
                suggestedToolId = toolId,
                suggestedMajor  = cat?.major ?: "",
                suggestedMiddle = cat?.middle ?: "",
                confirmedToolId = toolId,
                confirmedMajor  = cat?.major ?: "",
                confirmedMiddle = cat?.middle ?: "",
                confirmedMinor  = autoMinor
            )
        }
    }

    fun saveSections(context: Context, fileName: String, sections: List<DetectedSection>) {
        val dir = File(context.filesDir, "knowledge/custom")
        dir.mkdirs()
        val baseName = fileName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9가-힣_-]"), "_")
        val byTool = mutableMapOf<String, MutableList<KnowledgeCategory>>()

        sections.forEachIndexed { i, section ->
            if (section.confirmedMajor.isBlank() || section.confirmedMiddle.isBlank()) return@forEachIndexed
            val safeName = "${baseName}_${i}_${section.confirmedMajor.replace(' ', '_').take(20)}.txt"
            val file = File(dir, safeName)
            file.writeText(section.fullText)
            val categoryLabel = if (section.confirmedMinor.isNotBlank())
                "${section.confirmedMajor} > ${section.confirmedMiddle} > ${section.confirmedMinor}"
            else "${section.confirmedMajor} > ${section.confirmedMiddle}"
            val autoToolPrompt = listOf(
                "아래 $categoryLabel 참고문 범위에서만 답하라.",
                "관련 내용을 정확히 인용하되, 참고문에 없는 내용은 지어내지 말라.",
                "정보는 변경될 수 있으므로 최신 공식 자료 확인을 안내하라."
            )
            val keywords = extractKeywords(section.fullText)
            val cat = KnowledgeCategory(
                major      = section.confirmedMajor,
                middle     = section.confirmedMiddle,
                minor      = section.confirmedMinor,
                assetName  = "",
                filePath   = file.absolutePath,
                keywords   = keywords,
                toolPrompt = autoToolPrompt,
                source     = "user_upload"
            )
            byTool.getOrPut(section.confirmedToolId) { mutableListOf() }.add(cat)
        }

        byTool.forEach { (toolId, cats) ->
            ManifestLoader.addOverrideCategories(context, toolId, cats)
        }
    }

    // ── keyword extraction for user-uploaded docs ──────────────────────────────

    private val STOP_WORDS = setOf(
        "이", "가", "을", "를", "의", "에", "은", "는", "이다", "있다", "하다",
        "그", "저", "것", "수", "있", "하", "않", "되", "더", "로", "으로",
        "에서", "에게", "부터", "까지", "와", "과", "도", "만", "라", "이라",
        "한", "된", "할", "하는", "하고", "하여", "그리고", "또한", "또", "즉",
        "하지만", "그러나", "위해", "위한", "통해", "통한", "대한", "등", "및"
    )

    fun extractKeywords(text: String, maxKeywords: Int = 15): List<String> {
        return text.lowercase()
            .split(Regex("[\\s\\n.,!?;:()\\[\\]{}\"'/\\\\\\-]+"))
            .filter { token -> token.length >= 2 && token !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(maxKeywords)
            .map { it.key }
    }

    // ── section splitting ──────────────────────────────────────────────────────
    // 전략: ##/# 헤더를 1차 경계로 고정 → 섹션 내부가 CHUNK_SIZE 초과 시에만 LangChain 추가 분할

    private val CHUNK_SIZE    = 500
    private val CHUNK_OVERLAP = 60
    private val SEPARATORS    = listOf("\n\n", "\n", ". ", "。", " ")

    private fun splitIntoSections(text: String): List<String> {
        return splitByHeaders(text.trim())
            .flatMap { section ->
                if (section.length <= CHUNK_SIZE) listOf(section)
                else splitRecursive(section, SEPARATORS)
            }
            .filter { it.length > 50 }
            .ifEmpty { listOf(text.trim()).filter { it.isNotBlank() } }
    }

    // ##/# 헤더 기준 1차 분할 — 헤더가 없으면 전체를 하나로
    private fun splitByHeaders(text: String): List<String> {
        val sections = mutableListOf<String>()
        var current  = StringBuilder()
        for (line in text.lines()) {
            val isHeader = line.startsWith("## ") || line.startsWith("# ")
            if (isHeader && current.isNotBlank()) {
                sections.add(current.toString().trim())
                current = StringBuilder()
            }
            current.appendLine(line)
        }
        if (current.isNotBlank()) sections.add(current.toString().trim())
        return sections.ifEmpty { listOf(text.trim()) }
    }

    // 헤더 섹션이 CHUNK_SIZE 초과할 때만 호출 — 섹션 내부에서만 재귀 분할
    private fun splitRecursive(text: String, separators: List<String>): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)

        val sepIdx = separators.indexOfFirst { text.contains(it) }
        if (sepIdx == -1) return chunkByChars(text)

        val separator = separators[sepIdx]
        val nextSeps  = separators.subList(sepIdx + 1, separators.size)
        val splits    = text.split(separator).map { it.trim() }.filter { it.isNotBlank() }

        val goodSplits  = mutableListOf<String>()
        val finalChunks = mutableListOf<String>()
        for (s in splits) {
            if (s.length <= CHUNK_SIZE) {
                goodSplits.add(s)
            } else {
                if (goodSplits.isNotEmpty()) {
                    finalChunks.addAll(mergeSplits(goodSplits))
                    goodSplits.clear()
                }
                finalChunks.addAll(splitRecursive(s, nextSeps))
            }
        }
        if (goodSplits.isNotEmpty()) finalChunks.addAll(mergeSplits(goodSplits))
        return finalChunks
    }

    private fun mergeSplits(pieces: List<String>): List<String> {
        val chunks  = mutableListOf<String>()
        val current = ArrayDeque<String>()
        var currentLen = 0
        for (piece in pieces) {
            if (currentLen + piece.length > CHUNK_SIZE && current.isNotEmpty()) {
                chunks.add(current.joinToString("\n").trim())
                while (currentLen > CHUNK_OVERLAP && current.isNotEmpty()) {
                    currentLen -= current.removeFirst().length
                }
            }
            current.addLast(piece)
            currentLen += piece.length
        }
        if (current.isNotEmpty()) chunks.add(current.joinToString("\n").trim())
        return chunks
    }

    private fun chunkByChars(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + CHUNK_SIZE, text.length)
            chunks.add(text.substring(start, end))
            if (end == text.length) break
            start += CHUNK_SIZE - CHUNK_OVERLAP
        }
        return chunks
    }

    private fun extractHeading(text: String): String {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: return ""
        return when {
            firstLine.startsWith("## ") -> firstLine.removePrefix("## ").trim()
            firstLine.startsWith("# ")  -> firstLine.removePrefix("# ").trim()
            else -> ""
        }
    }

    private fun scoreSection(text: String, tools: List<KnowledgeTool>): Pair<String, KnowledgeCategory?> {
        val norm = text.lowercase()
        var bestScore = 0
        var bestToolId = tools.firstOrNull()?.id ?: ""
        var bestCat: KnowledgeCategory? = null
        tools.forEach { tool ->
            tool.categories.forEach { cat ->
                val score = cat.keywords.count { norm.contains(it.lowercase()) }
                if (score > bestScore) {
                    bestScore = score
                    bestToolId = tool.id
                    bestCat = cat
                }
            }
        }
        return bestToolId to bestCat
    }
}
