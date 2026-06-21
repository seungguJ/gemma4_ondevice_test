package com.example.gemma4ondevicetest

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class ModelRuntimeResult<out T> {
    data class Success<T>(val value: T) : ModelRuntimeResult<T>()
    data object Busy : ModelRuntimeResult<Nothing>()
    data object MissingModel : ModelRuntimeResult<Nothing>()
    data class LoadFailed(val message: String) : ModelRuntimeResult<Nothing>()
}

object ModelRuntimeGate {
    private val backgroundMutex = Mutex()

    val isLoaded: Boolean
        get() = LlmEngine.isLoaded

    fun lastError(): String? = LlmEngine.getLastError()

    fun freeAll() {
        LlmEngine.free()
    }

    fun clearSession(sessionId: String) {
        LlmEngine.clearSession(sessionId)
    }

    fun loadForUser(
        context: Context,
        source: ModelStore.ModelSource = ModelStore.getSelectedModel(context),
        config: LlmEngine.LlmConfig = LlmEngine.LlmConfig()
    ): Boolean {
        return LlmEngine.loadModel(context, source, config)
    }

    /**
     * Background AI work must not reuse or compete with a UI-owned model.
     * The gate owns load -> work -> clearSession -> free for Worker paths.
     */
    suspend fun <T> runBackgroundExclusive(
        context: Context,
        sessionId: String,
        source: ModelStore.ModelSource = ModelStore.getSelectedModel(context),
        block: () -> T
    ): ModelRuntimeResult<T> = withContext(Dispatchers.IO) {
        if (LlmEngine.isLoaded) return@withContext ModelRuntimeResult.Busy

        backgroundMutex.withLock {
            if (LlmEngine.isLoaded) return@withLock ModelRuntimeResult.Busy
            if (!ModelStore.hasModel(context, source)) return@withLock ModelRuntimeResult.MissingModel

            if (!LlmEngine.loadModel(context, source)) {
                return@withLock ModelRuntimeResult.LoadFailed(
                    LlmEngine.getLastError() ?: "모델 로드에 실패했습니다."
                )
            }

            try {
                ModelRuntimeResult.Success(block())
            } finally {
                LlmEngine.clearSession(sessionId)
                LlmEngine.free()
            }
        }
    }
}
