package com.filetoolkit.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filetoolkit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class CropCoordinates(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

enum class DragHandle {
    NONE,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Composable
fun InteractiveImageCropper(
    imagePath: String,
    onCropChange: (CropCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    var origWidth by remember(imagePath) { mutableStateOf(1) }
    var origHeight by remember(imagePath) { mutableStateOf(1) }

    // Normalized coordinates [0f, 1f]
    var normLeft by remember { mutableFloatStateOf(0.08f) }
    var normTop by remember { mutableFloatStateOf(0.08f) }
    var normRight by remember { mutableFloatStateOf(0.92f) }
    var normBottom by remember { mutableFloatStateOf(0.92f) }

    var selectedAspect by remember { mutableStateOf("Free") }

    LaunchedEffect(imagePath) {
        val bmp = withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    origWidth = opts.outWidth.coerceAtLeast(1)
                    origHeight = opts.outHeight.coerceAtLeast(1)

                    val sampleOpts = BitmapFactory.Options().apply {
                        inSampleSize = (opts.outWidth / 800).coerceAtLeast(1)
                    }
                    BitmapFactory.decodeFile(file.absolutePath, sampleOpts)
                } else null
            } catch (e: Exception) {
                null
            }
        }
        bitmap = bmp
    }

    // Sync crop changes to parent
    LaunchedEffect(normLeft, normTop, normRight, normBottom, origWidth, origHeight) {
        val px = (normLeft * origWidth).roundToInt().coerceAtLeast(0)
        val py = (normTop * origHeight).roundToInt().coerceAtLeast(0)
        val pw = ((normRight - normLeft) * origWidth).roundToInt().coerceAtLeast(1)
        val ph = ((normBottom - normTop) * origHeight).roundToInt().coerceAtLeast(1)
        onCropChange(CropCoordinates(x = px, y = py, width = pw, height = ph))
    }

    fun applyAspectRatio(aspect: String) {
        selectedAspect = aspect
        val curCenterX = (normLeft + normRight) / 2f
        val curCenterY = (normTop + normBottom) / 2f

        val ratio = when (aspect) {
            "1:1" -> 1.0f
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            else -> return // Freeform keeps current
        }

        // Target aspect in pixels
        val targetImageRatio = (origWidth.toFloat() / origHeight.toFloat())
        // Adjust normalized width/height according to image aspect ratio
        val normRatio = ratio / targetImageRatio

        var newW = (normRight - normLeft)
        var newH = newW / normRatio

        if (newH > 0.9f) {
            newH = 0.9f
            newW = newH * normRatio
        }
        if (newW > 0.9f) {
            newW = 0.9f
            newH = newW / normRatio
        }

        normLeft = (curCenterX - newW / 2f).coerceIn(0.02f, 0.98f - newW)
        normRight = normLeft + newW
        normTop = (curCenterY - newH / 2f).coerceIn(0.02f, 0.98f - newH)
        normBottom = normTop + newH
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .background(SurfaceCharcoal, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Manual Crop (Drag with fingers)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            val pxW = ((normRight - normLeft) * origWidth).roundToInt().coerceAtLeast(1)
            val pxH = ((normBottom - normTop) * origHeight).roundToInt().coerceAtLeast(1)
            Text(
                text = "$pxW × $pxH px",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Aspect ratio chip presets
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Free", "1:1", "4:3", "16:9").forEach { mode ->
                val isSelected = selectedAspect == mode
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (isSelected) BorderActive else BorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .background(if (isSelected) SurfaceRaised else Color.Transparent, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { applyAspectRatio(mode) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Direct Touch Manipulation Cropping Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PureBlack),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth.value
            val containerHeight = maxHeight.value

            var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Crop Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(containerWidth, containerHeight) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()

                                val rectLeftPx = normLeft * w
                                val rectTopPx = normTop * h
                                val rectRightPx = normRight * w
                                val rectBottomPx = normBottom * h

                                val threshold = 40f // 40px touch radius for corners

                                val distTL = (offset - Offset(rectLeftPx, rectTopPx)).getDistance()
                                val distTR = (offset - Offset(rectRightPx, rectTopPx)).getDistance()
                                val distBL = (offset - Offset(rectLeftPx, rectBottomPx)).getDistance()
                                val distBR = (offset - Offset(rectRightPx, rectBottomPx)).getDistance()

                                activeHandle = when {
                                    distTL <= threshold -> DragHandle.TOP_LEFT
                                    distTR <= threshold -> DragHandle.TOP_RIGHT
                                    distBL <= threshold -> DragHandle.BOTTOM_LEFT
                                    distBR <= threshold -> DragHandle.BOTTOM_RIGHT
                                    offset.x in rectLeftPx..rectRightPx && offset.y in rectTopPx..rectBottomPx -> DragHandle.MOVE
                                    else -> DragHandle.NONE
                                }
                            },
                            onDragEnd = { activeHandle = DragHandle.NONE },
                            onDragCancel = { activeHandle = DragHandle.NONE },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val dNormX = dragAmount.x / w
                                val dNormY = dragAmount.y / h

                                val minSize = 0.08f

                                when (activeHandle) {
                                    DragHandle.MOVE -> {
                                        val boxW = normRight - normLeft
                                        val boxH = normBottom - normTop
                                        val nextL = (normLeft + dNormX).coerceIn(0f, 1f - boxW)
                                        val nextT = (normTop + dNormY).coerceIn(0f, 1f - boxH)
                                        normLeft = nextL
                                        normRight = nextL + boxW
                                        normTop = nextT
                                        normBottom = nextT + boxH
                                    }
                                    DragHandle.TOP_LEFT -> {
                                        normLeft = (normLeft + dNormX).coerceIn(0f, normRight - minSize)
                                        normTop = (normTop + dNormY).coerceIn(0f, normBottom - minSize)
                                    }
                                    DragHandle.TOP_RIGHT -> {
                                        normRight = (normRight + dNormX).coerceIn(normLeft + minSize, 1f)
                                        normTop = (normTop + dNormY).coerceIn(0f, normBottom - minSize)
                                    }
                                    DragHandle.BOTTOM_LEFT -> {
                                        normLeft = (normLeft + dNormX).coerceIn(0f, normRight - minSize)
                                        normBottom = (normBottom + dNormY).coerceIn(normTop + minSize, 1f)
                                    }
                                    DragHandle.BOTTOM_RIGHT -> {
                                        normRight = (normRight + dNormX).coerceIn(normLeft + minSize, 1f)
                                        normBottom = (normBottom + dNormY).coerceIn(normTop + minSize, 1f)
                                    }
                                    DragHandle.NONE -> {}
                                }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                val rectLeftPx = normLeft * w
                val rectTopPx = normTop * h
                val rectRightPx = normRight * w
                val rectBottomPx = normBottom * h
                val rectWPx = rectRightPx - rectLeftPx
                val rectHPx = rectBottomPx - rectTopPx

                // 1. Dim scrim outside crop box
                // Top
                drawRect(color = Color(0x99000000), topLeft = Offset(0f, 0f), size = Size(w, rectTopPx))
                // Bottom
                drawRect(color = Color(0x99000000), topLeft = Offset(0f, rectBottomPx), size = Size(w, h - rectBottomPx))
                // Left
                drawRect(color = Color(0x99000000), topLeft = Offset(0f, rectTopPx), size = Size(rectLeftPx, rectHPx))
                // Right
                drawRect(color = Color(0x99000000), topLeft = Offset(rectRightPx, rectTopPx), size = Size(w - rectRightPx, rectHPx))

                // 2. White crop rectangle border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(rectLeftPx, rectTopPx),
                    size = Size(rectWPx, rectHPx),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Subtle Rule of Thirds grid
                val oneThirdW = rectWPx / 3f
                val oneThirdH = rectHPx / 3f
                val gridColor = Color(0x66FFFFFF)
                val gridStroke = Stroke(width = 1.dp.toPx())

                drawLine(gridColor, Offset(rectLeftPx + oneThirdW, rectTopPx), Offset(rectLeftPx + oneThirdW, rectBottomPx), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(rectLeftPx + oneThirdW * 2, rectTopPx), Offset(rectLeftPx + oneThirdW * 2, rectBottomPx), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(rectLeftPx, rectTopPx + oneThirdH), Offset(rectRightPx, rectTopPx + oneThirdH), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(rectLeftPx, rectTopPx + oneThirdH * 2), Offset(rectRightPx, rectTopPx + oneThirdH * 2), strokeWidth = gridStroke.width)

                // 4. White Corner grab handles
                val handleLen = 18.dp.toPx()
                val handleThickness = 3.5.dp.toPx()
                val handleColor = Color.White

                // Top-Left corner
                drawLine(handleColor, Offset(rectLeftPx - 2, rectTopPx), Offset(rectLeftPx + handleLen, rectTopPx), strokeWidth = handleThickness)
                drawLine(handleColor, Offset(rectLeftPx, rectTopPx - 2), Offset(rectLeftPx, rectTopPx + handleLen), strokeWidth = handleThickness)

                // Top-Right corner
                drawLine(handleColor, Offset(rectRightPx + 2, rectTopPx), Offset(rectRightPx - handleLen, rectTopPx), strokeWidth = handleThickness)
                drawLine(handleColor, Offset(rectRightPx, rectTopPx - 2), Offset(rectRightPx, rectTopPx + handleLen), strokeWidth = handleThickness)

                // Bottom-Left corner
                drawLine(handleColor, Offset(rectLeftPx - 2, rectBottomPx), Offset(rectLeftPx + handleLen, rectBottomPx), strokeWidth = handleThickness)
                drawLine(handleColor, Offset(rectLeftPx, rectBottomPx + 2), Offset(rectLeftPx, rectBottomPx - handleLen), strokeWidth = handleThickness)

                // Bottom-Right corner
                drawLine(handleColor, Offset(rectRightPx + 2, rectBottomPx), Offset(rectRightPx - handleLen, rectBottomPx), strokeWidth = handleThickness)
                drawLine(handleColor, Offset(rectRightPx, rectBottomPx + 2), Offset(rectRightPx, rectBottomPx - handleLen), strokeWidth = handleThickness)
            }
        }
    }
}
