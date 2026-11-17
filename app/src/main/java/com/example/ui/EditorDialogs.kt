package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.NormPoint
import com.example.model.OverlayFont
import com.example.model.OverlayItem
import com.example.model.OverlayPalette
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorSheet(
    existingItem: OverlayItem.TextItem?,
    onDismiss: () -> Unit,
    onConfirm: (text: String, fontSizePt: Float, font: OverlayFont, color: Color, hasWhiteBackground: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var text by remember(existingItem?.id) { mutableStateOf(existingItem?.text ?: "") }
    var fontSizePt by remember(existingItem?.id) { mutableFloatStateOf(existingItem?.fontSizePt ?: 16f) }
    var font by remember(existingItem?.id) { mutableStateOf(existingItem?.font ?: OverlayFont.SANS) }
    var color by remember(existingItem?.id) {
        mutableStateOf(existingItem?.let { Color(it.colorArgb) } ?: OverlayPalette.inkColors[0])
    }
    var hasWhiteBackground by remember(existingItem?.id) {
        mutableStateOf(existingItem?.hasWhiteBackground ?: false)
    }

    val todayFormatted = remember {
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())
    }
    val approvedLabel = stringResource(R.string.text_preset_approved)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (existingItem == null) R.string.text_sheet_add_title else R.string.text_sheet_edit_title
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.text_sheet_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.text_sheet_label)) },
                placeholder = { Text(stringResource(R.string.text_sheet_placeholder)) },
                textStyle = TextStyle(fontFamily = font.composeFamily),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_editor_input")
            )

            // 1-tap form presets (Today's date, Check, Cross, Approved, N/A)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        text = if (text.isBlank()) todayFormatted else "$text $todayFormatted"
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text(stringResource(R.string.text_preset_today)) }
                )
                listOf("✓", "✗", approvedLabel, "N/A").forEach { preset ->
                    AssistChip(
                        onClick = {
                            text = if (text.isBlank()) preset else "$text $preset"
                        },
                        label = { Text(preset) }
                    )
                }
            }

            Text(stringResource(R.string.text_sheet_font), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayFont.entries.forEach { option ->
                    FilterChip(
                        selected = font == option,
                        onClick = { font = option },
                        label = {
                            Text(
                                text = stringResource(option.displayNameResId),
                                style = TextStyle(fontFamily = option.composeFamily)
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.text_sheet_size, fontSizePt.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(124.dp)
                )
                Slider(
                    value = fontSizePt,
                    onValueChange = { fontSizePt = it },
                    valueRange = 10f..44f,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(stringResource(R.string.text_sheet_color), style = MaterialTheme.typography.labelLarge)
            ColorSwatchRow(
                selectedColor = color,
                onSelectColor = { color = it }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hasWhiteBackground = !hasWhiteBackground }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.text_white_bg_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hasWhiteBackground,
                    onCheckedChange = { hasWhiteBackground = it },
                    modifier = Modifier.testTag("text_white_bg_switch")
                )
            }

            Button(
                onClick = { onConfirm(text, fontSizePt, font, color, hasWhiteBackground) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("text_editor_confirm"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (existingItem == null) R.string.text_sheet_place else R.string.text_sheet_apply
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceSheet(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.image_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.image_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onPickGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("pick_gallery_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.image_pick_gallery))
            }

            OutlinedButton(
                onClick = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("take_photo_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.image_take_photo))
            }
        }
    }
}

@Composable
fun SignaturePadDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirmSignature: (strokes: List<List<NormPoint>>, color: Color, strokePt: Float) -> Unit
) {
    var inkColor by remember { mutableStateOf(initialColor) }
    var strokePt by remember { mutableFloatStateOf(3.2f) }
    val strokes = remember { mutableStateListOf<List<NormPoint>>() }
    val activeStroke = remember { mutableStateListOf<NormPoint>() }
    var padSize by remember { mutableStateOf(IntSize(1, 1)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.signature_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.signature_close)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.signature_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .onSizeChanged { padSize = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(padSize) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        activeStroke.clear()
                                        activeStroke.add(
                                            NormPoint(
                                                (pos.x / padSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                                (pos.y / padSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeStroke.add(
                                            NormPoint(
                                                (change.position.x / padSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                                (change.position.y / padSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDragEnd = {
                                        if (activeStroke.isNotEmpty()) {
                                            strokes.add(activeStroke.toList())
                                            activeStroke.clear()
                                        }
                                    },
                                    onDragCancel = { activeStroke.clear() }
                                )
                            }
                    ) {
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(24.dp.toPx(), size.height * 0.74f),
                            end = Offset(size.width - 24.dp.toPx(), size.height * 0.74f),
                            strokeWidth = 1.dp.toPx()
                        )

                        val all = strokes + if (activeStroke.isNotEmpty()) listOf(activeStroke) else emptyList()
                        val strokePx = strokePt * 2.4f
                        for (s in all) {
                            if (s.isEmpty()) continue
                            if (s.size == 1) {
                                drawCircle(
                                    color = inkColor,
                                    radius = strokePx / 2f,
                                    center = Offset(s[0].x * size.width, s[0].y * size.height)
                                )
                            } else {
                                val path = Path().apply {
                                    moveTo(s[0].x * size.width, s[0].y * size.height)
                                    for (i in 1 until s.size) {
                                        val px = s[i - 1].x * size.width
                                        val py = s[i - 1].y * size.height
                                        val cx = s[i].x * size.width
                                        val cy = s[i].y * size.height
                                        quadraticTo(px, py, (px + cx) / 2f, (py + cy) / 2f)
                                    }
                                    lineTo(s.last().x * size.width, s.last().y * size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    if (strokes.isEmpty() && activeStroke.isEmpty()) {
                        Text(
                            text = stringResource(R.string.signature_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSwatchRow(
                        colors = OverlayPalette.inkColors.take(4),
                        selectedColor = inkColor,
                        onSelectColor = { inkColor = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            2.0f to R.string.signature_thin,
                            3.4f to R.string.signature_medium,
                            5.2f to R.string.signature_bold
                        ).forEach { (w, labelRes) ->
                            FilterChip(
                                selected = strokePt == w,
                                onClick = { strokePt = w },
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.signature_clear))
                    }
                    Button(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                val mapped = strokes.map { s ->
                                    s.map { p ->
                                        NormPoint(
                                            x = 0.25f + p.x * 0.50f,
                                            y = 0.42f + p.y * 0.16f
                                        )
                                    }
                                }
                                onConfirmSignature(mapped, inkColor, strokePt)
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_signature_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.signature_insert))
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSwatchRow(
    colors: List<Color> = OverlayPalette.inkColors,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { swatch ->
            val isSelected = swatch.toArgb() == selectedColor.toArgb()
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                        shape = CircleShape
                    )
                    .clickable { onSelectColor(swatch) }
            )
        }
    }
}

@Composable
fun ConfirmClearLayerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_dialog_title)) },
        text = { Text(stringResource(R.string.clear_dialog_body)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.clear_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
