package com.filetoolkit.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filetoolkit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfPageThumbnail(
    val pageIndex: Int, // 0-indexed
    val pageNumber: Int, // 1-indexed
    val bitmap: Bitmap?
)

@Composable
fun PdfPageSelectorGrid(
    pdfPath: String,
    selectedPages: Set<Int>, // 1-indexed page numbers
    onSelectionChange: (Set<Int>) -> Unit,
    previewRotation: Int = 0,
    modifier: Modifier = Modifier
) {
    var thumbnails by remember(pdfPath) { mutableStateOf<List<PdfPageThumbnail>>(emptyList()) }
    var isLoading by remember(pdfPath) { mutableStateOf(true) }
    var totalPages by remember(pdfPath) { mutableStateOf(0) }

    LaunchedEffect(pdfPath) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            val list = mutableListOf<PdfPageThumbnail>()
            try {
                val file = File(pdfPath)
                if (file.exists()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    totalPages = count

                    for (i in 0 until count) {
                        try {
                            val page = renderer.openPage(i)
                            val targetW = 220
                            val targetH = ((targetW.toFloat() / page.width.toFloat()) * page.height).toInt().coerceAtLeast(100)
                            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(AndroidColor.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            list.add(PdfPageThumbnail(i, i + 1, bitmap))
                        } catch (e: Exception) {
                            list.add(PdfPageThumbnail(i, i + 1, null))
                        }
                    }
                    renderer.close()
                    pfd.close()
                }
            } catch (e: Exception) {
                // Ignore rendering errors, fallback to count if known
            }
            list
        }
        thumbnails = result
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .background(SurfaceCharcoal, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with selection count and bulk actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pages (${selectedPages.size} of ${totalPages.coerceAtLeast(thumbnails.size)} selected)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val all = (1..totalPages.coerceAtLeast(thumbnails.size)).toSet()
                        onSelectionChange(all)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Select All", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = { onSelectionChange(emptySet()) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
            }
        } else if (thumbnails.isEmpty()) {
            Text(
                text = "No pages found in PDF",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            // Render grid in chunks of 3 items per row
            val rows = thumbnails.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            val isSelected = selectedPages.contains(item.pageNumber)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BorderActive else BorderSubtle,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val newSet = if (isSelected) {
                                            selectedPages - item.pageNumber
                                        } else {
                                            selectedPages + item.pageNumber
                                        }
                                        onSelectionChange(newSet)
                                    }
                                    .padding(6.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PureWhite),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.bitmap != null) {
                                            Image(
                                                bitmap = item.bitmap.asImageBitmap(),
                                                contentDescription = "Page ${item.pageNumber}",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .rotate(previewRotation.toFloat()),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text(
                                                text = "${item.pageNumber}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = PureBlack
                                            )
                                        }

                                        // Selection indicator badge in top right
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) PureBlack else BorderMedium,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Page ${item.pageNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Fill empty slots if row has fewer than 3 items
                        val remaining = 3 - rowItems.size
                        for (i in 0 until remaining) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
