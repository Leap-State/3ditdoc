package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DrawMode
import com.example.model.EditorTool
import com.example.model.GeometricShapeType
import com.example.model.NormPoint
import com.example.model.OverlayItem
import com.example.model.PdfPageMeta
import com.example.ui.theme.PdfCanvasBackdrop
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PdfPageInteractiveCanvas(
    pageBitmap: Bitmap?,
    pageMeta: PdfPageMeta?,
    items: List<OverlayItem>,
    selectedItemId: String?,
    activeTool: EditorTool,
    drawMode: DrawMode,
    activeShapeType: GeometricShapeType,
    shapeFilled: Boolean,
    shapeOpacity: Float,
    isLayerVisible: Boolean,
    isMoveAllMode: Boolean,
    drawColor: Color,
    drawStrokeWidthPt: Float,
    drawOpacity: Float,
    activeStrokes: MutableList<List<NormPoint>>,
    onSelectItem: (String?) -> Unit,
    onEditTextItem: (OverlayItem.TextItem) -> Unit,
    onTapToPlaceText: (normX: Float, normY: Float) -> Unit,
    onCommitShape: (GeometricShapeType, centerX: Float, centerY: Float, normWidth: Float, normHeight: Float) -> Unit,
    onBeginGestureUndo: () -> Unit,
    onMoveItem: (id: String, dxNorm: Float, dyNorm: Float) -> Unit,
    onMoveAllItems: (dxNorm: Float, dyNorm: Float) -> Unit,
    onScaleItem: (id: String, scaleFactor: Float) -> Unit,
    onRotateItem: (id: String, deltaDeg: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember(pageMeta?.index) { mutableFloatStateOf(1f) }
    var panOffset by remember(pageMeta?.index) { mutableStateOf(Offset.Zero) }
    val currentLiveStroke = remember { mutableStateListOf<NormPoint>() }

    var liveShapeStart by remember { mutableStateOf<NormPoint?>(null) }
    var liveShapeCurrent by remember { mutableStateOf<NormPoint?>(null) }
    var isActivelyDraggingItem by remember { mutableStateOf(false) }

    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val showVertCenterGuide = isActivelyDraggingItem && selectedItem != null && abs(selectedItem.centerX - 0.5f) < 0.016f
    val showHorizCenterGuide = isActivelyDraggingItem && selectedItem != null && abs(selectedItem.centerY - 0.5f) < 0.016f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PdfCanvasBackdrop)
            .pointerInput(activeTool, pageMeta?.index, isMoveAllMode) {
                if (activeTool == EditorTool.DRAW || isMoveAllMode) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2 || (pointerCount == 1 && zoom > 1.03f && selectedItemId == null)) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            zoom = newZoom
                            if (newZoom <= 1.01f) {
                                panOffset = Offset.Zero
                            } else {
                                val maxPanX = size.width * (newZoom - 1f) * 0.55f
                                val maxPanY = size.height * (newZoom - 1f) * 0.55f
                                panOffset = Offset(
                                    x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                    y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                )
                            }
                            if (pointerCount >= 2) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val availW = with(density) { (maxWidth - 24.dp).toPx().coerceAtLeast(100f) }
        val availH = with(density) { (maxHeight - 24.dp).toPx().coerceAtLeast(100f) }

        val aspect = if (pageMeta != null && pageMeta.heightPt > 0f) {
            pageMeta.widthPt / pageMeta.heightPt
        } else 0.707f

        val pageW: Float
        val pageH: Float
        if (availW / availH > aspect) {
            pageH = availH
            pageW = availH * aspect
        } else {
            pageW = availW
            pageH = availW / aspect
        }

        val pageWidthDp = with(density) { pageW.toDp() }
        val pageHeightDp = with(density) { pageH.toDp() }
        val pxPerPt = if (pageMeta != null && pageMeta.widthPt > 0f) pageW / pageMeta.widthPt else 1f

        Box(
            modifier = Modifier
                .size(pageWidthDp, pageHeightDp)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
                .shadow(12.dp, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(activeTool, pageW, pageH, isMoveAllMode) {
                    if (activeTool == EditorTool.DRAW || isMoveAllMode) return@pointerInput
                    detectTapGestures { tapOffset ->
                        val nx = (tapOffset.x / pageW).coerceIn(0.02f, 0.90f)
                        val ny = (tapOffset.y / pageH).coerceIn(0.02f, 0.92f)
                        if (activeTool == EditorTool.TEXT) {
                            onTapToPlaceText(nx, ny)
                        } else {
                            onSelectItem(null)
                        }
                    }
                }
                .then(
                    if (isMoveAllMode) {
                        Modifier.pointerInput(pageW, pageH) {
                            detectDragGestures(
                                onDragStart = { onBeginGestureUndo() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onMoveAllItems(dragAmount.x / pageW, dragAmount.y / pageH)
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            // Base untouched PDF page bitmap
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.editor_pdf_page_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Smart Center Snap Alignment Guides when dragging
            if (showVertCenterGuide || showHorizCenterGuide) {
                val guideColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    if (showVertCenterGuide) {
                        drawLine(
                            color = guideColor,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 2f,
                            pathEffect = dash
                        )
                    }
                    if (showHorizCenterGuide) {
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2f,
                            pathEffect = dash
                        )
                    }
                }
            }

            // Independent Overlay Layer
            if (isLayerVisible) {
                items.forEach { item ->
                    val isSelected = item.id == selectedItemId && activeTool != EditorTool.DRAW && !isMoveAllMode
                    when (item) {
                        is OverlayItem.TextItem -> {
                            TextOverlayNode(
                                item = item,
                                pageW = pageW,
                                pageH = pageH,
                                pxPerPt = pxPerPt,
                                isSelected = isSelected,
                                isMoveAllHighlighted = isMoveAllMode,
                                onSelect = {
                                    if (!isMoveAllMode) {
                                        if (isSelected) onEditTextItem(item) else onSelectItem(item.id)
                                    }
                                },
                                onBeginGesture = {
                                    isActivelyDraggingItem = true
                                    onBeginGestureUndo()
                                },
                                onDragDelta = { dx, dy -> onMoveItem(item.id, dx / pageW, dy / pageH) },
                                onScaleFactor = { factor -> onScaleItem(item.id, factor) },
                                onRotateDelta = { deg -> onRotateItem(item.id, deg) },
                                onDragEndSnapCheck = { isActivelyDraggingItem = false }
                            )
                        }
                        is OverlayItem.ImageItem -> {
                            ImageOverlayNode(
                                item = item,
                                pageW = pageW,
                                pageH = pageH,
                                isSelected = isSelected,
                                isMoveAllHighlighted = isMoveAllMode,
                                onSelect = { if (!isMoveAllMode) onSelectItem(item.id) },
                                onBeginGesture = {
                                    isActivelyDraggingItem = true
                                    onBeginGestureUndo()
                                },
                                onDragDelta = { dx, dy -> onMoveItem(item.id, dx / pageW, dy / pageH) },
                                onScaleFactor = { factor -> onScaleItem(item.id, factor) },
                                onRotateDelta = { deg -> onRotateItem(item.id, deg) },
                                onDragEndSnapCheck = { isActivelyDraggingItem = false }
                            )
                        }
                        is OverlayItem.DrawingItem -> {
                            DrawingOverlayNode(
                                item = item,
                                pageW = pageW,
                                pageH = pageH,
                                pxPerPt = pxPerPt,
                                isSelected = isSelected,
                                isMoveAllHighlighted = isMoveAllMode,
                                onSelect = { if (!isMoveAllMode) onSelectItem(item.id) },
                                onBeginGesture = {
                                    isActivelyDraggingItem = true
                                    onBeginGestureUndo()
                                },
                                onDragDelta = { dx, dy -> onMoveItem(item.id, dx / pageW, dy / pageH) },
                                onScaleFactor = { factor -> onScaleItem(item.id, factor) },
                                onRotateDelta = { deg -> onRotateItem(item.id, deg) },
                                onDragEndSnapCheck = { isActivelyDraggingItem = false }
                            )
                        }
                        is OverlayItem.ShapeItem -> {
                            ShapeOverlayNode(
                                item = item,
                                pageW = pageW,
                                pageH = pageH,
                                pxPerPt = pxPerPt,
                                isSelected = isSelected,
                                isMoveAllHighlighted = isMoveAllMode,
                                onSelect = { if (!isMoveAllMode) onSelectItem(item.id) },
                                onBeginGesture = {
                                    isActivelyDraggingItem = true
                                    onBeginGestureUndo()
                                },
                                onDragDelta = { dx, dy -> onMoveItem(item.id, dx / pageW, dy / pageH) },
                                onScaleFactor = { factor -> onScaleItem(item.id, factor) },
                                onRotateDelta = { deg -> onRotateItem(item.id, deg) },
                                onDragEndSnapCheck = { isActivelyDraggingItem = false }
                            )
                        }
                    }
                }
            }

            // Live capture layer when activeTool == EditorTool.DRAW
            if (activeTool == EditorTool.DRAW) {
                if (drawMode == DrawMode.SHAPES) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pageW, pageH, activeShapeType) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val pt = NormPoint(
                                            (startOffset.x / pageW).coerceIn(0f, 1f),
                                            (startOffset.y / pageH).coerceIn(0f, 1f)
                                        )
                                        liveShapeStart = pt
                                        liveShapeCurrent = pt
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        liveShapeCurrent = NormPoint(
                                            (change.position.x / pageW).coerceIn(0f, 1f),
                                            (change.position.y / pageH).coerceIn(0f, 1f)
                                        )
                                    },
                                    onDragEnd = {
                                        val s = liveShapeStart
                                        val e = liveShapeCurrent
                                        liveShapeStart = null
                                        liveShapeCurrent = null
                                        if (s != null && e != null) {
                                            val minX = min(s.x, e.x)
                                            val maxX = max(s.x, e.x)
                                            val minY = min(s.y, e.y)
                                            val maxY = max(s.y, e.y)
                                            val rawW = maxX - minX
                                            val rawH = maxY - minY
                                            if (rawW > 0.03f || rawH > 0.03f) {
                                                val finalW = rawW.coerceIn(0.05f, 0.92f)
                                                val finalH = if (activeShapeType == GeometricShapeType.LINE) {
                                                    max(rawH, 0.035f).coerceIn(0.035f, 0.85f)
                                                } else {
                                                    rawH.coerceIn(0.04f, 0.92f)
                                                }
                                                onCommitShape(
                                                    activeShapeType,
                                                    (minX + maxX) / 2f,
                                                    (minY + maxY) / 2f,
                                                    finalW,
                                                    finalH
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        liveShapeStart = null
                                        liveShapeCurrent = null
                                    }
                                )
                            }
                    ) {
                        val s = liveShapeStart
                        val e = liveShapeCurrent
                        if (s != null && e != null) {
                            val left = min(s.x, e.x) * size.width
                            val top = min(s.y, e.y) * size.height
                            val w = (abs(e.x - s.x) * size.width).coerceAtLeast(8f)
                            val h = (abs(e.y - s.y) * size.height).coerceAtLeast(8f)
                            drawShapeGeometry(
                                shapeType = activeShapeType,
                                topLeft = Offset(left, top),
                                boxSize = Size(w, h),
                                color = drawColor.copy(alpha = shapeOpacity),
                                strokeWidthPx = (drawStrokeWidthPt * pxPerPt).coerceAtLeast(2f),
                                isFilled = shapeFilled
                            )
                        }
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pageW, pageH) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        currentLiveStroke.clear()
                                        currentLiveStroke.add(
                                            NormPoint(
                                                (startOffset.x / pageW).coerceIn(0f, 1f),
                                                (startOffset.y / pageH).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pt = NormPoint(
                                            (change.position.x / pageW).coerceIn(0f, 1f),
                                            (change.position.y / pageH).coerceIn(0f, 1f)
                                        )
                                        currentLiveStroke.add(pt)
                                    },
                                    onDragEnd = {
                                        if (currentLiveStroke.isNotEmpty()) {
                                            activeStrokes.add(currentLiveStroke.toList())
                                            currentLiveStroke.clear()
                                        }
                                    },
                                    onDragCancel = {
                                        currentLiveStroke.clear()
                                    }
                                )
                            }
                    ) {
                        val strokePx = (drawStrokeWidthPt * pxPerPt).coerceAtLeast(2f)
                        val liveColor = drawColor.copy(alpha = drawOpacity)
                        val allStrokes = activeStrokes + if (currentLiveStroke.isNotEmpty()) listOf(currentLiveStroke) else emptyList()
                        for (stroke in allStrokes) {
                            if (stroke.isEmpty()) continue
                            if (stroke.size == 1) {
                                drawCircle(
                                    color = liveColor,
                                    radius = strokePx / 2f,
                                    center = Offset(stroke[0].x * size.width, stroke[0].y * size.height)
                                )
                            } else {
                                val path = buildSmoothPath(stroke, size.width, size.height)
                                drawPath(
                                    path = path,
                                    color = liveColor,
                                    style = Stroke(
                                        width = strokePx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (zoom > 1.05f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable {
                        zoom = 1f
                        panOffset = Offset.Zero
                    },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 3.dp
            ) {
                Text(
                    text = stringResource(R.string.editor_zoom_reset, zoom),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TextOverlayNode(
    item: OverlayItem.TextItem,
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
    val xPx = (item.normX * pageW).roundToInt()
    val yPx = (item.normY * pageH).roundToInt()
    val fontSizeSp = with(density) { (item.fontSizePt * pxPerPt).toSp() }
    val lineHeightSp = with(density) { (item.fontSizePt * 1.25f * pxPerPt).toSp() }
    val selectionColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .graphicsLayer { rotationZ = item.rotationDeg }
            .then(
                if (item.hasWhiteBackground) {
                    Modifier.background(Color.White, RoundedCornerShape(2.dp))
                } else Modifier
            )
            .then(
                when {
                    isSelected -> Modifier
                        .border(1.5.dp, selectionColor, RoundedCornerShape(6.dp))
                        .background(selectionColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    isMoveAllHighlighted -> Modifier
                        .border(1.dp, selectionColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    else -> Modifier
                }
            )
            .pointerInput(item.id, isSelected) {
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
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Text(
            text = item.text,
            color = Color(item.colorArgb),
            style = TextStyle(
                fontFamily = item.font.composeFamily,
                fontSize = fontSizeSp,
                lineHeight = lineHeightSp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )

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

@Composable
private fun ImageOverlayNode(
    item: OverlayItem.ImageItem,
    pageW: Float,
    pageH: Float,
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

    val imageBitmap: ImageBitmap? = remember(item.localFilePath) {
        runCatching {
            BitmapFactory.decodeFile(item.localFilePath)?.asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx, topPx) }
            .size(wDp, hDp)
            .graphicsLayer { rotationZ = item.rotationDeg }
            .then(
                when {
                    isSelected -> Modifier.border(1.5.dp, selectionColor, RoundedCornerShape(4.dp))
                    isMoveAllHighlighted -> Modifier.border(1.dp, selectionColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
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
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.editor_added_image_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
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

@Composable
private fun DrawingOverlayNode(
    item: OverlayItem.DrawingItem,
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
            val strokePx = (item.strokeWidthPt * pxPerPt).coerceAtLeast(1.5f)
            val color = Color(item.colorArgb).copy(alpha = item.opacity.coerceIn(0.05f, 1f))
            for (stroke in item.strokes) {
                val pts = stroke.points
                if (pts.isEmpty()) continue
                if (pts.size == 1) {
                    drawCircle(
                        color = color,
                        radius = strokePx / 2f,
                        center = Offset(pts[0].x * size.width, pts[0].y * size.height)
                    )
                } else {
                    val path = buildSmoothPath(pts, size.width, size.height)
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
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

private fun buildSmoothPath(points: List<NormPoint>, width: Float, height: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    val p0 = Offset(points[0].x * width, points[0].y * height)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until points.size) {
        val prev = Offset(points[i - 1].x * width, points[i - 1].y * height)
        val curr = Offset(points[i].x * width, points[i].y * height)
        val mid = Offset((prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
        path.quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    val last = Offset(points.last().x * width, points.last().y * height)
    path.lineTo(last.x, last.y)
    return path
}
