package com.filetoolkit.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.filetoolkit.app.data.HistoryItem
import com.filetoolkit.app.ui.components.formatFileSize
import com.filetoolkit.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentScreen(
    historyItems: List<HistoryItem>,
    onClearHistory: () -> Unit,
    onNavigateToTools: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("All") }
    val dateFormat = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.getDefault()) }

    val filteredItems = remember(historyItems, selectedFilter) {
        when (selectedFilter) {
            "Completed" -> historyItems.filter { it.isSuccess }
            "Failed" -> historyItems.filter { !it.isSuccess }
            else -> historyItems
        }
    }

    Scaffold(
        containerColor = PureBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (historyItems.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // Filter Tabs (All / Completed / Failed)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Completed", "Failed").forEach { tab ->
                        val isSelected = selectedFilter == tab
                        Box(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (isSelected) BorderActive else BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = tab }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.HistoryToggleOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No recent operations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToTools,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PureWhite,
                                    contentColor = PureBlack
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Explore Tools", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.outputFileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${item.toolTitle} · ${dateFormat.format(Date(item.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatFileSize(item.inputSize)} → ${formatFileSize(item.outputSize)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.isSuccess) StateSuccess else StateError
                                )
                            }

                            if (item.isSuccess) {
                                IconButton(onClick = {
                                    val file = File(item.outputFilePath)
                                    if (file.exists()) {
                                        try {
                                            val uri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, if (file.extension == "pdf") "application/pdf" else "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "File was removed from cache", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Open",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
