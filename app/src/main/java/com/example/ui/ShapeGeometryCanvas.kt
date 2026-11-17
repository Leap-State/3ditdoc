package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeometricShapeType
import com.example.model.OverlayItem
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ShapeOverlayNode(
    item: OverlayItem.ShapeItem,
    pageW: Float,
    pageH: Float,
    pxPerPt: Float,
    isSelected: Boolean,
    isMoveAllHighlighted: Boolean,
    onSelect: () -> Unit,
    onBeginGesture: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onScaleFactor: (Float) -> Unit,
    onRotateDelta: (Float) -> Unit,
    onDragEndSnapCheck: () -> Unit
) {
    val density = LocalDensity.current
    val wPx = (item.normWidth * pageW).coerceAtLeast(24f)
    val hPx = (item.normHeight * pageH).coerceAtLeast(24f)
    val leftPx = (item.centerX * pageW - wPx / 2f).roundToInt()
    val topPx = (item.centerY * pageH - hPx / 2f).roundToInt()

    val wDp = with(density) { wPx.toDp() }
    val hDp = with(density) { hPx.toDp() }
    val selectionColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx, topPx) }
            .size(wDp, hDp)
            .graphicsLayer { rotationZ = item.rotationDeg }
            .then(
                when {
                    isSelected -> Modifier
                        .border(1.5.dp, selectionColor, RoundedCornerShape(4.dp))
                        .background(selectionColor.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    isMoveAllHighlighted -> Modifier
                        .border(1.dp, selectionColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    else -> Modifier
                }
            )
            .pointerInput(item.id) {
                detectTapGestures { onSelect() }
            }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = {
                        onBeginGesture()
                        if (!isSelected) onSelect()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEndSnapCheck,
                    onDragCancel = onDragEndSnapCheck
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawShapeGeometry(
                shapeType = item.shapeType,
                topLeft = Offset.Zero,
                boxSize = size,
                color = Color(item.colorArgb).copy(alpha = item.opacity.coerceIn(0.05f, 1f)),
                strokeWidthPx = (item.strokeWidthPt * pxPerPt).coerceAtLeast(2f),
                isFilled = item.isFilled
            )
        }

        if (isSelected) {
            TransformHandlesOverlay(
                selectionColor = selectionColor,
                onBeginGesture = onBeginGesture,
                onDragMoveDelta = onDragDelta,
                onScaleFactor = onScaleFactor,
                onRotateDelta = onRotateDelta,
                onDragEnd = onDragEndSnapCheck
            )
        }
    }
}

fun DrawScope.drawShapeGeometry(
    shapeType: GeometricShapeType,
    topLeft: Offset,
    boxSize: Size,
    color: Color,
    strokeWidthPx: Float,
    isFilled: Boolean
) {
    val w = boxSize.width.coerceAtLeast(4f)
    val h = boxSize.height.coerceAtLeast(4f)
    val left = topLeft.x
    val top = topLeft.y
    val right = left + w
    val bottom = top + h
    val cx = left + w / 2f
    val cy = top + h / 2f

    val strokeStyle = Stroke(
        width = strokeWidthPx,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val drawStyle = if (isFilled) Fill else strokeStyle

    when (shapeType) {
        GeometricShapeType.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = topLeft,
                size = Size(w, h),
                style = drawStyle
            )
        }
        GeometricShapeType.CIRCLE -> {
            drawOval(
                color = color,
                topLeft = topLeft,
                size = Size(w, h),
                style = drawStyle
            )
        }
        GeometricShapeType.TRIANGLE -> {
            val path = Path().apply {
                moveTo(cx, top)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(path = path, color = color, style = drawStyle)
        }
        GeometricShapeType.DIAMOND -> {
            val path = Path().apply {
                moveTo(cx, top)
                lineTo(right, cy)
                lineTo(cx, bottom)
                lineTo(left, cy)
                close()
            }
            drawPath(path = path, color = color, style = drawStyle)
        }
        GeometricShapeType.STAR -> {
            val rx = w / 2f
            val ry = h / 2f
            val path = Path().apply {
                for (i in 0 until 10) {
                    val scale = if (i % 2 == 0) 1f else 0.42f
                    val angleRad = Math.toRadians(-90.0 + i * 36.0)
                    val px = cx + (rx * scale * cos(angleRad)).toFloat()
                    val py = cy + (ry * scale * sin(angleRad)).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(path = path, color = color, style = drawStyle)
        }
        GeometricShapeType.LINE -> {
            drawLine(
                color = color,
                start = Offset(left, cy),
                end = Offset(right, cy),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
        GeometricShapeType.ARROW -> {
            val headLen = min(w * 0.34f, min(w, h) * 0.72f).coerceAtLeast(10f)
            val backX = (right - headLen).coerceAtLeast(left + w * 0.25f)
            if (isFilled) {
                val shaftHalfH = (h * 0.14f).coerceAtLeast(3f)
                val path = Path().apply {
                    moveTo(left, cy - shaftHalfH)
                    lineTo(backX, cy - shaftHalfH)
                    lineTo(backX, top)
                    lineTo(right, cy)
                    lineTo(backX, bottom)
                    lineTo(backX, cy + shaftHalfH)
                    lineTo(left, cy + shaftHalfH)
                    close()
                }
                drawPath(path = path, color = color, style = Fill)
            } else {
                val wingH = (h * 0.39f).coerceAtLeast(8f)
                drawLine(
                    color = color,
                    start = Offset(left, cy),
                    end = Offset(right, cy),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
                val headPath = Path().apply {
                    moveTo(backX, cy - wingH)
                    lineTo(right, cy)
                    lineTo(backX, cy + wingH)
                }
                drawPath(path = headPath, color = color, style = strokeStyle)
            }
        }
    }
}

@Composable
fun BoxScope.TransformHandlesOverlay(
    selectionColor: Color,
    onBeginGesture: () -> Unit,
    onDragMoveDelta: (Float, Float) -> Unit,
    onScaleFactor: (Float) -> Unit,
    onRotateDelta: (Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    // Bottom-left dedicated Move grip (48dp touch area, 28dp crisp pill)
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(x = (-22).dp, y = 22.dp)
            .size(48.dp)
            .testTag("item_move_handle")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onBeginGesture() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragMoveDelta(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenWith,
                contentDescription = stringResource(R.string.editor_move_handle_desc),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // Bottom-right proportional resize handle (48dp touch area)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 22.dp, y = 22.dp)
            .size(48.dp)
            .testTag("item_resize_handle")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onBeginGesture() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.x + dragAmount.y
                        val factor = 1f + (delta / 210f)
                        onScaleFactor(factor.coerceIn(0.86f, 1.14f))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(selectionColor)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInFull,
                contentDescription = stringResource(R.string.editor_resize_item),
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }

    // Top-center rotation handle (48dp touch area)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-34).dp)
            .size(48.dp)
            .testTag("item_rotate_handle")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onBeginGesture() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deg = dragAmount.x * 0.45f
                        onRotateDelta(deg)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.editor_rotate_item),
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
