package com.filetoolkit.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filetoolkit.app.data.CacheManager
import com.filetoolkit.app.ui.components.formatFileSize
import com.filetoolkit.app.ui.theme.*

@Composable
fun SettingsScreen(
    onClearHistory: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    val context = LocalContext.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var defaultQuality by remember { mutableIntStateOf(85) }

    LaunchedEffect(Unit) {
        cacheSizeBytes = CacheManager.getCacheSizeBytes(context)
    }

    Scaffold(
        containerColor = PureBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Appearance
            item {
                SettingsSectionHeader("Appearance")
                SettingsRow(title = "Theme", value = "Dark") {
                    Toast.makeText(context, "Dark (Pure Charcoal utility theme)", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Processing
            item {
                SettingsSectionHeader("Processing")
                SettingsRow(title = "Default image quality", value = "$defaultQuality%") {
                    showQualityDialog = true
                }
                SettingsRow(title = "Supported formats", value = "12 formats", onClick = onOpenLicenses)
                SettingsRow(title = "Output location", value = "App Cache / SAF") {
                    Toast.makeText(context, "Storage Access Framework (SAF)", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Storage & Files
            item {
                SettingsSectionHeader("Storage & Cache")
                SettingsRow(
                    title = "Temporary cache",
                    value = formatFileSize(cacheSizeBytes),
                    onClick = { showClearCacheDialog = true }
                )
                SettingsRow(title = "Auto-cleanup", value = "Every 24 hours") {
                    Toast.makeText(context, "Files older than 24 hours are automatically removed", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Privacy
            item {
                SettingsSectionHeader("Privacy & Data")
                SettingsRow(title = "Offline processing", value = "100% on-device") {
                    Toast.makeText(context, "All processing occurs locally with Rust native binaries", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Clear conversion history") {
                    showClearHistoryDialog = true
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // About
            item {
                SettingsSectionHeader("About")
                SettingsRow(title = "Version", value = "1.0.0") {
                    Toast.makeText(context, "FileKit v1.0.0 (Rust Core 1.0)", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Format matrix", onClick = onOpenLicenses)
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Clear History Dialog
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                containerColor = SurfaceCharcoal,
                title = { Text("Clear History?", color = TextPrimary) },
                text = { Text("This will delete the conversion history log. Generated files on device are not deleted.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear", color = StateError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // Clear Cache Dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                containerColor = SurfaceCharcoal,
                title = { Text("Clear Temporary Cache?", color = TextPrimary) },
                text = {
                    Text(
                        "This will delete temporary output files (${formatFileSize(cacheSizeBytes)}) created during processing.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        CacheManager.clearAllCache(context)
                        cacheSizeBytes = CacheManager.getCacheSizeBytes(context)
                        showClearCacheDialog = false
                        Toast.makeText(context, "Temporary cache cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear", color = StateError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // Quality Dialog
        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                containerColor = SurfaceCharcoal,
                title = { Text("Default Image Quality", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(75, 85, 95).forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = defaultQuality == q,
                                        onClick = {
                                            defaultQuality = q
                                            showQualityDialog = false
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultQuality == q,
                                    onClick = {
                                        defaultQuality = q
                                        showQualityDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = PureWhite,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$q% ${if (q == 85) "(Balanced)" else if (q == 75) "(High Compression)" else "(Maximum Quality)"}",
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text("Close", color = TextPrimary)
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            fontSize = 16.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
