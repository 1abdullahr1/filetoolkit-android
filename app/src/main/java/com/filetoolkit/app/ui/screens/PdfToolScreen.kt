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
            // Picker Card
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
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (tool.acceptsMultiple) "Select Multiple PDF Files" else "Select PDF File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (tool.acceptsMultiple) {
                                    multiplePickerLauncher.launch("application/pdf")
                                } else {
                                    singlePickerLauncher.launch("application/pdf")
                                }
                            }
                        ) {
                            Text(if (selectedFiles.isEmpty()) "Browse PDF" else "Change File")
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

                // Tool-specific options
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Operation Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            when (tool.id) {
                                "pdf_extract", "pdf_delete_pages" -> {
                                    OutlinedTextField(
                                        value = pageInput,
                                        onValueChange = { pageInput = it },
                                        label = { Text("Page numbers (comma separated, e.g. 1, 3, 5)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "pdf_rotate" -> {
                                    Column {
                                        Text("Rotation Angle", style = MaterialTheme.typography.labelLarge)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(90, 180, 270).forEach { deg ->
                                                FilterChip(
                                                    selected = rotationDegrees == deg,
                                                    onClick = { rotationDegrees = deg },
                                                    label = { Text("$deg°") }
                                                )
                                            }
                                        }
                                    }
                                }
                                "pdf_watermark" -> {
                                    OutlinedTextField(
                                        value = watermarkText,
                                        onValueChange = { watermarkText = it },
                                        label = { Text("Watermark text") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "pdf_page_numbers" -> {
                                    OutlinedTextField(
                                        value = pageNumFormat,
                                        onValueChange = { pageNumFormat = it },
                                        label = { Text("Footer format") },
                                        placeholder = { Text("Page {n} of {total}") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "pdf_merge" -> {
                                    Text(
                                        text = "Files will be merged in the order listed above.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                "pdf_compress" -> {
                                    Text(
                                        text = "Streams will be re-encoded with Flate compression and orphaned objects pruned.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Process Button
                item {
                    Button(
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
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Process Document", style = MaterialTheme.typography.titleMedium)
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
            ProcessingDialog(message = "Executing PDF manipulation via Rust lopdf...")
        }
    }
}
