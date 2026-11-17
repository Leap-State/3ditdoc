package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DrawMode
import com.example.model.GeometricShapeType
import com.example.model.OverlayItem
import com.example.model.OverlayPalette
import com.example.model.QuickStampType

@Composable
fun SelectedItemContextBar(
    item: OverlayItem,
    isMovePanelExpanded: Boolean,
    isMoveAllMode: Boolean,
    onToggleMovePanel: () -> Unit,
    onToggleMoveAllMode: () -> Unit,
    onNudge: (dxNorm: Float, dyNorm: Float) -> Unit,
    onCenterHorizontal: () -> Unit,
    onBringToFront: (String) -> Unit,
    onEditText: (OverlayItem.TextItem) -> Unit,
    onRotate90: (String) -> Unit,
    onToggleImageBgRemoval: (String) -> Unit,
    onChangeDrawingColor: (String, Color) -> Unit,
    onUpdateShapeStyle: (id: String, color: Color?, isFilled: Boolean?, opacity: Float?) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary item header & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (item) {
                    is OverlayItem.TextItem -> {
                        Text(
                            text = stringResource(R.string.selected_text_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onEditText(item) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.selected_edit))
                            }
                            MoveToggleChip(
                                expanded = isMovePanelExpanded,
                                onClick = onToggleMovePanel
                            )
                        }
                    }

                    is OverlayItem.ImageItem -> {
                        Text(
                            text = stringResource(R.string.selected_image_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MoveToggleChip(
                            expanded = isMovePanelExpanded,
                            onClick = onToggleMovePanel
                        )
                    }

                    is OverlayItem.DrawingItem -> {
                        val palette = if (item.opacity < 0.99f) {
                            OverlayPalette.highlighterColors.take(4)
                        } else {
                            OverlayPalette.inkColors.take(4)
                        }
                        ColorSwatchRow(
                            colors = palette,
                            selectedColor = Color(item.colorArgb),
                            onSelectColor = { c -> onChangeDrawingColor(item.id, c) }
                        )
                        MoveToggleChip(
                            expanded = isMovePanelExpanded,
                            onClick = onToggleMovePanel
                        )
                    }

                    is OverlayItem.ShapeItem -> {
                        ColorSwatchRow(
                            colors = OverlayPalette.inkColors.take(5),
                            selectedColor = Color(item.colorArgb),
                            onSelectColor = { c -> onUpdateShapeStyle(item.id, c, null, null) }
                        )
                        MoveToggleChip(
                            expanded = isMovePanelExpanded,
                            onClick = onToggleMovePanel
                        )
                    }
                }
            }

            // Secondary quick-actions row (Fill/Opacity for shapes, Bring Front, Rotate, Duplicate, Delete)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item is OverlayItem.ImageItem) {
                    FilterChip(
                        selected = item.isBackgroundRemoved,
                        onClick = { onToggleImageBgRemoval(item.id) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    if (item.isBackgroundRemoved) R.string.image_bg_removed else R.string.image_remove_bg
                                ),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("selected_image_remove_bg_chip")
                    )
                }

                if (item is OverlayItem.ShapeItem && item.shapeType != GeometricShapeType.LINE) {
                    FilterChip(
                        selected = item.isFilled,
                        onClick = { onUpdateShapeStyle(item.id, null, !item.isFilled, null) },
                        label = {
                            Text(
                                stringResource(if (item.isFilled) R.string.shape_filled else R.string.shape_outline),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    FilterChip(
                        selected = item.opacity < 0.99f,
                        onClick = {
                            val nextOpacity = if (item.opacity < 0.99f) 1f else 0.40f
                            onUpdateShapeStyle(item.id, null, null, nextOpacity)
                        },
                        label = {
                            Text(stringResource(R.string.shape_translucent), style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }

                IconButton(
                    onClick = { onBringToFront(item.id) },
                    modifier = Modifier.testTag("selected_bring_front_button")
                ) {
                    Icon(
                        Icons.Rounded.FlipToFront,
                        contentDescription = stringResource(R.string.selected_bring_front)
                    )
                }

                IconButton(onClick = { onRotate90(item.id) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.RotateRight,
                        contentDescription = stringResource(R.string.selected_rotate_90)
                    )
                }

                IconButton(
                    onClick = { onDuplicate(item.id) },
                    modifier = Modifier.testTag("selected_duplicate_button")
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.selected_duplicate)
                    )
                }

                IconButton(onClick = { onDelete(item.id) }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.selected_delete_item),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Expandable Precision Move & Alignment Dock
            AnimatedVisibility(visible = isMovePanelExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    PrecisionMoveStrip(
                        isMoveAllActive = isMoveAllMode,
                        showMoveAllToggle = true,
                        onToggleMoveAll = onToggleMoveAllMode,
                        onNudge = onNudge,
                        onCenterHorizontal = onCenterHorizontal
                    )
                }
            }
        }
    }
}

@Composable
fun MoveAllControlBar(
    onNudge: (dxNorm: Float, dyNorm: Float) -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.OpenWith,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.move_all_banner),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("move_all_done_button")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.move_all_done))
                }
            }

            PrecisionMoveStrip(
                isMoveAllActive = true,
                showMoveAllToggle = false,
                onToggleMoveAll = {},
                onNudge = onNudge,
                onCenterHorizontal = null
            )
        }
    }
}

@Composable
private fun MoveToggleChip(
    expanded: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = expanded,
        onClick = onClick,
        leadingIcon = {
            Icon(Icons.Rounded.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text(stringResource(R.string.selected_move)) },
        modifier = Modifier.testTag("selected_move_button")
    )
}

@Composable
private fun PrecisionMoveStrip(
    isMoveAllActive: Boolean,
    showMoveAllToggle: Boolean,
    onToggleMoveAll: () -> Unit,
    onNudge: (dxNorm: Float, dyNorm: Float) -> Unit,
    onCenterHorizontal: (() -> Unit)?
) {
    var fineStep by remember { mutableStateOf(false) }
    val step = if (fineStep) 0.004f else 0.018f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = { onNudge(-step, 0f) },
            modifier = Modifier.testTag("move_pad_left")
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.move_pad_left)
            )
        }
        IconButton(
            onClick = { onNudge(0f, -step) },
            modifier = Modifier.testTag("move_pad_up")
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.move_pad_up)
            )
        }
        IconButton(
            onClick = { onNudge(0f, step) },
            modifier = Modifier.testTag("move_pad_down")
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.move_pad_down)
            )
        }
        IconButton(
            onClick = { onNudge(step, 0f) },
            modifier = Modifier.testTag("move_pad_right")
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.move_pad_right)
            )
        }

        FilterChip(
            selected = fineStep,
            onClick = { fineStep = !fineStep },
            label = {
                Text(
                    stringResource(if (fineStep) R.string.move_step_fine else R.string.move_step_medium),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )

        if (onCenterHorizontal != null && !isMoveAllActive) {
            FilterChip(
                selected = false,
                onClick = onCenterHorizontal,
                leadingIcon = {
                    Icon(Icons.Rounded.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(stringResource(R.string.move_center_page), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("move_center_button")
            )
        }

        if (showMoveAllToggle) {
            FilterChip(
                selected = isMoveAllActive,
                onClick = onToggleMoveAll,
                leadingIcon = {
                    Icon(Icons.Rounded.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(stringResource(R.string.selected_move_all), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("move_all_toggle_chip")
            )
        }
    }
}

@Composable
fun DrawingControlsBar(
    drawMode: DrawMode,
    drawColor: Color,
    strokeWidthPt: Float,
    activeShapeType: GeometricShapeType,
    shapeFilled: Boolean,
    shapeOpacity: Float,
    hasPendingStrokes: Boolean,
    hasLastSignature: Boolean,
    onSelectDrawMode: (DrawMode) -> Unit,
    onSelectColor: (Color) -> Unit,
    onSelectStrokeWidth: (Float) -> Unit,
    onSelectShapeType: (GeometricShapeType) -> Unit,
    onSetShapeFilled: (Boolean) -> Unit,
    onSetShapeOpacity: (Float) -> Unit,
    onInsertSelectedShape: () -> Unit,
    onOpenSignaturePad: () -> Unit,
    onReuseLastSignature: () -> Unit,
    onAddQuickStamp: (QuickStampType) -> Unit,
    onUndoStroke: () -> Unit,
    onFinishDrawing: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Pen / Highlighter / Shapes mode chips + Undo / Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = drawMode == DrawMode.PEN,
                        onClick = { onSelectDrawMode(DrawMode.PEN) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.draw_mode_pen)) },
                        modifier = Modifier.testTag("draw_mode_pen_chip")
                    )
                    FilterChip(
                        selected = drawMode == DrawMode.HIGHLIGHTER,
                        onClick = { onSelectDrawMode(DrawMode.HIGHLIGHTER) },
                        leadingIcon = {
                            Icon(Icons.Rounded.BorderColor, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.draw_mode_highlighter)) },
                        modifier = Modifier.testTag("draw_mode_highlighter_chip")
                    )
                    FilterChip(
                        selected = drawMode == DrawMode.SHAPES,
                        onClick = { onSelectDrawMode(DrawMode.SHAPES) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.draw_mode_shapes)) },
                        modifier = Modifier.testTag("draw_mode_shapes_chip")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hasPendingStrokes && drawMode != DrawMode.SHAPES) {
                        TextButton(onClick = onUndoStroke) {
                            Text(stringResource(R.string.draw_undo_stroke), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = onFinishDrawing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("finish_drawing_button")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.draw_done))
                    }
                }
            }

            if (drawMode == DrawMode.SHAPES) {
                // Row 2 (Shapes): Geometric Shape Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeometricShapeType.entries.forEach { shape ->
                        val selected = activeShapeType == shape
                        val chipInk = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectShapeType(shape) },
                            leadingIcon = {
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    drawShapeGeometry(
                                        shapeType = shape,
                                        topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                        boxSize = size,
                                        color = chipInk,
                                        strokeWidthPx = 3f,
                                        isFilled = shapeFilled && shape != GeometricShapeType.LINE
                                    )
                                }
                            },
                            label = { Text(stringResource(shape.labelResId)) },
                            modifier = Modifier.testTag("shape_chip_${shape.name.lowercase()}")
                        )
                    }
                }

                // Row 3 (Shapes): Colors + Fill/Outline + Soft Opacity + Insert Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSwatchRow(
                        colors = OverlayPalette.inkColors,
                        selectedColor = drawColor,
                        onSelectColor = onSelectColor
                    )

                    if (activeShapeType != GeometricShapeType.LINE) {
                        FilterChip(
                            selected = shapeFilled,
                            onClick = { onSetShapeFilled(!shapeFilled) },
                            label = {
                                Text(
                                    stringResource(if (shapeFilled) R.string.shape_filled else R.string.shape_outline),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("shape_fill_toggle")
                        )
                    }

                    FilterChip(
                        selected = shapeOpacity < 0.99f,
                        onClick = { onSetShapeOpacity(if (shapeOpacity < 0.99f) 1f else 0.40f) },
                        label = { Text(stringResource(R.string.shape_translucent), style = MaterialTheme.typography.labelSmall) }
                    )

                    OutlinedButton(
                        onClick = onInsertSelectedShape,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("insert_shape_button")
                    ) {
                        Text(stringResource(R.string.shape_insert_btn), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                // Row 2 (Pen / Highlighter): Colors + Stroke thickness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeColors = if (drawMode == DrawMode.HIGHLIGHTER) {
                        OverlayPalette.highlighterColors
                    } else {
                        OverlayPalette.inkColors.take(5)
                    }
                    ColorSwatchRow(
                        colors = activeColors,
                        selectedColor = drawColor,
                        onSelectColor = onSelectColor
                    )

                    if (drawMode == DrawMode.PEN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                2.0f to R.string.draw_size_s,
                                3.5f to R.string.draw_size_m,
                                6.0f to R.string.draw_size_l
                            ).forEach { (w, labelRes) ->
                                val active = strokeWidthPt == w
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { onSelectStrokeWidth(w) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 3: Signature Pad, Last Signature Reuse & Quick Stamps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenSignaturePad,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_signature_pad_button")
                    ) {
                        Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.draw_signature_btn), style = MaterialTheme.typography.labelMedium)
                    }

                    if (hasLastSignature) {
                        FilterChip(
                            selected = false,
                            onClick = onReuseLastSignature,
                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.draw_reuse_signature)) },
                            modifier = Modifier.testTag("reuse_last_signature_chip")
                        )
                    }

                    FilterChip(
                        selected = false,
                        onClick = { onAddQuickStamp(QuickStampType.CHECK) },
                        leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.stamp_check)) },
                        modifier = Modifier.testTag("quick_stamp_check")
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onAddQuickStamp(QuickStampType.CROSS) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.stamp_cross)) },
                        modifier = Modifier.testTag("quick_stamp_cross")
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onAddQuickStamp(QuickStampType.BOX) },
                        leadingIcon = { Icon(Icons.Rounded.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.stamp_box)) },
                        modifier = Modifier.testTag("quick_stamp_box")
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onAddQuickStamp(QuickStampType.LINE) },
                        leadingIcon = { Icon(Icons.Rounded.HorizontalRule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.stamp_line)) },
                        modifier = Modifier.testTag("quick_stamp_line")
                    )
                }
            }
        }
    }
}
