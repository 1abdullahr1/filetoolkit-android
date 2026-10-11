package com.filetoolkit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.filetoolkit.app.data.*
import com.filetoolkit.app.ui.components.*
import com.filetoolkit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

@Composable
fun ImageToolScreen(
    tool: ToolDefinition,
    onNavigateBack: () -> Unit,
    onResult: (OperationResult, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var targetFormat by remember { mutableStateOf("jpeg") }
    var quality by remember { mutableStateOf(85f) }
    var widthInput by remember { mutableStateOf("1920") }
    var heightInput by remember { mutableStateOf("1080") }
    var isCropMode by remember { mutableStateOf(tool.id == "img_resize") }
    var cropCoords by remember { mutableStateOf<CropCoordinates?>(null) }
    var baseRatio by remember { mutableStateOf<Float?>(null) }
    var maintainAspectRatio by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun copyUriToTemp(uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = if (mime.contains("png")) "png" else if (mime.contains("webp")) "webp" else "jpg"
            val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = copyUriToTemp(uri)
                if (tempFile != null) {
                    val info = NativeBridge.inspect(tempFile.absolutePath)
                    selectedFiles = listOf(info)
                    if (info.width != null && info.height != null && info.height > 0) {
                        widthInput = info.width.toString()
                        heightInput = info.height.toString()
                        baseRatio = info.width.toFloat() / info.height.toFloat()
                    }
                }
            }
        }
    }

    val multiplePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val list = mutableListOf<FileInfo>()
                for (uri in uris) {
                    val tempFile = copyUriToTemp(uri)
                    if (tempFile != null) {
                        list.add(NativeBridge.inspect(tempFile.absolutePath))
                    }
                }
                selectedFiles = list
            }
        }
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            AppHeader(
                title = tool.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // File Picker / List
            if (selectedFiles.isEmpty()) {
                item {
                    DashedFilePickerBox(
                        title = if (tool.acceptsMultiple) "Select images" else "Select image",
                        icon = Icons.Default.Image,
                        onClick = {
                            if (tool.acceptsMultiple) {
                                multiplePickerLauncher.launch("image/*")
                            } else {
                                singlePickerLauncher.launch("image/*")
                            }
                        }
                    )
                }
            } else {
                // Section subtitle
                item {
                    Text(
                        text = "Selected files · ${selectedFiles.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // Live preview or interactive crop for primary image
                if (selectedFiles.isNotEmpty()) {
                    if (isCropMode && selectedFiles.size == 1) {
                        item {
                            InteractiveImageCropper(
                                imagePath = selectedFiles.first().path,
                                onCropChange = { coords ->
                                    cropCoords = coords
                                    widthInput = coords.width.toString()
                                    heightInput = coords.height.toString()
                                }
                            )
                        }
                    } else {
                        item {
                            ImagePreviewCard(
                                filePath = selectedFiles.first().path,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }

                // File list with Move Up and Move Down accessible reordering controls
                itemsIndexed(selectedFiles) { index, file ->
                    SelectedFileItemRow(
                        fileInfo = file,
                        onRemove = {
                            selectedFiles = selectedFiles.filter { it.path != file.path }
                        },
                        onMoveUp = if (index > 0) {
                            {
                                val list = selectedFiles.toMutableList()
                                Collections.swap(list, index, index - 1)
                                selectedFiles = list
                            }
                        } else null,
                        onMoveDown = if (index < selectedFiles.size - 1) {
                            {
                                val list = selectedFiles.toMutableList()
                                Collections.swap(list, index, index + 1)
                                selectedFiles = list
                            }
                        } else null
                    )
                }

                if (tool.acceptsMultiple) {
                    item {
                        DashedFilePickerBox(
                            title = "Add more images",
                            icon = Icons.Default.Add,
                            onClick = { multiplePickerLauncher.launch("image/*") }
                        )
                    }
                }
            }

            // Dimensions Section (Screenshot 3)
            if (tool.id == "img_resize" || tool.id == "img_convert") {
                if (selectedFiles.size == 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCropMode = !isCropMode }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Direct finger crop",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            Switch(
                                checked = isCropMode,
                                onCheckedChange = { isCropMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureBlack,
                                    checkedTrackColor = PureWhite,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = SurfaceInput
                                )
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Dimensions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DarkInputField(
                            label = "Width (px)",
                            value = widthInput,
                            onValueChange = { newWStr ->
                                widthInput = newWStr
                                val newW = newWStr.toIntOrNull()
                                if (maintainAspectRatio && newW != null && newW > 0) {
                                    val r = baseRatio ?: (widthInput.toFloatOrNull()?.let { w ->
                                        heightInput.toFloatOrNull()?.let { h -> if (h > 0) w / h else null }
                                    } ?: 1f)
                                    val newH = (newW / r).toInt()
                                    if (newH > 0 && newH.toString() != heightInput) {
                                        heightInput = newH.toString()
                                    }
                                }
                            },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        DarkInputField(
                            label = "Height (px)",
                            value = heightInput,
                            onValueChange = { newHStr ->
                                heightInput = newHStr
                                val newH = newHStr.toIntOrNull()
                                if (maintainAspectRatio && newH != null && newH > 0) {
                                    val r = baseRatio ?: (widthInput.toFloatOrNull()?.let { w ->
                                        heightInput.toFloatOrNull()?.let { h -> if (h > 0) w / h else null }
                                    } ?: 1f)
                                    val newW = (newH * r).toInt()
                                    if (newW > 0 && newW.toString() != widthInput) {
                                        widthInput = newW.toString()
                                    }
                                }
                            },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { maintainAspectRatio = !maintainAspectRatio }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Maintain aspect ratio",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (maintainAspectRatio) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (maintainAspectRatio) TextPrimary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Output format Section (Screenshot 3)
            item {
                Text(
                    text = "Output format",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            item {
                FormatChipSelector(
                    options = listOf("JPEG", "PNG", "WebP", "BMP"),
                    selectedOption = targetFormat,
                    onOptionSelected = { targetFormat = it.lowercase() }
                )
            }

            // Quality slider for lossy formats
            if (targetFormat == "jpeg" || targetFormat == "webp") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Quality", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("${quality.toInt()}%", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        Slider(
                            value = quality,
                            onValueChange = { quality = it },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = BorderSubtle
                            )
                        )
                    }
                }
            }

            // Live estimated file size indicator
            val firstFileSize = selectedFiles.firstOrNull()?.sizeBytes ?: 0L
            if (firstFileSize > 0L) {
                val factor = when (targetFormat.lowercase()) {
                    "jpeg", "jpg" -> 0.75f * (quality / 100f)
                    "webp" -> 0.60f * (quality / 100f)
                    "png" -> 1.10f
                    "bmp" -> 2.50f
                    else -> 1.0f
                }
                val estBytes = (firstFileSize * factor).toLong().coerceAtLeast(1024L)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated size",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "~${formatFileSize(estBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Action Button (Screenshot 3)
            item {
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = tool.title,
                    enabled = selectedFiles.isNotEmpty(),
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        processingJob = scope.launch(Dispatchers.IO) {
                            try {
                                if (tool.id == "img_to_pdf") {
                                    val outFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.pdf")
                                    val req = ImagesToPdfRequest(
                                        inputPaths = selectedFiles.map { it.path },
                                        outputPath = outFile.absolutePath
                                    )
                                    val result = NativeBridge.createPdfFromImages(req)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onResult(result, tool.title)
                                    }
                                } else if (selectedFiles.size > 1) {
                                    val batchItems = selectedFiles.map { file ->
                                        val outFile = File(context.cacheDir, "conv_${System.currentTimeMillis()}_${file.filename}.$targetFormat")
                                        ImageConvertRequest(
                                            inputPath = file.path,
                                            outputPath = outFile.absolutePath,
                                            targetFormat = targetFormat,
                                            quality = quality.toInt(),
                                            width = widthInput.toIntOrNull(),
                                            height = heightInput.toIntOrNull(),
                                            keepAspectRatio = maintainAspectRatio
                                        )
                                    }
                                    val batchReq = BatchImageRequest(items = batchItems)
                                    val batchRes = NativeBridge.batchConvertImg(batchReq)
                                    val primaryResult = batchRes.items.firstOrNull() ?: OperationResult(false, errorMessage = "Batch failed")
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onResult(primaryResult, tool.title)
                                    }
                                } else {
                                    val single = selectedFiles.first()
                                    val outFile = File(context.cacheDir, "conv_${System.currentTimeMillis()}.$targetFormat")
                                    val req = ImageConvertRequest(
                                        inputPath = single.path,
                                        outputPath = outFile.absolutePath,
                                        targetFormat = targetFormat,
                                        quality = quality.toInt(),
                                        width = if (isCropMode) null else widthInput.toIntOrNull(),
                                        height = if (isCropMode) null else heightInput.toIntOrNull(),
                                        cropX = if (isCropMode) cropCoords?.x else null,
                                        cropY = if (isCropMode) cropCoords?.y else null,
                                        cropWidth = if (isCropMode) cropCoords?.width else null,
                                        cropHeight = if (isCropMode) cropCoords?.height else null,
                                        keepAspectRatio = maintainAspectRatio
                                    )
                                    val result = NativeBridge.convertImg(req)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onResult(result, tool.title)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    errorMessage = e.message
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        if (isProcessing) {
            ProcessingModal(
                message = "Processing image...",
                onCancel = {
                    processingJob?.cancel()
                    isProcessing = false
                }
            )
        }
    }
}
