package com.ai.assistance.operit.data.llama

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class LlamaDownloadState {
    object Idle : LlamaDownloadState()
    object Connecting : LlamaDownloadState()
    data class Downloading(
        val progress: Float,
        val speed: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : LlamaDownloadState()
    data class Paused(val progress: Float, val downloadedBytes: Long) : LlamaDownloadState()
    data class Failed(val error: String) : LlamaDownloadState()
    data object Completed : LlamaDownloadState()
}

/**
 * Downloads catalog GGUF models into the directory [com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher]
 * scans (Downloads/Operit/models/llama), so a finished download shows up in the
 * local model list without any extra registration step.
 *
 * Unlike the MNN manager this handles single-file direct URLs only: every
 * catalog source is a direct file URL, and multi-source entries fall back to
 * the next source when one fails (a deliberate product decision, not error
 * swallowing - if all sources fail the state is Failed with the last error).
 */
class LlamaModelDownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LlamaModelDownloadManager"
        private const val TEMP_SUFFIX = ".part"
        private const val PREFS_NAME = "llama_model_downloads"
        private const val KEY_DOWNLOADED_IDS = "downloaded_ids"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

        @Volatile
        private var instance: LlamaModelDownloadManager? = null

        fun getInstance(context: Context): LlamaModelDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: LlamaModelDownloadManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadStates = ConcurrentHashMap<String, MutableStateFlow<LlamaDownloadState>>()
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val prefs: android.content.SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Must stay identical to [com.ai.assistance.operit.api.chat.llmprovider.LlamaProvider.getModelsDir]
     * (Downloads/Operit/models/llama): ModelListFetcher scans this directory to
     * build the local model list, so a finished download shows up there directly.
     */
    val modelDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Operit/models/llama"
    )

    init {
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
    }

    fun getDownloadState(modelId: String): StateFlow<LlamaDownloadState> {
        return downloadStates.getOrPut(modelId) {
            MutableStateFlow(
                if (isModelDownloaded(modelId)) LlamaDownloadState.Completed else LlamaDownloadState.Idle
            )
        }.asStateFlow()
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val catalogModel = LlamaModelCatalog.findById(modelId) ?: return false
        val targetFile = File(modelDir, catalogModel.fileName)
        if (!targetFile.isFile) return false
        // A leftover .part means the rename never happened, so the target is stale.
        return !File(modelDir, catalogModel.fileName + TEMP_SUFFIX).exists()
    }

    fun getDownloadedModelIds(): Set<String> {
        return prefs.getStringSet(KEY_DOWNLOADED_IDS, emptySet()) ?: emptySet()
    }

    /**
     * Starts (or resumes) downloading [modelId]. Sources are tried in catalog
     * order; a failing source falls back to the next one.
     */
    fun downloadModel(modelId: String) {
        val catalogModel = LlamaModelCatalog.findById(modelId)
        if (catalogModel == null) {
            AppLogger.w(TAG, "Catalog has no model with id $modelId")
            return
        }
        val currentState = getDownloadState(modelId).value
        if (currentState is LlamaDownloadState.Downloading || currentState is LlamaDownloadState.Connecting) {
            AppLogger.w(TAG, "Download for $modelId already running, ignoring click")
            return
        }
        if (catalogModel.sources.isEmpty()) {
            updateDownloadState(modelId, LlamaDownloadState.Failed(context.getString(R.string.llama_download_no_source)))
            return
        }

        downloadJobs[modelId]?.cancel()
        pauseFlags[modelId] = false
        updateDownloadState(modelId, LlamaDownloadState.Connecting)

        val job = applicationScope.launch {
            var lastError: String = context.getString(R.string.llama_download_no_source)
            var anySourceAttempted = false
            for ((_, url) in catalogModel.sources) {
                if (pauseFlags[modelId] == true) {
                    return@launch
                }
                anySourceAttempted = true
                try {
                    downloadSingleFile(modelId, catalogModel, url)
                    markDownloaded(modelId)
                    updateDownloadState(modelId, LlamaDownloadState.Completed)
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: context.getString(R.string.llama_download_unknown_error)
                    AppLogger.w(TAG, "Source failed for $modelId ($url): $lastError")
                }
            }
            if (anySourceAttempted) {
                updateDownloadState(modelId, LlamaDownloadState.Failed(lastError))
            }
        }
        downloadJobs[modelId] = job
        job.invokeOnCompletion { downloadJobs.remove(modelId) }
    }

    private suspend fun downloadSingleFile(
        modelId: String,
        catalogModel: LlamaCatalogModel,
        url: String
    ) = withContext(Dispatchers.IO) {
        val targetFile = File(modelDir, catalogModel.fileName)
        val tempFile = File(modelDir, catalogModel.fileName + TEMP_SUFFIX)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val headSize = try {
            val headResponse = okHttpClient.newCall(Request.Builder().url(url).head().build()).execute()
            headResponse.use { it.header("Content-Length")?.toLongOrNull() ?: -1L }
        } catch (e: Exception) {
            AppLogger.w(TAG, "HEAD failed for $url: ${e.message}")
            -1L
        }

        if (targetFile.isFile && headSize > 0 && targetFile.length() == headSize) {
            AppLogger.d(TAG, "${catalogModel.fileName} already complete, skipping download")
            return@withContext
        }

        var downloadedBytes = if (tempFile.isFile) tempFile.length() else 0L
        if (targetFile.isFile && (headSize <= 0 || targetFile.length() != headSize)) {
            // Unverifiable or size-mismatched leftovers are worse than no file.
            targetFile.delete()
        }

        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        response.use {
            if (!response.isSuccessful && response.code != 206) {
                throw java.io.IOException(
                    context.getString(R.string.llama_download_failed_http, response.code, response.message)
                )
            }
            val body = response.body ?: throw java.io.IOException(context.getString(R.string.llama_download_response_empty))
            if (response.code == 200 && downloadedBytes > 0) {
                // Server ignored the Range header: the payload starts at byte 0.
                tempFile.delete()
                downloadedBytes = 0L
            }
            val contentLength = body.contentLength()
            val totalBytes = if (response.code == 206) downloadedBytes + contentLength else contentLength

            var currentDownloaded = downloadedBytes
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloaded = downloadedBytes
            body.byteStream().use { input ->
                FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (pauseFlags[modelId] == true) {
                            val progress = if (totalBytes > 0) currentDownloaded.toFloat() / totalBytes else 0f
                            updateDownloadState(modelId, LlamaDownloadState.Paused(progress, currentDownloaded))
                            return@withContext
                        }
                        output.write(buffer, 0, bytesRead)
                        currentDownloaded += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS) {
                            val speedBytesPerSec =
                                (currentDownloaded - lastDownloaded) / ((currentTime - lastUpdateTime) / 1000.0)
                            val progress = if (totalBytes > 0) currentDownloaded.toFloat() / totalBytes else 0f
                            updateDownloadState(
                                modelId,
                                LlamaDownloadState.Downloading(
                                    progress,
                                    formatSpeed(speedBytesPerSec),
                                    currentDownloaded,
                                    totalBytes
                                )
                            )
                            lastUpdateTime = currentTime
                            lastDownloaded = currentDownloaded
                        }
                    }
                }
            }

            if (headSize > 0 && currentDownloaded != headSize) {
                throw java.io.IOException(
                    context.getString(R.string.llama_download_size_mismatch, headSize, currentDownloaded)
                )
            }
            if (!tempFile.renameTo(targetFile)) {
                throw java.io.IOException(context.getString(R.string.llama_download_rename_failed))
            }
            verifyGguf(targetFile)
        }
    }

    /** Rejects corrupt downloads instead of letting llama.cpp fail later with an opaque error. */
    private fun verifyGguf(file: File) {
        try {
            GgufHeaderParser.parse(file)
        } catch (e: Exception) {
            file.delete()
            throw java.io.IOException(
                context.getString(R.string.llama_download_invalid_gguf, e.message ?: "")
            )
        }
    }

    fun pauseDownload(modelId: String) {
        pauseFlags[modelId] = true
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        updateDownloadState(modelId, LlamaDownloadState.Idle)
    }

    fun deleteModel(modelId: String): Boolean {
        val catalogModel = LlamaModelCatalog.findById(modelId) ?: return false
        val targetFile = File(modelDir, catalogModel.fileName)
        val tempFile = File(modelDir, catalogModel.fileName + TEMP_SUFFIX)
        val deletedTarget = !targetFile.exists() || targetFile.delete()
        val deletedTemp = !tempFile.exists() || tempFile.delete()
        unmarkDownloaded(modelId)
        downloadStates[modelId]?.value = LlamaDownloadState.Idle
        return deletedTarget && deletedTemp
    }

    fun getDownloadedFiles(): List<File> {
        return LlamaModelCatalog.models.map { File(modelDir, it.fileName) }.filter { it.isFile }
    }

    private fun markDownloaded(modelId: String) {
        val ids = getDownloadedModelIds().toMutableSet()
        ids.add(modelId)
        prefs.edit().putStringSet(KEY_DOWNLOADED_IDS, ids).apply()
    }

    private fun unmarkDownloaded(modelId: String) {
        val ids = getDownloadedModelIds().toMutableSet()
        ids.remove(modelId)
        prefs.edit().putStringSet(KEY_DOWNLOADED_IDS, ids).apply()
    }

    private fun updateDownloadState(modelId: String, state: LlamaDownloadState) {
        downloadStates.getOrPut(modelId) { MutableStateFlow(state) }.value = state
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond < 1024 -> "${bytesPerSecond.toInt()} B/s"
            bytesPerSecond < 1024 * 1024 -> "${(bytesPerSecond / 1024).toInt()} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024))
        }
    }
}
