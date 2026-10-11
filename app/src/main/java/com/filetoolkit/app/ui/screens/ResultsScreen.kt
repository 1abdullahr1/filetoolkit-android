package com.filetoolkit.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.filetoolkit.app.data.OperationResult
import com.filetoolkit.app.ui.components.AppHeader
import com.filetoolkit.app.ui.components.PrimaryActionButton
import com.filetoolkit.app.ui.components.formatFileSize
import com.filetoolkit.app.ui.theme.*
import java.io.File
import java.io.FileInputStream

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
                Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
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
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error", Toast.LENGTH_SHORT).show()
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
        containerColor = PureBlack,
        topBar = {
            AppHeader(
                title = toolTitle,
                canNavigateBack = true,
                onNavigateBack = onNavigateHome
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
            // Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal)
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
                            tint = if (result.success) TextPrimary else StateError,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (result.success) "Operation completed" else "Operation failed",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!result.success) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = result.errorMessage ?: "Unknown error occurred",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StateError
                            )
                        }
                    }
                }
            }

            if (result.success && outputFile != null) {
                // File info card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("File", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text(outputFile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Output size", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text(formatFileSize(result.outputSizeBytes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            if (result.inputSizeBytes > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Input size", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(formatFileSize(result.inputSizeBytes), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Action buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            text = "Save to device",
                            onClick = { saveFileLauncher.launch(outputFile.name) }
                        )

                        OutlinedButton(
                            onClick = { shareFile() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderMedium),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Share file", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = { openFile() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderMedium),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Open in external viewer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = onNavigateHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Return to tools", color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
