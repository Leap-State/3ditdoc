package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DrawMode
import com.example.model.EditorTool
import com.example.model.GeometricShapeType
import com.example.model.NormPoint
import com.example.model.OverlayFont
import com.example.model.OverlayItem
import com.example.model.QuickStampType
import com.example.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onBackHome: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onSetActiveTool: (EditorTool) -> Unit,
    onSetDrawMode: (DrawMode) -> Unit,
    onSetActiveShapeType: (GeometricShapeType) -> Unit,
    onSetShapeFilled: (Boolean) -> Unit,
    onSetShapeOpacity: (Float) -> Unit,
    onToggleMoveAllMode: (Boolean?) -> Unit,
    onToggleMovePanel: () -> Unit,
    onNudgeSelectedOrAll: (Float, Float) -> Unit,
    onCenterHorizontal: () -> Unit,
    onBringToFront: (String) -> Unit,
    onToggleLayerVisibility: () -> Unit,
    onSelectItem: (String?) -> Unit,
    onAddText: (text: String, normX: Float, normY: Float, fontSizePt: Float, font: OverlayFont, color: Color, hasWhiteBackground: Boolean) -> Unit,
    onUpdateText: (id: String, text: String, fontSizePt: Float, font: OverlayFont, color: Color, hasWhiteBackground: Boolean) -> Unit,
    onPickGalleryImage: () -> Unit,
    onTakeCameraPhoto: () -> Unit,
    onSetDrawColor: (Color) -> Unit,
    onSetDrawStrokeWidth: (Float) -> Unit,
    onCommitDrawing: (strokes: List<List<NormPoint>>, color: Color, strokePt: Float, opacity: Float, isSignature: Boolean) -> Unit,
    onCommitShape: (shapeType: GeometricShapeType, centerX: Float, centerY: Float, normWidth: Float, normHeight: Float) -> Unit,
    onInsertDefaultShape: () -> Unit,
    onReuseLastSignature: () -> Unit,
    onAddQuickStamp: (QuickStampType) -> Unit,
    onBeginGestureUndo: () -> Unit,
    onMoveItem: (String, Float, Float) -> Unit,
    onMoveAllItems: (Float, Float) -> Unit,
    onScaleItem: (String, Float) -> Unit,
    onRotateItem: (String, Float, Boolean) -> Unit,
    onToggleImageBgRemoval: (String) -> Unit,
    onLoadPageThumbnails: () -> Unit,
    onRotatePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAppendPdf: () -> Unit,
    onChangeDrawingColor: (String, Color) -> Unit,
    onUpdateShapeStyle: (id: String, color: Color?, isFilled: Boolean?, opacity: Float?) -> Unit,
    onDuplicateItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAllLayers: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenPreviewSave: () -> Unit,
    onExportCurrentPageAsImage: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenLegal: () -> Unit
) {
    val doc = state.document ?: return
    val currentMeta = doc.pages.getOrNull(state.currentPageIndex)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showPageOrganizer by remember { mutableStateOf(false) }

    var textModalTargetCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingTextItem by remember { mutableStateOf<OverlayItem.TextItem?>(null) }

    val activeStrokes = remember(state.currentPageIndex) { mutableStateListOf<List<NormPoint>>() }

    fun commitActiveStrokesIfAny() {
        if (activeStrokes.isNotEmpty()) {
            onCommitDrawing(
                activeStrokes.toList(),
                state.activeDrawInkColor,
                state.activeDrawStrokePt,
                state.activeDrawOpacity,
                false
            )
            activeStrokes.clear()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.totalOverlayCount > 0) {
                                showExitConfirm = true
                            } else {
                                onBackHome()
                            }
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = doc.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.isLayerVisible) {
                                stringResource(R.string.editor_layer_active, state.totalOverlayCount)
                            } else {
                                stringResource(R.string.editor_viewing_original)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isLayerVisible) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onUndo,
                        enabled = state.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.editor_undo)
                        )
                    }
                    IconButton(
                        onClick = onToggleLayerVisibility,
                        modifier = Modifier.testTag("toggle_layer_visibility")
                    ) {
                        Icon(
                            imageVector = if (state.isLayerVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = stringResource(
                                if (state.isLayerVisible) R.string.editor_hide_layer else R.string.editor_show_layer
                            )
                        )
                    }
                    Button(
                        onClick = {
                            commitActiveStrokesIfAny()
                            onOpenPreviewSave()
                        },
                        enabled = !state.isGeneratingPreview,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(end = 4.dp)
                            .testTag("preview_save_button"),
                        shape = RoundedCornerShape(50)
                    ) {
                        if (state.isGeneratingPreview) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.editor_preview_btn),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.editor_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_redo)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null) },
                                enabled = state.canRedo,
                                onClick = {
                                    showOverflowMenu = false
                                    onRedo()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_move_all_action)) },
                                leadingIcon = { Icon(Icons.Rounded.OpenWith, contentDescription = null) },
                                enabled = state.currentPageItems.isNotEmpty(),
                                onClick = {
                                    showOverflowMenu = false
                                    commitActiveStrokesIfAny()
                                    onToggleMoveAllMode(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pages_organizer_action)) },
                                leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    commitActiveStrokesIfAny()
                                    showPageOrganizer = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_export_page_image)) },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    commitActiveStrokesIfAny()
                                    onExportCurrentPageAsImage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_clear_layer)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                                enabled = state.totalOverlayCount > 0,
                                onClick = {
                                    showOverflowMenu = false
                                    showClearConfirm = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_tutorial)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenTutorial()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_privacy)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenLegal()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    AnimatedVisibility(visible = state.activeTool == EditorTool.DRAW) {
                        DrawingControlsBar(
                            drawMode = state.drawMode,
                            drawColor = state.activeDrawInkColor,
                            strokeWidthPt = state.drawStrokeWidthPt,
                            activeShapeType = state.activeShapeType,
                            shapeFilled = state.shapeFilled,
                            shapeOpacity = state.shapeOpacity,
                            hasPendingStrokes = activeStrokes.isNotEmpty(),
                            hasLastSignature = state.lastSavedSignature != null,
                            onSelectDrawMode = onSetDrawMode,
                            onSelectColor = onSetDrawColor,
                            onSelectStrokeWidth = onSetDrawStrokeWidth,
                            onSelectShapeType = onSetActiveShapeType,
                            onSetShapeFilled = onSetShapeFilled,
                            onSetShapeOpacity = onSetShapeOpacity,
                            onInsertSelectedShape = onInsertDefaultShape,
                            onOpenSignaturePad = { showSignaturePad = true },
                            onReuseLastSignature = onReuseLastSignature,
                            onAddQuickStamp = onAddQuickStamp,
                            onUndoStroke = {
                                if (activeStrokes.isNotEmpty()) {
                                    activeStrokes.removeAt(activeStrokes.lastIndex)
                                }
                            },
                            onFinishDrawing = {
                                commitActiveStrokesIfAny()
                                onSetActiveTool(EditorTool.SELECT)
                            }
                        )
                    }

                    AnimatedVisibility(visible = state.activeTool != EditorTool.DRAW && state.isMoveAllMode) {
                        MoveAllControlBar(
                            onNudge = onNudgeSelectedOrAll,
                            onDone = { onToggleMoveAllMode(false) }
                        )
                    }

                    AnimatedVisibility(
                        visible = state.activeTool != EditorTool.DRAW &&
                            !state.isMoveAllMode &&
                            state.selectedItem != null
                    ) {
                        state.selectedItem?.let { item ->
                            SelectedItemContextBar(
                                item = item,
                                isMovePanelExpanded = state.isMovePanelExpanded,
                                isMoveAllMode = state.isMoveAllMode,
                                onToggleMovePanel = onToggleMovePanel,
                                onToggleMoveAllMode = { onToggleMoveAllMode(null) },
                                onNudge = onNudgeSelectedOrAll,
                                onCenterHorizontal = onCenterHorizontal,
                                onBringToFront = onBringToFront,
                                onEditText = { editingTextItem = it },
                                onRotate90 = { id -> onRotateItem(id, 90f, true) },
                                onToggleImageBgRemoval = onToggleImageBgRemoval,
                                onChangeDrawingColor = { id, c -> onChangeDrawingColor(id, c) },
                                onUpdateShapeStyle = onUpdateShapeStyle,
                                onDuplicate = onDuplicateItem,
                                onDelete = onDeleteItem
                            )
                        }
                    }

                    // Strictly 3 primary visible actions: Write, Image, Draw
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryToolButton(
                            icon = Icons.Rounded.TextFields,
                            label = stringResource(R.string.tool_text),
                            isActive = state.activeTool == EditorTool.TEXT,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_text_button"),
                            onClick = {
                                commitActiveStrokesIfAny()
                                if (state.activeTool == EditorTool.TEXT) {
                                    onSetActiveTool(EditorTool.SELECT)
                                } else {
                                    onSetActiveTool(EditorTool.TEXT)
                                }
                            }
                        )
                        PrimaryToolButton(
                            icon = Icons.Rounded.AddPhotoAlternate,
                            label = stringResource(R.string.tool_image),
                            isActive = showImageSourceSheet,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_image_button"),
                            onClick = {
                                commitActiveStrokesIfAny()
                                onSetActiveTool(EditorTool.SELECT)
                                showImageSourceSheet = true
                            }
                        )
                        PrimaryToolButton(
                            icon = Icons.Rounded.Draw,
                            label = stringResource(R.string.tool_draw),
                            isActive = state.activeTool == EditorTool.DRAW,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_draw_button"),
                            onClick = {
                                if (state.activeTool == EditorTool.DRAW) {
                                    commitActiveStrokesIfAny()
                                    onSetActiveTool(EditorTool.SELECT)
                                } else {
                                    onSetActiveTool(EditorTool.DRAW)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PdfPageInteractiveCanvas(
                pageBitmap = state.currentPageBitmap,
                pageMeta = currentMeta,
                items = state.currentPageItems,
                selectedItemId = state.selectedItemId,
                activeTool = state.activeTool,
                drawMode = state.drawMode,
                activeShapeType = state.activeShapeType,
                shapeFilled = state.shapeFilled,
                shapeOpacity = state.shapeOpacity,
                isLayerVisible = state.isLayerVisible,
                isMoveAllMode = state.isMoveAllMode,
                drawColor = state.activeDrawInkColor,
                drawStrokeWidthPt = state.activeDrawStrokePt,
                drawOpacity = state.activeDrawOpacity,
                activeStrokes = activeStrokes,
                onSelectItem = onSelectItem,
                onEditTextItem = { editingTextItem = it },
                onTapToPlaceText = { nx, ny ->
                    textModalTargetCoords = nx to ny
                },
                onCommitShape = onCommitShape,
                onBeginGestureUndo = onBeginGestureUndo,
                onMoveItem = onMoveItem,
                onMoveAllItems = onMoveAllItems,
                onScaleItem = onScaleItem,
                onRotateItem = { id, deg -> onRotateItem(id, deg, false) }
            )

            // Floating 1-tap Move button when items exist on page (works across all item types)
            AnimatedVisibility(
                visible = state.currentPageItems.isNotEmpty() &&
                    state.activeTool != EditorTool.DRAW &&
                    !state.isMoveAllMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (state.selectedItemId != null) {
                                onToggleMovePanel()
                            } else {
                                onToggleMoveAllMode(true)
                            }
                        }
                        .testTag("canvas_move_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenWith,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.selected_move),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.activeTool == EditorTool.TEXT,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = stringResource(R.string.editor_tap_to_write_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.activeTool == EditorTool.DRAW && state.drawMode == DrawMode.SHAPES,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = stringResource(R.string.editor_shapes_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (doc.pageCount > 1) {
                        IconButton(
                            onClick = {
                                commitActiveStrokesIfAny()
                                onSelectPage(state.currentPageIndex - 1)
                            },
                            enabled = state.currentPageIndex > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.editor_prev_page)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                commitActiveStrokesIfAny()
                                showPageOrganizer = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("open_page_organizer_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.pages_organizer_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.editor_page_indicator,
                                state.currentPageIndex + 1,
                                doc.pageCount
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (doc.pageCount > 1) {
                        IconButton(
                            onClick = {
                                commitActiveStrokesIfAny()
                                onSelectPage(state.currentPageIndex + 1)
                            },
                            enabled = state.currentPageIndex < doc.pageCount - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.editor_next_page)
                            )
                        }
                    }
                }
            }
        }
    }

    textModalTargetCoords?.let { (nx, ny) ->
        TextEditorSheet(
            existingItem = null,
            onDismiss = { textModalTargetCoords = null },
            onConfirm = { text, fontSizePt, font, color, hasWhiteBg ->
                onAddText(text, nx, ny, fontSizePt, font, color, hasWhiteBg)
                textModalTargetCoords = null
            }
        )
    }

    editingTextItem?.let { target ->
        TextEditorSheet(
            existingItem = target,
            onDismiss = { editingTextItem = null },
            onConfirm = { text, fontSizePt, font, color, hasWhiteBg ->
                onUpdateText(target.id, text, fontSizePt, font, color, hasWhiteBg)
                editingTextItem = null
            },
            onDelete = {
                onDeleteItem(target.id)
                editingTextItem = null
            }
        )
    }

    if (showImageSourceSheet) {
        ImageSourceSheet(
            onDismiss = { showImageSourceSheet = false },
            onPickGallery = {
                showImageSourceSheet = false
                onPickGalleryImage()
            },
            onTakePhoto = {
                showImageSourceSheet = false
                onTakeCameraPhoto()
            }
        )
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            initialColor = state.drawColor,
            onDismiss = { showSignaturePad = false },
            onConfirmSignature = { strokes, color, strokePt ->
                onCommitDrawing(strokes, color, strokePt, 1f, true)
                showSignaturePad = false
            }
        )
    }

    if (showClearConfirm) {
        ConfirmClearLayerDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                activeStrokes.clear()
                onClearAllLayers()
                showClearConfirm = false
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.exit_dialog_title)) },
            text = { Text(stringResource(R.string.exit_dialog_body)) },
            confirmButton = {
                Button(onClick = {
                    showExitConfirm = false
                    onBackHome()
                }) {
                    Text(stringResource(R.string.exit_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.exit_dialog_keep))
                }
            }
        )
    }

    if (showPageOrganizer) {
        PageOrganizerDialog(
            document = doc,
            currentPageIndex = state.currentPageIndex,
            pageThumbnails = state.pageThumbnails,
            overlayItemsByPage = state.overlayItemsByPage,
            isBusy = state.isOrganizingPages,
            onLoadThumbnails = onLoadPageThumbnails,
            onSelectPage = onSelectPage,
            onRotatePage = onRotatePage,
            onMovePage = onMovePage,
            onDeletePage = onDeletePage,
            onAppendPdf = onAppendPdf,
            onDismiss = { showPageOrganizer = false }
        )
    }
}

@Composable
private fun PrimaryToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (isActive) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else null

    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = border
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
