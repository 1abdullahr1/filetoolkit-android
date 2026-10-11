package com.filetoolkit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    var maintainAspectRatio by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
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
                    if (info.width != null && info.height != null) {
                        widthInput = info.width.toString()
                        heightInput = info.height.toString()
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
                items(selectedFiles) { file ->
                    SelectedFileItemRow(
                        fileInfo = file,
                        onRemove = {
                            selectedFiles = selectedFiles.filter { it.path != file.path }
                        }
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
                            onValueChange = { widthInput = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        DarkInputField(
                            label = "Height (px)",
                            value = heightInput,
                            onValueChange = { heightInput = it },
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

            // Action Button (Screenshot 3)
            item {
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = tool.title,
                    enabled = selectedFiles.isNotEmpty(),
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                if (tool.id == "img_to_pdf") {
                                    val outFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.pdf")
                                    val req = ImagesToPdfRequest(
                                        inputPaths = selectedFiles.map { it.path },
                                        outputPath = outFile.absolutePath
                                    )
                                    val result = NativeBridge.createPdfFromImages(req)
                                    isProcessing = false
                                    onResult(result, tool.title)
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
                                    isProcessing = false
                                    val primaryResult = batchRes.items.firstOrNull() ?: OperationResult(false, errorMessage = "Batch failed")
                                    onResult(primaryResult, tool.title)
                                } else {
                                    val single = selectedFiles.first()
                                    val outFile = File(context.cacheDir, "conv_${System.currentTimeMillis()}.$targetFormat")
                                    val req = ImageConvertRequest(
                                        inputPath = single.path,
                                        outputPath = outFile.absolutePath,
                                        targetFormat = targetFormat,
                                        quality = quality.toInt(),
                                        width = widthInput.toIntOrNull(),
                                        height = heightInput.toIntOrNull(),
                                        keepAspectRatio = maintainAspectRatio
                                    )
                                    val result = NativeBridge.convertImg(req)
                                    isProcessing = false
                                    onResult(result, tool.title)
                                }
                            } catch (e: Exception) {
                                isProcessing = false
                                errorMessage = e.message
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        if (isProcessing) {
            ProcessingModal(message = "Processing image...")
        }
    }
}
