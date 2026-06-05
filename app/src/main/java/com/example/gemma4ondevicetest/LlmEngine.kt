package com.example.gemma4ondevicetest

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

object LlmEngine {
    private const val TAG = "LlmEngine"
    private val lock = Any()

    private var engine: Engine? = null
    private var loaded = false
    private var lastError: String? = null
    private val conversations = mutableMapOf<String, Conversation>()

    var lastLoadDurationMs: Long = -1L
        private set
    var lastFreeTimestamp: Long = -1L
        private set

    val isLoaded: Boolean
        get() = loaded

    fun getLastError(): String? = lastError

    init {
        try {
            System.loadLibrary("litertlm_jni")
            Log.i(TAG, "LiteRT-LM native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load LiteRT-LM JNI library", e)
            lastError = "LiteRT-LM JNI 로드 실패: ${e.message}"
        }
    }

    data class LlmConfig(
        val maxTokens: Int = 256,
        val contextSize: Int = 2048,
        val systemInstruction: String = "반드시 한국어로만 답하라. You must respond only in Korean. 너는 간결하고 자연스러운 한국어 도우미다."
    )

    fun loadModel(
        context: Context,
        source: ModelStore.ModelSource = ModelStore.getSelectedModel(context),
        config: LlmConfig = LlmConfig()
    ): Boolean {
        synchronized(lock) {
            if (loaded && engine != null) {
                return true
            }
            if (isUnsupportedEmulator()) {
                lastError = "Android 에뮬레이터에서는 LiteRT-LM 온디바이스 실행이 제한될 수 있습니다. 실제 ARM64 기기를 사용하세요."
                return false
            }

            val modelFile = ModelStore.prepareRuntimeModelFile(context, source)
                .getOrElse {
                    lastError = it.message ?: "모델 파일을 준비하지 못했습니다."
                    return false
                }

            return try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                val engineConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path,
                    maxNumTokens = config.contextSize
                )
                val startMs = System.currentTimeMillis()
                engine = Engine(engineConfig).also { it.initialize() }
                lastLoadDurationMs = System.currentTimeMillis() - startMs
                loaded = engine != null
                if (!loaded) {
                    lastError = "엔진 초기화에 실패했습니다."
                } else {
                    lastError = null
                    Log.i(TAG, "Model loaded in ${lastLoadDurationMs}ms (${modelFile.name})")
                }
                loaded
            } catch (e: Exception) {
                engine?.close()
                engine = null
                loaded = false
                lastLoadDurationMs = -1L
                lastError = "모델 로드 실패: ${e.message}"
                Log.e(TAG, "Model load failed", e)
                false
            }
        }
    }

    fun hasConversation(sessionId: String): Boolean {
        synchronized(lock) { return conversations.containsKey(sessionId) }
    }

    fun generateForSession(
        sessionId: String,
        prompt: String,
        config: LlmConfig = LlmConfig()
    ): String {
        synchronized(lock) {
            val currentEngine = engine
            if (!loaded || currentEngine == null) {
                lastError = "모델이 아직 로드되지 않았습니다."
                return ""
            }
            return try {
                val conversation = conversations.getOrPut(sessionId) {
                    currentEngine.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(config.systemInstruction))
                    )
                }
                val output = StringBuilder()
                runBlocking {
                    conversation.sendMessageAsync(prompt).collect { chunk ->
                        output.append(chunk.toString())
                        if (output.length >= config.maxTokens * 6) return@collect
                    }
                }
                output.toString().trim()
            } catch (e: Exception) {
                conversations.remove(sessionId)?.runCatching { close() }
                lastError = "응답 생성 실패: ${e.message}"
                Log.e(TAG, "Session generation failed", e)
                ""
            }
        }
    }

    fun clearSession(sessionId: String) {
        synchronized(lock) {
            conversations.remove(sessionId)?.runCatching { close() }
            Log.i(TAG, "Cleared conversation for session $sessionId")
        }
    }

    fun free() {
        synchronized(lock) {
            conversations.values.forEach { runCatching { it.close() } }
            conversations.clear()
            if (engine != null) {
                lastFreeTimestamp = System.currentTimeMillis()
                Log.i(TAG, "Freeing engine at ${lastFreeTimestamp}")
                try {
                    engine?.close()
                } catch (_: Exception) {
                }
                engine = null
                loaded = false
                Log.i(TAG, "Engine freed (was loaded for ${
                    if (lastLoadDurationMs >= 0) "${lastLoadDurationMs}ms" else "unknown"
                })")
            }
        }
    }

    private fun isUnsupportedEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            hardware.contains("ranchu") ||
            product.contains("sdk_gphone")
    }
}
