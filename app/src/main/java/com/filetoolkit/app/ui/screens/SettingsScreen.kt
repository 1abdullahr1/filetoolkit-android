package com.filetoolkit.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.filetoolkit.app.ui.theme.*

@Composable
fun SettingsScreen(
    onClearHistory: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

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
                SettingsRow(title = "Theme") {
                    Toast.makeText(context, "Dark (System default)", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Processing
            item {
                SettingsSectionHeader("Processing")
                SettingsRow(title = "Default image quality") {
                    Toast.makeText(context, "Default: 85%", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Default output format") {
                    Toast.makeText(context, "JPEG / PDF", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Output location") {
                    Toast.makeText(context, "Storage Access Framework (SAF)", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Files
            item {
                SettingsSectionHeader("Files")
                SettingsRow(title = "Recent history") {
                    Toast.makeText(context, "Local JSON persistence", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Temporary files") {
                    Toast.makeText(context, "Managed app cache", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // Privacy
            item {
                SettingsSectionHeader("Privacy")
                SettingsRow(title = "Local processing") {
                    Toast.makeText(context, "100% Offline Rust Native Core", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Clear history") {
                    showClearDialog = true
                }
                SettingsRow(title = "Permissions") {
                    Toast.makeText(context, "No broad storage permissions required", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider(color = BorderSubtle, thickness = 1.dp) }

            // About
            item {
                SettingsSectionHeader("About")
                SettingsRow(title = "Version") {
                    Toast.makeText(context, "FileKit v1.0.0 (Rust Core 1.0)", Toast.LENGTH_SHORT).show()
                }
                SettingsRow(title = "Open-source licences", onClick = onOpenLicenses)
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = SurfaceCharcoal,
                title = { Text("Clear History?", color = TextPrimary) },
                text = { Text("This will delete the conversion history log. Generated files are not deleted.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        onClearHistory()
                        showClearDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear", color = StateError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel", color = TextSecondary)
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
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
