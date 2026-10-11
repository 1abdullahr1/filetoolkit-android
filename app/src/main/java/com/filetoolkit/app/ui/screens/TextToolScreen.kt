package com.filetoolkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.filetoolkit.app.ui.components.ProcessingDialog
import com.filetoolkit.app.ui.components.ToolkitTopBar
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolScreen(
    tool: ToolDefinition,
    onNavigateBack: () -> Unit,
    onResult: (OperationResult, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var textInput by remember { mutableStateOf("") }
    var documentTitle by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(11f) }
    var pageSize by remember { mutableStateOf("A4") }
    var isImageTarget by remember { mutableStateOf(tool.id == "text_to_image") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            // Text Input Box
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
                            text = "Text Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (!isImageTarget) {
                            OutlinedTextField(
                                value = documentTitle,
                                onValueChange = { documentTitle = it },
                                label = { Text("Document Title (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Type or paste your text here...\nLong text automatically paginates into multiple pages.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 360.dp),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${textInput.length} characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${textInput.lines().size} lines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Formatting Options Card
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
                            text = "Layout & Typography",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Font size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = fontSize == 9f,
                                    onClick = { fontSize = 9f },
                                    label = { Text("Small") }
                                )
                                FilterChip(
                                    selected = fontSize == 11f,
                                    onClick = { fontSize = 11f },
                                    label = { Text("Normal") }
                                )
                                FilterChip(
                                    selected = fontSize == 14f,
                                    onClick = { fontSize = 14f },
                                    label = { Text("Large") }
                                )
                            }
                        }

                        if (!isImageTarget) {
                            // Page size selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Page Standard", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = pageSize == "A4",
                                        onClick = { pageSize = "A4" },
                                        label = { Text("A4 (ISO)") }
                                    )
                                    FilterChip(
                                        selected = pageSize == "LETTER",
                                        onClick = { pageSize = "LETTER" },
                                        label = { Text("US Letter") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export Button
            item {
                Button(
                    onClick = {
                        if (textInput.trim().isEmpty()) {
                            errorMessage = "Please enter some text before exporting"
                            return@Button
                        }

                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val result = if (isImageTarget) {
                                    val outFile = File(context.cacheDir, "text_img_${System.currentTimeMillis()}.png")
                                    val req = TextToImageRequest(
                                        text = textInput,
                                        outputPath = outFile.absolutePath,
                                        targetFormat = "png",
                                        fontSize = (fontSize * 1.5f).toInt()
                                    )
                                    NativeBridge.exportTextToImage(req)
                                } else {
                                    val outFile = File(context.cacheDir, "text_doc_${System.currentTimeMillis()}.pdf")
                                    val req = TextToPdfRequest(
                                        text = textInput,
                                        outputPath = outFile.absolutePath,
                                        title = if (documentTitle.isNotBlank()) documentTitle else null,
                                        fontSize = fontSize,
                                        pageSize = pageSize
                                    )
                                    NativeBridge.exportTextToPdf(req)
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
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isImageTarget) "Export to Image" else "Generate Paginated PDF",
                        style = MaterialTheme.typography.titleMedium
                    )
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
            ProcessingDialog(message = "Formatting text & generating PDF pages...")
        }
    }
}
