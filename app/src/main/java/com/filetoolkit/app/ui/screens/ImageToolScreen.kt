package com.filetoolkit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filetoolkit.app.data.*
import com.filetoolkit.app.ui.components.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
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
    var enableResize by remember { mutableStateOf(false) }
    var customWidth by remember { mutableStateOf("800") }
    var customHeight by remember { mutableStateOf("600") }
    var keepAspectRatio by remember { mutableStateOf(true) }
    var rotationDegrees by remember { mutableStateOf(0) }
    var stripMetadata by remember { mutableStateOf(true) }
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
        topBar = {
            ToolkitTopBar(
                title = tool.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pick File Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (tool.acceptsMultiple) "Select Image(s) from Device" else "Select Image from Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Supports JPEG, PNG, WebP, BMP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (tool.acceptsMultiple) {
                                    multiplePickerLauncher.launch("image/*")
                                } else {
                                    singlePickerLauncher.launch("image/*")
                                }
                            }
                        ) {
                            Text(if (selectedFiles.isEmpty()) "Browse Files" else "Change Files")
                        }
                    }
                }
            }

            // Display selected files
            if (selectedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "Selected Files (${selectedFiles.size})")
                }
                items(selectedFiles) { fileInfo ->
                    FileDisplayCard(
                        fileInfo = fileInfo,
                        onRemove = {
                            selectedFiles = selectedFiles.filter { it.path != fileInfo.path }
                        }
                    )
                }

                // If tool is images to PDF, show PDF options; otherwise show image conversion settings
                if (tool.id == "img_to_pdf") {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "PDF Generation Options",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Each selected image will be converted into a high-resolution A4 page maintaining aspect ratio.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    // Settings card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Conversion Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Target format chips
                                Column {
                                    Text("Output Format", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("jpeg", "png", "webp", "bmp").forEach { fmt ->
                                            FilterChip(
                                                selected = targetFormat == fmt,
                                                onClick = { targetFormat = fmt },
                                                label = { Text(fmt.uppercase()) }
                                            )
                                        }
                                    }
                                }

                                // Quality slider for JPEG / WebP
                                if (targetFormat == "jpeg" || targetFormat == "webp") {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Quality", style = MaterialTheme.typography.labelLarge)
                                            Text("${quality.toInt()}%", fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = quality,
                                            onValueChange = { quality = it },
                                            valueRange = 10f..100f,
                                            steps = 18
                                        )
                                    }
                                }

                                // Rotation
                                Column {
                                    Text("Rotate Image", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0, 90, 180, 270).forEach { deg ->
                                            FilterChip(
                                                selected = rotationDegrees == deg,
                                                onClick = { rotationDegrees = deg },
                                                label = { Text(if (deg == 0) "None" else "$deg°") }
                                            )
                                        }
                                    }
                                }

                                // Resize toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Custom Dimensions", style = MaterialTheme.typography.labelLarge)
                                    Switch(
                                        checked = enableResize,
                                        onCheckedChange = { enableResize = it }
                                    )
                                }

                                if (enableResize) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customWidth,
                                            onValueChange = { customWidth = it },
                                            label = { Text("Width (px)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = customHeight,
                                            onValueChange = { customHeight = it },
                                            label = { Text("Height (px)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = keepAspectRatio,
                                            onCheckedChange = { keepAspectRatio = it }
                                        )
                                        Text("Preserve aspect ratio", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                // Strip metadata
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = stripMetadata,
                                        onCheckedChange = { stripMetadata = it }
                                    )
                                    Text("Strip EXIF & location metadata", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Convert Button
                item {
                    Button(
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
                                                width = if (enableResize) customWidth.toIntOrNull() else null,
                                                height = if (enableResize) customHeight.toIntOrNull() else null,
                                                keepAspectRatio = keepAspectRatio,
                                                rotationDegrees = if (rotationDegrees > 0) rotationDegrees else null,
                                                stripMetadata = stripMetadata
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
                                            width = if (enableResize) customWidth.toIntOrNull() else null,
                                            height = if (enableResize) customHeight.toIntOrNull() else null,
                                            keepAspectRatio = keepAspectRatio,
                                            rotationDegrees = if (rotationDegrees > 0) rotationDegrees else null,
                                            stripMetadata = stripMetadata
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
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Conversion", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Text(
                        text = "Error: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (isProcessing) {
            ProcessingDialog(message = "Converting with Rust Native Engine...")
        }
    }
}
