package com.example.gemma4ondevicetest

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelStore {
    private const val PUBLIC_DIR = "Download/gemma4_ondevice_test"
    private const val RUNTIME_DIR = "llm_runtime"
    private const val PREFS_NAME = "model_store"
    private const val KEY_SELECTED_MODEL = "selected_model"

    data class ModelSource(
        val id: String,
        val label: String,
        val fileName: String,
        val downloadUrl: String? = null,
        val expectedSizeBytes: Long? = null
    )

    val CUSTOM = ModelSource(
        id = "custom",
        label = "직접 선택",
        fileName = "model-custom.litertlm"
    )

    val GEMMA_4 = ModelSource(
        id = "gemma4",
        label = "Gemma 4 E2B (2bit)",
        fileName = "model-gemma-4-e2b.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedSizeBytes = 2_583_085_056L
    )

    val allSources = listOf(CUSTOM, GEMMA_4)

    fun getSelectedModel(context: Context): ModelSource {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_SELECTED_MODEL, CUSTOM.id)
        return allSources.firstOrNull { it.id == id } ?: CUSTOM
    }

    fun setSelectedModel(context: Context, source: ModelSource) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_MODEL, source.id)
            .apply()
    }

    fun hasModel(context: Context, source: ModelSource = getSelectedModel(context)): Boolean {
        val publicLocation = getPublicLocation(context, source) ?: return false
        val size = publicLocation.length(context)
        val expected = source.expectedSizeBytes
        return size > 0L && (expected == null || size > expected * 0.9)
    }

    suspend fun importModel(context: Context, sourceUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        importModel(context, sourceUri, CUSTOM, selectAfterImport = true)
    }

    suspend fun importModel(
        context: Context,
        sourceUri: Uri,
        source: ModelSource,
        selectAfterImport: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (source == CUSTOM) {
                // SAF URI를 직접 저장 — 2.5GB 복사 불필요
                // 런타임 복사는 prepareRuntimeModelFile에서 최초 1회만 수행
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                setStoredLocation(context, source, PublicLocation.ContentUri(sourceUri))
                clearRuntimeCopy(context, source)
                if (selectAfterImport) setSelectedModel(context, source)
                File(getUserVisiblePath(source))
            } else {
                val copied = copyUriToPublicDownloads(context, sourceUri, source)
                if (selectAfterImport) setSelectedModel(context, source)
                copied
            }
        }
    }

    suspend fun installBundledModelIfAvailable(
        context: Context,
        source: ModelSource,
        assetName: String
    ): Result<File?> = withContext(Dispatchers.IO) {
        runCatching {
            if (hasModel(context, source)) {
                return@runCatching null
            }
            val publicLocation = createPublicDestination(context, source)
            context.assets.open(assetName).use { input ->
                publicLocation.openOutputStream(context).use { output ->
                    input.copyTo(output)
                }
            }
            setStoredLocation(context, source, publicLocation)
            clearRuntimeCopy(context, source)
            File(getUserVisiblePath(source))
        }
    }

    suspend fun downloadModel(
        context: Context,
        source: ModelSource,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            requireNotNull(source.downloadUrl) { "다운로드 URL이 없는 모델입니다." }

            val connection = URL(source.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode !in 200..299) {
                error("다운로드 실패: HTTP ${connection.responseCode}")
            }

            val totalBytes = when {
                connection.contentLengthLong > 0 -> connection.contentLengthLong
                source.expectedSizeBytes != null -> source.expectedSizeBytes
                else -> -1L
            }
            val publicLocation = createPublicDestination(context, source)

            connection.inputStream.use { input ->
                publicLocation.openOutputStream(context).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(progress)
                        }
                    }
                }
            }
            connection.disconnect()

            setStoredLocation(context, source, publicLocation)
            setSelectedModel(context, source)
            clearRuntimeCopy(context, source)
            onProgress(100)
            File(getUserVisiblePath(source))
        }
    }

    fun hasRuntimeFile(context: Context, source: ModelSource): Boolean {
        val runtimeFile = File(File(context.filesDir, RUNTIME_DIR), source.fileName)
        return runtimeFile.exists() && runtimeFile.length() > 0
    }

    suspend fun installAssetToRuntime(
        context: Context,
        source: ModelSource,
        assetName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val runtimeDir = File(context.filesDir, RUNTIME_DIR)
            if (!runtimeDir.exists()) runtimeDir.mkdirs()
            val runtimeFile = File(runtimeDir, source.fileName)
            val minValidSize = source.expectedSizeBytes?.let { it * 9 / 10 } ?: 1024L
            if (runtimeFile.exists() && runtimeFile.length() >= minValidSize) {
                return@runCatching runtimeFile
            }
            if (runtimeFile.exists()) runtimeFile.delete()
            context.assets.open(assetName).use { input ->
                runtimeFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            runtimeFile
        }
    }

    fun prepareRuntimeModelFile(
        context: Context,
        source: ModelSource = getSelectedModel(context)
    ): Result<File> {
        return runCatching {
            val runtimeDir = File(context.filesDir, RUNTIME_DIR)
            if (!runtimeDir.exists()) runtimeDir.mkdirs()
            val runtimeFile = File(runtimeDir, source.fileName)

            val minValidSize = source.expectedSizeBytes?.let { it * 9 / 10 } ?: 1024L
            if (runtimeFile.exists() && runtimeFile.length() >= minValidSize) {
                return@runCatching runtimeFile
            }
            if (runtimeFile.exists()) runtimeFile.delete()

            val publicLocation = getPublicLocation(context, source)
                ?: error("모델 파일이 없습니다. 먼저 ${source.label} 모델을 준비하세요.")

            publicLocation.openInputStream(context).use { input ->
                runtimeFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            runtimeFile
        }
    }

    fun describe(context: Context, source: ModelSource = getSelectedModel(context)): String {
        val publicLocation = getPublicLocation(context, source)
        if (publicLocation == null) {
            return "${source.label} · 모델 없음"
        }
        val sizeMb = publicLocation.length(context) / 1024 / 1024
        return "${source.label} · ${sizeMb}MB · ${getUserVisiblePath(source)}"
    }

    fun getUserVisiblePath(source: ModelSource): String {
        return "$PUBLIC_DIR/${source.fileName}"
    }

    private fun copyUriToPublicDownloads(context: Context, sourceUri: Uri, source: ModelSource): File {
        val publicLocation = createPublicDestination(context, source)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            publicLocation.openOutputStream(context).use { output ->
                input.copyTo(output)
            }
        } ?: error("선택한 파일을 열 수 없습니다.")

        setStoredLocation(context, source, publicLocation)
        clearRuntimeCopy(context, source)
        return File(getUserVisiblePath(source))
    }

    private fun getStoredLocation(context: Context, source: ModelSource): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("model_uri_${source.id}", null)
    }

    private fun setStoredLocation(context: Context, source: ModelSource, location: PublicLocation) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("model_uri_${source.id}", location.persistedValue)
            .apply()
    }

    private fun getPublicLocation(context: Context, source: ModelSource): PublicLocation? {
        val persisted = getStoredLocation(context, source)
        return when {
            persisted?.startsWith("content://") == true -> PublicLocation.ContentUri(Uri.parse(persisted))
            persisted?.isNotBlank() == true -> PublicLocation.DirectFile(File(persisted))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> findDownloadsUri(context, source.fileName)?.let { PublicLocation.ContentUri(it) }
            else -> {
                val legacyFile = getLegacyDownloadsFile(source)
                if (legacyFile.exists()) PublicLocation.DirectFile(legacyFile) else null
            }
        }
    }

    private fun createPublicDestination(context: Context, source: ModelSource): PublicLocation {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findDownloadsUri(context, source.fileName)?.let { existing ->
                context.contentResolver.delete(existing, null, null)
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, PUBLIC_DIR)
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Download 경로에 파일을 만들 수 없습니다.")
            PublicLocation.ContentUri(uri)
        } else {
            val file = getLegacyDownloadsFile(source)
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            PublicLocation.DirectFile(file)
        }
    }

    private fun findDownloadsUri(context: Context, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val args = arrayOf(fileName, "$PUBLIC_DIR/")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    private fun getLegacyDownloadsFile(source: ModelSource): File {
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(File(downloadsRoot, "gemma4_ondevice_test"), source.fileName)
    }

    private fun clearRuntimeCopy(context: Context, source: ModelSource) {
        val runtimeFile = File(File(context.filesDir, RUNTIME_DIR), source.fileName)
        if (runtimeFile.exists()) {
            runtimeFile.delete()
        }
    }

    private sealed interface PublicLocation {
        val persistedValue: String

        fun openInputStream(context: Context) = when (this) {
            is ContentUri -> context.contentResolver.openInputStream(uri)
                ?: error("다운로드한 모델 파일을 열 수 없습니다.")
            is DirectFile -> file.inputStream()
        }

        fun openOutputStream(context: Context) = when (this) {
            is ContentUri -> context.contentResolver.openOutputStream(uri, "w")
                ?: error("Download 경로에 모델 파일을 저장할 수 없습니다.")
            is DirectFile -> file.outputStream()
        }

        fun length(context: Context): Long = when (this) {
            is ContentUri -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            is DirectFile -> if (file.exists()) file.length() else 0L
        }

        data class ContentUri(val uri: Uri) : PublicLocation {
            override val persistedValue: String = uri.toString()
        }

        data class DirectFile(val file: File) : PublicLocation {
            override val persistedValue: String = file.absolutePath
        }
    }
}
