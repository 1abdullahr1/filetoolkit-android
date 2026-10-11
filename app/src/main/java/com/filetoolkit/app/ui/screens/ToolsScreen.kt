package com.filetoolkit.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.filetoolkit.app.data.ToolCategory
import com.filetoolkit.app.data.ToolDefinition
import com.filetoolkit.app.data.ToolRepository
import com.filetoolkit.app.ui.theme.*

@Composable
fun ToolsScreen(
    initialCategory: ToolCategory? = null,
    onSelectTool: (ToolDefinition) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(searchQuery) {
        if (searchQuery.isNotBlank()) {
            ToolRepository.searchTools(searchQuery)
        } else {
            ToolRepository.allTools
        }
    }

    val imageTools = remember(filteredTools) { filteredTools.filter { it.category == ToolCategory.IMAGES } }
    val pdfTools = remember(filteredTools) { filteredTools.filter { it.category == ToolCategory.PDF } }
    val textTools = remember(filteredTools) { filteredTools.filter { it.category == ToolCategory.TEXT } }

    Scaffold(
        containerColor = PureBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Search Bar matching screenshot 5
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tools", color = TextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCharcoal,
                        unfocusedContainerColor = SurfaceCharcoal,
                        focusedBorderColor = BorderMedium,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Images Category
            if (imageTools.isNotEmpty()) {
                item {
                    Text(
                        text = "Images",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    ToolTwoColumnGrid(tools = imageTools, onSelectTool = onSelectTool)
                }
            }

            // PDF Category
            if (pdfTools.isNotEmpty()) {
                item {
                    Text(
                        text = "PDF",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    ToolTwoColumnGrid(tools = pdfTools, onSelectTool = onSelectTool)
                }
            }

            // Text Category
            if (textTools.isNotEmpty()) {
                item {
                    Text(
                        text = "Text",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    ToolTwoColumnGrid(tools = textTools, onSelectTool = onSelectTool)
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ToolTwoColumnGrid(
    tools: List<ToolDefinition>,
    onSelectTool: (ToolDefinition) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolGridButton(
                    tool = pair[0],
                    onClick = { onSelectTool(pair[0]) },
                    modifier = Modifier.weight(1f)
                )
                if (pair.size > 1) {
                    ToolGridButton(
                        tool = pair[1],
                        onClick = { onSelectTool(pair[1]) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ToolGridButton(
    tool: ToolDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(58.dp)
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
    }
}
