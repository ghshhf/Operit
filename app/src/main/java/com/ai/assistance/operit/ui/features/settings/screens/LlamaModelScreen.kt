package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.llama.LlamaCatalogModel
import com.ai.assistance.operit.data.llama.LlamaDownloadState
import com.ai.assistance.operit.data.llama.LlamaModelCatalog
import com.ai.assistance.operit.data.llama.LlamaModelDownloadManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LlamaModelScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { LlamaModelDownloadManager.getInstance(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var importingFileName by remember { mutableStateOf<String?>(null) }
    var importedBytes by remember { mutableStateOf(0L) }
    var installedFiles by remember { mutableStateOf(downloadManager.getDownloadedFiles()) }

    val refreshInstalled: () -> Unit = { installedFiles = downloadManager.getDownloadedFiles() }

    val filteredModels = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            LlamaModelCatalog.models
        } else {
            LlamaModelCatalog.models.filter { model ->
                model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.description.contains(searchQuery, ignoreCase = true) ||
                    model.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
            importingFileName = displayName
            importedBytes = 0L
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val target = File(downloadManager.modelDir, displayName)
                            if (target.exists()) {
                                throw IllegalStateException(context.getString(R.string.llama_import_exists))
                            }
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(1024 * 1024)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        importedBytes += read
                                    }
                                }
                            } ?: throw IllegalStateException(context.getString(R.string.llama_download_response_empty))
                        }
                    }
            importingFileName = null
            refreshInstalled()
            val message = result.fold(
                onSuccess = { context.getString(R.string.llama_import_success, displayName) },
                onFailure = { context.getString(R.string.llama_import_failed, it.message ?: "") }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    CustomScaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.llama_model_manager_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 目录说明 + 导入入口
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.llama_model_dir_hint),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = importingFileName == null,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.llama_import_button),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                    if (importingFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.llama_importing, importingFileName ?: ""),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBytes(importedBytes),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.llama_search_models)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filteredModels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.llama_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(filteredModels, key = { it.id }) { model ->
                    CatalogModelCard(
                        model = model,
                        downloadManager = downloadManager,
                        onDelete = { showDeleteDialog = model.fileName }
                    )
                }

                // 已安装文件（含手动导入、非目录条目）
                if (searchQuery.isBlank()) {
                    item {
                        Text(
                            text = stringResource(R.string.llama_installed_files_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    if (installedFiles.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.llama_installed_empty),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(installedFiles, key = { "installed:${it.name}" }) { file ->
                            InstalledFileRow(
                                file = file,
                                onDelete = { showDeleteDialog = file.name }
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { fileName ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.llama_delete_model)) },
            text = { Text(stringResource(R.string.llama_confirm_delete_file, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val catalogModel = LlamaModelCatalog.models.firstOrNull { it.fileName == fileName }
                        if (catalogModel != null) {
                            downloadManager.deleteModel(catalogModel.id)
                        } else {
                            File(downloadManager.modelDir, fileName).delete()
                        }
                        refreshInstalled()
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CatalogModelCard(
    model: LlamaCatalogModel,
    downloadManager: LlamaModelDownloadManager,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val downloadState by downloadManager.getDownloadState(model.id).collectAsState()

    val canDelete = when (downloadState) {
        is LlamaDownloadState.Completed, is LlamaDownloadState.Failed, is LlamaDownloadState.Paused -> true
        is LlamaDownloadState.Idle -> downloadManager.isModelDownloaded(model.id)
        else -> false
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (model.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        model.tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.llama_model_meta,
                    model.parameterCount,
                    model.quantization,
                    model.contextLength.toString(),
                    formatBytes(model.sizeBytes)
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (model.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (downloadState) {
                is LlamaDownloadState.Idle, is LlamaDownloadState.Completed -> {
                    if (downloadManager.isModelDownloaded(model.id)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.llama_download_complete),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp),
                                enabled = canDelete
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.llama_delete_model),
                                    tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { downloadManager.downloadModel(model.id) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.llama_start_download),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                is LlamaDownloadState.Connecting -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.llama_connecting),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is LlamaDownloadState.Downloading -> {
                    val state = downloadState as LlamaDownloadState.Downloading
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${(state.progress * 100).toInt()}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    maxLines = 1
                                )
                                Text(
                                    text = state.speed,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { downloadManager.pauseDownload(model.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.llama_pause),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                is LlamaDownloadState.Paused -> {
                    val state = downloadState as LlamaDownloadState.Paused
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.llama_download_paused)} · ${formatBytes(state.downloadedBytes)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { downloadManager.downloadModel(model.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.llama_resume),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                is LlamaDownloadState.Failed -> {
                    val state = downloadState as LlamaDownloadState.Failed
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { downloadManager.downloadModel(model.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.mnn_retry),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledFileRow(
    file: File,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBytes(file.length()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.llama_delete_model),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
