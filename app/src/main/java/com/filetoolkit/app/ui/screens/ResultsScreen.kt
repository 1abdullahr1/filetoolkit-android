package com.filetoolkit.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.filetoolkit.app.data.OperationResult
import com.filetoolkit.app.ui.components.ToolkitTopBar
import com.filetoolkit.app.ui.components.formatFileSize
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    result: OperationResult,
    toolTitle: String,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val outputFile = result.outputPath?.let { File(it) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            outputFile?.let {
                if (it.extension.equals("pdf", true)) "application/pdf"
                else if (it.extension.equals("png", true)) "image/png"
                else if (it.extension.equals("webp", true)) "image/webp"
                else "image/jpeg"
            } ?: "*/*"
        )
    ) { destUri ->
        if (destUri != null && outputFile != null && outputFile.exists()) {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                    FileInputStream(outputFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareFile() {
        if (outputFile == null || !outputFile.exists()) return
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (outputFile.extension.equals("pdf", true)) "application/pdf" else "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share generated file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile() {
        if (outputFile == null || !outputFile.exists()) return
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    contentUri,
                    if (outputFile.extension.equals("pdf", true)) "application/pdf" else "image/*"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            ToolkitTopBar(
                title = "Result: $toolTitle",
                canNavigateBack = true,
                onNavigateBack = onNavigateHome
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (result.success) "Operation Completed" else "Operation Failed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!result.success) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.errorMessage ?: "Unknown error occurred during processing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (result.success && outputFile != null) {
                // File summary card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "File Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Generated file:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(outputFile.name, fontWeight = FontWeight.Medium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Output file size:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatFileSize(result.outputSizeBytes), fontWeight = FontWeight.Bold)
                            }

                            if (result.inputSizeBytes > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Input file size:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatFileSize(result.inputSizeBytes))
                                }

                                val diff = result.outputSizeBytes - result.inputSizeBytes
                                val pct = (diff.toDouble() / result.inputSizeBytes.toDouble() * 100).toInt()
                                val diffLabel = if (diff <= 0) "$pct% (Compressed)" else "+$pct%"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Size change:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(diffLabel, fontWeight = FontWeight.Bold, color = if (diff <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // Action buttons
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                saveFileLauncher.launch(outputFile.name)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Device (SAF)")
                        }

                        OutlinedButton(
                            onClick = { shareFile() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share File")
                        }

                        OutlinedButton(
                            onClick = { openFile() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in External Viewer")
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = onNavigateHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Return to Tools")
                }
            }
        }
    }
}
