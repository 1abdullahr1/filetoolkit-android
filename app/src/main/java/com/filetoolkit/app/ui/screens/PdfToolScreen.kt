package com.filetoolkit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun PdfToolScreen(
    tool: ToolDefinition,
    onNavigateBack: () -> Unit,
    onResult: (OperationResult, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var pageInput by remember { mutableStateOf("1") }
    var rotationDegrees by remember { mutableStateOf(90) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var pageNumFormat by remember { mutableStateOf("Page {n} of {total}") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun copyUriToTemp(uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val tempFile = File(context.cacheDir, "input_pdf_${System.currentTimeMillis()}.pdf")
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
                val list = selectedFiles.toMutableList()
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
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section subtitle matching screenshot 1: "Selected files · 2"
            if (selectedFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Selected files · ${selectedFiles.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                items(selectedFiles) { file ->
                    SelectedFileItemRow(
                        fileInfo = file,
                        onRemove = {
                            selectedFiles = selectedFiles.filter { it.path != file.path }
                        }
                    )
                }
            }

            // Dashed Add files Box matching screenshot 1
            item {
                DashedFilePickerBox(
                    title = if (selectedFiles.isEmpty()) "Select PDF" else "Add files",
                    icon = if (selectedFiles.isEmpty()) Icons.Default.PictureAsPdf else Icons.Default.Add,
                    onClick = {
                        if (tool.acceptsMultiple) {
                            multiplePickerLauncher.launch("application/pdf")
                        } else {
                            singlePickerLauncher.launch("application/pdf")
                        }
                    }
                )
            }

            // Operation-specific options
            if (selectedFiles.isNotEmpty()) {
                when (tool.id) {
                    "pdf_extract", "pdf_delete_pages" -> {
                        item {
                            DarkInputField(
                                label = "Page numbers (comma-separated, e.g. 1, 3, 5)",
                                value = pageInput,
                                onValueChange = { pageInput = it }
                            )
                        }
                    }
                    "pdf_rotate" -> {
                        item {
                            Text("Rotation angle", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            FormatChipSelector(
                                options = listOf("90°", "180°", "270°"),
                                selectedOption = "$rotationDegrees°",
                                onOptionSelected = {
                                    rotationDegrees = it.replace("°", "").toIntOrNull() ?: 90
                                }
                            )
                        }
                    }
                    "pdf_watermark" -> {
                        item {
                            DarkInputField(
                                label = "Watermark text",
                                value = watermarkText,
                                onValueChange = { watermarkText = it }
                            )
                        }
                    }
                    "pdf_page_numbers" -> {
                        item {
                            DarkInputField(
                                label = "Footer format",
                                value = pageNumFormat,
                                onValueChange = { pageNumFormat = it }
                            )
                        }
                    }
                }
            }

            // Action Button matching screenshot 1
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
                                val outFile = File(context.cacheDir, "pdf_out_${System.currentTimeMillis()}.pdf")
                                val primaryFile = selectedFiles.first()

                                val result = when (tool.id) {
                                    "pdf_merge" -> {
                                        val req = PdfMergeRequest(
                                            inputPaths = selectedFiles.map { it.path },
                                            outputPath = outFile.absolutePath
                                        )
                                        NativeBridge.merge(req)
                                    }
                                    "pdf_extract" -> {
                                        val pages = pageInput.split(",")
                                            .mapNotNull { it.trim().toIntOrNull() }
                                            .filter { it > 0 }
                                        val req = PdfExtractPagesRequest(
                                            inputPath = primaryFile.path,
                                            outputPath = outFile.absolutePath,
                                            pageNumbers = pages
                                        )
                                        NativeBridge.extractPages(req)
                                    }
                                    "pdf_rotate" -> {
                                        val req = PdfRotatePagesRequest(
                                            inputPath = primaryFile.path,
                                            outputPath = outFile.absolutePath,
                                            rotationDegrees = rotationDegrees
                                        )
                                        NativeBridge.rotatePages(req)
                                    }
                                    "pdf_delete_pages" -> {
                                        val pages = pageInput.split(",")
                                            .mapNotNull { it.trim().toIntOrNull() }
                                            .filter { it > 0 }
                                        val req = PdfDeletePagesRequest(
                                            inputPath = primaryFile.path,
                                            outputPath = outFile.absolutePath,
                                            pageNumbers = pages
                                        )
                                        NativeBridge.deletePages(req)
                                    }
                                    "pdf_watermark" -> {
                                        val req = PdfWatermarkRequest(
                                            inputPath = primaryFile.path,
                                            outputPath = outFile.absolutePath,
                                            watermarkText = watermarkText
                                        )
                                        NativeBridge.watermark(req)
                                    }
                                    "pdf_page_numbers" -> {
                                        val req = PdfPageNumberRequest(
                                            inputPath = primaryFile.path,
                                            outputPath = outFile.absolutePath,
                                            format = pageNumFormat
                                        )
                                        NativeBridge.addNumbers(req)
                                    }
                                    "pdf_compress" -> {
                                        NativeBridge.compressPdfFile(
                                            primaryFile.path,
                                            outFile.absolutePath
                                        )
                                    }
                                    else -> OperationResult(false, errorMessage = "Unknown PDF operation")
                                }

                                isProcessing = false
                                onResult(result, tool.title)
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
            ProcessingModal(message = "Processing PDF document...")
        }
    }
}
