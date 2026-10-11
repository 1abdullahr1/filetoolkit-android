package com.filetoolkit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PdfToolScreen(
    tool: ToolDefinition,
    onNavigateBack: () -> Unit,
    onResult: (OperationResult, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var originalOrder by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var pageInput by remember { mutableStateOf("1") }
    var selectedPages by remember { mutableStateOf<Set<Int>>(setOf(1)) }
    var rotationDegrees by remember { mutableStateOf(90) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var pageNumFormat by remember { mutableStateOf("Page {n} of {total}") }
    var splitPagesPerFile by remember { mutableIntStateOf(1) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingJob by remember { mutableStateOf<Job?>(null) }
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
                    originalOrder = listOf(info)
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
                originalOrder = list
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected files · ${selectedFiles.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (selectedFiles.size > 1 && selectedFiles != originalOrder && originalOrder.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clickable { selectedFiles = originalOrder.filter { it in selectedFiles } }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset order",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset order",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                itemsIndexed(selectedFiles) { index, file ->
                    SelectedFileItemRow(
                        fileInfo = file,
                        onRemove = {
                            selectedFiles = selectedFiles.filter { it.path != file.path }
                            originalOrder = originalOrder.filter { it.path != file.path }
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
                            PdfPageSelectorGrid(
                                pdfPath = selectedFiles.first().path,
                                selectedPages = selectedPages,
                                onSelectionChange = { newSelection ->
                                    selectedPages = newSelection
                                    pageInput = newSelection.sorted().joinToString(", ")
                                }
                            )
                        }

                        item {
                            DarkInputField(
                                label = "Or enter page numbers (e.g. 1, 3, 5)",
                                value = pageInput,
                                onValueChange = { inputStr ->
                                    pageInput = inputStr
                                    val parsed = inputStr.split(",")
                                        .mapNotNull { it.trim().toIntOrNull() }
                                        .filter { it > 0 }
                                        .toSet()
                                    if (parsed.isNotEmpty()) {
                                        selectedPages = parsed
                                    }
                                }
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

                        item {
                            PdfPageSelectorGrid(
                                pdfPath = selectedFiles.first().path,
                                selectedPages = selectedPages,
                                onSelectionChange = { newSelection ->
                                    selectedPages = newSelection
                                },
                                previewRotation = rotationDegrees
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
                    "pdf_split" -> {
                        item {
                            Text("Pages per document", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            FormatChipSelector(
                                options = listOf("1 page", "2 pages", "5 pages", "10 pages"),
                                selectedOption = "$splitPagesPerFile page" + if (splitPagesPerFile > 1) "s" else "",
                                onOptionSelected = {
                                    splitPagesPerFile = it.replace(" pages", "").replace(" page", "").trim().toIntOrNull() ?: 1
                                }
                            )
                        }

                        item {
                            PdfPageSelectorGrid(
                                pdfPath = selectedFiles.first().path,
                                selectedPages = emptySet(),
                                onSelectionChange = {}
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
                        processingJob = scope.launch(Dispatchers.IO) {
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
                                    "pdf_split" -> {
                                        val req = PdfSplitRequest(
                                            inputPath = primaryFile.path,
                                            outputDir = context.cacheDir.absolutePath,
                                            pagesPerFile = splitPagesPerFile
                                        )
                                        val splitResults = NativeBridge.split(req)
                                        splitResults.firstOrNull { it.success }
                                            ?: splitResults.firstOrNull()
                                            ?: OperationResult(false, inputPath = primaryFile.path, errorMessage = "Failed to split PDF")
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

                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    onResult(result, tool.title)
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
                message = "Processing PDF document...",
                onCancel = {
                    processingJob?.cancel()
                    isProcessing = false
                }
            )
        }
    }
}
