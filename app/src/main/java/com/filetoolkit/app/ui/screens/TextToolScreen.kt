package com.filetoolkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filetoolkit.app.data.OperationResult
import com.filetoolkit.app.data.TextToImageRequest
import com.filetoolkit.app.data.TextToPdfRequest
import com.filetoolkit.app.data.ToolDefinition
import com.filetoolkit.app.data.NativeBridge
import com.filetoolkit.app.ui.components.*
import com.filetoolkit.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

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
    var selectedPageSize by remember { mutableStateOf("A4") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            if (tool.id == "text_to_pdf") {
                item {
                    DarkInputField(
                        label = "Document title (optional)",
                        value = documentTitle,
                        onValueChange = { documentTitle = it }
                    )
                }
            }

            item {
                Text(
                    text = "Text content",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter or paste text here...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceInput,
                        unfocusedContainerColor = SurfaceInput,
                        focusedBorderColor = BorderMedium,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            if (tool.id == "text_to_pdf") {
                item {
                    Text("Page standard", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FormatChipSelector(
                        options = listOf("A4", "LETTER"),
                        selectedOption = selectedPageSize,
                        onOptionSelected = { selectedPageSize = it }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = if (tool.id == "text_to_image") "Export image" else "Generate PDF",
                    enabled = textInput.isNotBlank(),
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val result = if (tool.id == "text_to_image") {
                                    val outFile = File(context.cacheDir, "text_${System.currentTimeMillis()}.png")
                                    val req = TextToImageRequest(
                                        text = textInput,
                                        outputPath = outFile.absolutePath,
                                        targetFormat = "png"
                                    )
                                    NativeBridge.exportTextToImage(req)
                                } else {
                                    val outFile = File(context.cacheDir, "text_${System.currentTimeMillis()}.pdf")
                                    val req = TextToPdfRequest(
                                        text = textInput,
                                        outputPath = outFile.absolutePath,
                                        title = if (documentTitle.isNotBlank()) documentTitle else null,
                                        pageSize = selectedPageSize
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
                    }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        if (isProcessing) {
            ProcessingModal(message = "Formatting and exporting...")
        }
    }
}
