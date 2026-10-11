package com.filetoolkit.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filetoolkit.app.data.FileInfo
import com.filetoolkit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PureBlack,
            titleContentColor = TextPrimary
        )
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "button_press_scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionPrimaryBg,
            contentColor = ActionPrimaryText,
            disabledContainerColor = SurfaceRaised,
            disabledContentColor = TextSecondary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) ActionPrimaryText else TextSecondary
        )
    }
}

@Composable
fun DashedFilePickerBox(
    title: String = "Add files",
    icon: ImageVector = Icons.Default.Add,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCharcoal)
            .drawBehind {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                )
                drawRoundRect(
                    color = BorderMedium,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun SelectedFileItemRow(
    fileInfo: FileInfo,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
            Icon(
                imageVector = if (fileInfo.format == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileInfo.filename,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaDetails = buildString {
                    if (fileInfo.pageCount != null) {
                        append("${fileInfo.pageCount} pages · ")
                    } else if (fileInfo.width != null && fileInfo.height != null) {
                        append("${fileInfo.width}×${fileInfo.height} · ")
                    }
                    append(formatFileSize(fileInfo.sizeBytes))
                }
                Text(
                    text = metaDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Reorder controls (Section 3 of d.md)
            if (onMoveUp != null) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onMoveDown != null) {
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImagePreviewCard(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 2 // sample down to keep preview lightweight and bounded
                    }
                    bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }
    }

    if (bitmap != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceInput),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Live preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun DarkInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceInput,
                unfocusedContainerColor = SurfaceInput,
                focusedBorderColor = BorderMedium,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
    }
}

@Composable
fun FormatChipSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val isSelected = opt.equals(selectedOption, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) SurfaceRaised else SurfaceCharcoal)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) BorderActive else BorderSubtle,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOptionSelected(opt) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = opt.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun ProcessingModal(
    message: String = "Processing...",
    onCancel: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = StateError)
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = SurfaceCharcoal,
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                CircularProgressIndicator(
                    color = TextPrimary,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    )
}
