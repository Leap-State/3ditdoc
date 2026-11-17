package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ImageToPdfResult
import com.example.data.MergedPdfResult
import com.example.data.PdfConversionPreview
import com.example.data.PdfEngine
import com.example.data.PdfOverlayExporter
import com.example.data.PreferencesRepository
import com.example.model.DrawMode
import com.example.model.EditorTool
import com.example.model.GeometricShapeType
import com.example.model.ImagePageDraft
import com.example.model.MergePdfSource
import com.example.model.NormPoint
import com.example.model.NormStroke
import com.example.model.OverlayFont
import com.example.model.OverlayItem
import com.example.model.OverlayPalette
import com.example.model.PdfDocumentMeta
import com.example.model.QuickStampType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class EditorUiState(
    val document: PdfDocumentMeta? = null,
    val currentPageIndex: Int = 0,
    val currentPageBitmap: Bitmap? = null,
    val isLoadingDocument: Boolean = false,
    val isRenderingPage: Boolean = false,
    val overlayItemsByPage: Map<Int, List<OverlayItem>> = emptyMap(),
    val selectedItemId: String? = null,
    val activeTool: EditorTool = EditorTool.SELECT,
    val isLayerVisible: Boolean = true,
    val drawMode: DrawMode = DrawMode.PEN,
    val drawColor: Color = OverlayPalette.inkColors[0],
    val highlighterColor: Color = OverlayPalette.highlighterColors[0],
    val drawStrokeWidthPt: Float = 3.0f,
    val activeShapeType: GeometricShapeType = GeometricShapeType.RECTANGLE,
    val shapeFilled: Boolean = false,
    val shapeOpacity: Float = 1f,
    val isMoveAllMode: Boolean = false,
    val isMovePanelExpanded: Boolean = false,
    val lastSavedSignature: List<List<NormPoint>>? = null,
    val isGeneratingPreview: Boolean = false,
    val previewPdfPath: String? = null,
    val previewPageIndex: Int = 0,
    val previewPageBitmap: Bitmap? = null,
    val isSavingOutput: Boolean = false,
    val savedSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Converter, Page Organizer, & Merge PDF states
    val isConverting: Boolean = false,
    val isOrganizingPages: Boolean = false,
    val pageThumbnails: Map<Int, Bitmap> = emptyMap(),
    val pdfToImagePreview: PdfConversionPreview? = null,
    val imagePageDrafts: List<ImagePageDraft> = emptyList(),
    val imageToPdfResult: ImageToPdfResult? = null,
    val isMergeDialogOpen: Boolean = false,
    val mergeSources: List<MergePdfSource> = emptyList(),
    val mergedPdfResult: MergedPdfResult? = null,
    val isMergingPdfs: Boolean = false,
    val converterStatusMessage: String? = null
) {
    val currentPageItems: List<OverlayItem>
        get() = overlayItemsByPage[currentPageIndex].orEmpty()

    val selectedItem: OverlayItem?
        get() = currentPageItems.firstOrNull { it.id == selectedItemId }

    val totalOverlayCount: Int
        get() = overlayItemsByPage.values.sumOf { it.size }

    val activeDrawInkColor: Color
        get() = if (drawMode == DrawMode.HIGHLIGHTER) highlighterColor else drawColor

    val activeDrawStrokePt: Float
        get() = if (drawMode == DrawMode.HIGHLIGHTER) 14f else drawStrokeWidthPt

    val activeDrawOpacity: Float
        get() = if (drawMode == DrawMode.HIGHLIGHTER) 0.38f else 1f
}

class PdfEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val pdfEngine = PdfEngine(application)
    private val exporter = PdfOverlayExporter(application)
    private val prefs = PreferencesRepository(application)

    val hasSeenTutorial: StateFlow<Boolean?> = prefs.hasSeenTutorial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<Map<Int, List<OverlayItem>>>()
    private val redoStack = ArrayDeque<Map<Int, List<OverlayItem>>>()
    private val maxHistory = 30

    private var pendingCameraFile: File? = null

    fun markTutorialSeen() {
        viewModelScope.launch {
            prefs.markTutorialSeen()
        }
    }

    fun openPdfFromUri(uri: Uri, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDocument = true, errorMessage = null) }
            val result = pdfEngine.importPdfToCache(uri)
            result.onSuccess { meta ->
                applyOpenedDocument(meta, onOpened)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDocument = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun openSampleTestPdf(onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDocument = true, errorMessage = null) }
            val result = pdfEngine.createSampleTestPdf()
            result.onSuccess { meta ->
                applyOpenedDocument(meta, onOpened)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDocument = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    private fun applyOpenedDocument(meta: PdfDocumentMeta, onOpened: () -> Unit) {
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            EditorUiState(
                document = meta,
                currentPageIndex = 0,
                isLoadingDocument = false
            )
        }
        loadPageBitmap(meta.sourceCachePath, 0)
        onOpened()
    }

    fun closeDocument() {
        undoStack.clear()
        redoStack.clear()
        pdfEngine.clearBitmapCache()
        _uiState.value = EditorUiState()
    }

    fun selectPage(index: Int) {
        val doc = _uiState.value.document ?: return
        val clamped = index.coerceIn(0, doc.pageCount - 1)
        if (clamped == _uiState.value.currentPageIndex && _uiState.value.currentPageBitmap != null) return

        _uiState.update {
            it.copy(
                currentPageIndex = clamped,
                selectedItemId = null
            )
        }
        loadPageBitmap(doc.sourceCachePath, clamped)
    }

    private fun loadPageBitmap(pdfPath: String, pageIndex: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRenderingPage = true) }
            val bmp = pdfEngine.renderPage(pdfPath, pageIndex, targetWidthPx = 1500, cacheKeyPrefix = "src")
            _uiState.update {
                if (it.currentPageIndex == pageIndex) {
                    it.copy(currentPageBitmap = bmp, isRenderingPage = false)
                } else {
                    it.copy(isRenderingPage = false)
                }
            }
        }
    }

    fun setActiveTool(tool: EditorTool) {
        _uiState.update {
            it.copy(
                activeTool = tool,
                isMoveAllMode = if (tool != EditorTool.SELECT) false else it.isMoveAllMode,
                isMovePanelExpanded = if (tool != EditorTool.SELECT) false else it.isMovePanelExpanded,
                selectedItemId = if (tool == EditorTool.DRAW) null else it.selectedItemId
            )
        }
    }

    fun setDrawMode(mode: DrawMode) {
        _uiState.update { it.copy(drawMode = mode) }
    }

    fun setActiveShapeType(shapeType: GeometricShapeType) {
        _uiState.update { it.copy(activeShapeType = shapeType) }
    }

    fun setShapeFilled(filled: Boolean) {
        _uiState.update { it.copy(shapeFilled = filled) }
    }

    fun setShapeOpacity(opacity: Float) {
        _uiState.update { it.copy(shapeOpacity = opacity.coerceIn(0.25f, 1f)) }
    }

    fun toggleMoveAllMode(enabled: Boolean? = null) {
        _uiState.update { state ->
            val next = enabled ?: !state.isMoveAllMode
            state.copy(
                isMoveAllMode = next,
                activeTool = EditorTool.SELECT,
                selectedItemId = if (next) null else state.selectedItemId,
                isMovePanelExpanded = if (next) true else state.isMovePanelExpanded,
                isLayerVisible = true
            )
        }
    }

    fun toggleMovePanel() {
        _uiState.update { it.copy(isMovePanelExpanded = !it.isMovePanelExpanded) }
    }

    fun toggleLayerVisibility() {
        _uiState.update {
            it.copy(
                isLayerVisible = !it.isLayerVisible,
                selectedItemId = if (it.isLayerVisible) null else it.selectedItemId
            )
        }
    }

    fun selectItem(itemId: String?) {
        _uiState.update {
            it.copy(
                selectedItemId = itemId,
                isMoveAllMode = if (itemId != null) false else it.isMoveAllMode,
                isMovePanelExpanded = if (itemId == null && !it.isMoveAllMode) false else it.isMovePanelExpanded,
                activeTool = if (itemId != null) EditorTool.SELECT else it.activeTool
            )
        }
    }

    fun setDrawColor(color: Color) {
        _uiState.update {
            if (it.drawMode == DrawMode.HIGHLIGHTER) it.copy(highlighterColor = color)
            else it.copy(drawColor = color)
        }
    }

    fun setDrawStrokeWidth(widthPt: Float) {
        _uiState.update { it.copy(drawStrokeWidthPt = widthPt) }
    }

    fun addTextItem(
        text: String,
        normX: Float = 0.25f,
        normY: Float = 0.25f,
        fontSizePt: Float = 16f,
        font: OverlayFont = OverlayFont.SANS,
        color: Color = OverlayPalette.inkColors[0],
        hasWhiteBackground: Boolean = false
    ) {
        if (text.isBlank()) return
        val pageIdx = _uiState.value.currentPageIndex
        val newItem = OverlayItem.TextItem(
            pageIndex = pageIdx,
            text = text.trimEnd(),
            normX = normX.coerceIn(0.02f, 0.85f),
            normY = normY.coerceIn(0.02f, 0.90f),
            fontSizePt = fontSizePt,
            font = font,
            colorArgb = color.toArgb(),
            hasWhiteBackground = hasWhiteBackground
        )
        mutateItemsWithUndo { map ->
            val list = map[pageIdx].orEmpty() + newItem
            map + (pageIdx to list)
        }
        _uiState.update {
            it.copy(
                selectedItemId = newItem.id,
                activeTool = EditorTool.SELECT,
                isLayerVisible = true
            )
        }
    }

    fun updateTextItem(
        id: String,
        text: String? = null,
        fontSizePt: Float? = null,
        font: OverlayFont? = null,
        color: Color? = null,
        hasWhiteBackground: Boolean? = null,
        recordUndo: Boolean = true
    ) {
        val pageIdx = _uiState.value.currentPageIndex
        val updater: (Map<Int, List<OverlayItem>>) -> Map<Int, List<OverlayItem>> = { map ->
            val updatedList = map[pageIdx].orEmpty().map { item ->
                if (item is OverlayItem.TextItem && item.id == id) {
                    item.copy(
                        text = text ?: item.text,
                        fontSizePt = (fontSizePt ?: item.fontSizePt).coerceIn(8f, 64f),
                        font = font ?: item.font,
                        colorArgb = color?.toArgb() ?: item.colorArgb,
                        hasWhiteBackground = hasWhiteBackground ?: item.hasWhiteBackground
                    )
                } else item
            }
            map + (pageIdx to updatedList)
        }
        if (recordUndo) mutateItemsWithUndo(updater) else mutateItemsWithoutUndo(updater)
    }

    fun addImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = pdfEngine.prepareOverlayImage(uri)
            result.onSuccess { prepared ->
                val doc = _uiState.value.document ?: return@onSuccess
                val pageIdx = _uiState.value.currentPageIndex
                val pageMeta = doc.pages.getOrNull(pageIdx) ?: return@onSuccess
                val pageAspect = pageMeta.widthPt / max(1f, pageMeta.heightPt)

                val normW = 0.36f
                val normH = (normW * pageAspect / max(0.1f, prepared.aspectRatio)).coerceIn(0.06f, 0.7f)

                val item = OverlayItem.ImageItem(
                    pageIndex = pageIdx,
                    localFilePath = prepared.localPath,
                    centerX = 0.5f,
                    centerY = 0.5f,
                    normWidth = normW,
                    normHeight = normH
                )
                mutateItemsWithUndo { map ->
                    map + (pageIdx to (map[pageIdx].orEmpty() + item))
                }
                _uiState.update {
                    it.copy(
                        selectedItemId = item.id,
                        activeTool = EditorTool.SELECT,
                        isLayerVisible = true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_import_image))
                }
            }
        }
    }

    fun prepareCameraOutputFile(): File {
        val file = pdfEngine.createCameraCaptureFile()
        pendingCameraFile = file
        return file
    }

    fun onCameraCaptureSuccess() {
        val file = pendingCameraFile ?: return
        if (file.exists() && file.length() > 0L) {
            addImageFromUri(Uri.fromFile(file))
        }
        pendingCameraFile = null
    }

    fun toggleImageBackgroundRemoval(itemId: String) {
        val pageIdx = _uiState.value.currentPageIndex
        val target = _uiState.value.currentPageItems
            .firstOrNull { it.id == itemId } as? OverlayItem.ImageItem ?: return

        if (target.isBackgroundRemoved) {
            mutateItemsWithUndo { map ->
                val updated = map[pageIdx].orEmpty().map { item ->
                    if (item is OverlayItem.ImageItem && item.id == itemId) {
                        item.copy(
                            localFilePath = item.originalFilePath,
                            isBackgroundRemoved = false
                        )
                    } else item
                }
                map + (pageIdx to updated)
            }
        } else {
            viewModelScope.launch {
                val res = pdfEngine.removeLightBackgroundFromImage(target.originalFilePath)
                res.onSuccess { processedPath ->
                    mutateItemsWithUndo { map ->
                        val updated = map[pageIdx].orEmpty().map { item ->
                            if (item is OverlayItem.ImageItem && item.id == itemId) {
                                item.copy(
                                    localFilePath = processedPath,
                                    isBackgroundRemoved = true
                                )
                            } else item
                        }
                        map + (pageIdx to updated)
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_import_image))
                    }
                }
            }
        }
    }

    fun addDrawingStrokes(
        rawPageStrokes: List<List<NormPoint>>,
        color: Color,
        strokeWidthPt: Float,
        opacity: Float = 1f,
        isSignature: Boolean = false
    ) {
        val validStrokes = rawPageStrokes.filter { it.isNotEmpty() }
        if (validStrokes.isEmpty()) return

        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f

        for (stroke in validStrokes) {
            for (p in stroke) {
                minX = min(minX, p.x)
                maxX = max(maxX, p.x)
                minY = min(minY, p.y)
                maxY = max(maxY, p.y)
            }
        }

        val padX = 0.015f
        val padY = 0.012f
        val boxMinX = (minX - padX).coerceIn(0f, 0.96f)
        val boxMaxX = (maxX + padX).coerceIn(boxMinX + 0.04f, 1f)
        val boxMinY = (minY - padY).coerceIn(0f, 0.96f)
        val boxMaxY = (maxY + padY).coerceIn(boxMinY + 0.03f, 1f)

        val w = (boxMaxX - boxMinX).coerceAtLeast(0.04f)
        val h = (boxMaxY - boxMinY).coerceAtLeast(0.03f)
        val cx = (boxMinX + boxMaxX) / 2f
        val cy = (boxMinY + boxMaxY) / 2f

        val localStrokes = validStrokes.map { stroke ->
            NormStroke(
                points = stroke.map { pt ->
                    NormPoint(
                        x = ((pt.x - boxMinX) / w).coerceIn(0f, 1f),
                        y = ((pt.y - boxMinY) / h).coerceIn(0f, 1f)
                    )
                }
            )
        }

        val pageIdx = _uiState.value.currentPageIndex
        val item = OverlayItem.DrawingItem(
            pageIndex = pageIdx,
            strokes = localStrokes,
            centerX = cx,
            centerY = cy,
            normWidth = w,
            normHeight = h,
            colorArgb = color.toArgb(),
            strokeWidthPt = strokeWidthPt,
            opacity = opacity,
            isSignature = isSignature
        )

        mutateItemsWithUndo { map ->
            map + (pageIdx to (map[pageIdx].orEmpty() + item))
        }
        _uiState.update {
            it.copy(
                selectedItemId = item.id,
                activeTool = EditorTool.SELECT,
                isLayerVisible = true,
                lastSavedSignature = if (isSignature) rawPageStrokes else it.lastSavedSignature
            )
        }
    }

    fun reuseLastSignature() {
        val saved = _uiState.value.lastSavedSignature ?: return
        addDrawingStrokes(
            rawPageStrokes = saved,
            color = _uiState.value.drawColor,
            strokeWidthPt = 3.2f,
            opacity = 1f,
            isSignature = true
        )
    }

    fun addShapeItem(
        shapeType: GeometricShapeType = _uiState.value.activeShapeType,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        normWidth: Float = 0.28f,
        normHeight: Float = if (shapeType == GeometricShapeType.LINE) 0.04f else 0.16f,
        color: Color = _uiState.value.drawColor,
        strokeWidthPt: Float = _uiState.value.drawStrokeWidthPt,
        isFilled: Boolean = _uiState.value.shapeFilled,
        opacity: Float = _uiState.value.shapeOpacity
    ) {
        val pageIdx = _uiState.value.currentPageIndex
        val item = OverlayItem.ShapeItem(
            pageIndex = pageIdx,
            shapeType = shapeType,
            centerX = centerX.coerceIn(0.05f, 0.95f),
            centerY = centerY.coerceIn(0.05f, 0.95f),
            normWidth = normWidth.coerceIn(0.04f, 0.95f),
            normHeight = normHeight.coerceIn(0.03f, 0.95f),
            colorArgb = color.toArgb(),
            strokeWidthPt = strokeWidthPt,
            isFilled = isFilled,
            opacity = opacity
        )
        mutateItemsWithUndo { map ->
            map + (pageIdx to (map[pageIdx].orEmpty() + item))
        }
        _uiState.update {
            it.copy(
                selectedItemId = item.id,
                activeTool = EditorTool.SELECT,
                isLayerVisible = true
            )
        }
    }

    fun addQuickStamp(type: QuickStampType) {
        val pageIdx = _uiState.value.currentPageIndex
        val color = _uiState.value.drawColor.toArgb()

        val (strokes, w, h) = when (type) {
            QuickStampType.CHECK -> Triple(
                listOf(
                    NormStroke(
                        listOf(
                            NormPoint(0.12f, 0.54f),
                            NormPoint(0.40f, 0.84f),
                            NormPoint(0.88f, 0.16f)
                        )
                    )
                ),
                0.08f,
                0.06f
            )
            QuickStampType.CROSS -> Triple(
                listOf(
                    NormStroke(listOf(NormPoint(0.15f, 0.15f), NormPoint(0.85f, 0.85f))),
                    NormStroke(listOf(NormPoint(0.85f, 0.15f), NormPoint(0.15f, 0.85f)))
                ),
                0.07f,
                0.055f
            )
            QuickStampType.BOX -> Triple(
                listOf(
                    NormStroke(
                        listOf(
                            NormPoint(0.05f, 0.05f),
                            NormPoint(0.95f, 0.05f),
                            NormPoint(0.95f, 0.95f),
                            NormPoint(0.05f, 0.95f),
                            NormPoint(0.05f, 0.05f)
                        )
                    )
                ),
                0.22f,
                0.08f
            )
            QuickStampType.LINE -> Triple(
                listOf(
                    NormStroke(listOf(NormPoint(0.04f, 0.50f), NormPoint(0.96f, 0.50f)))
                ),
                0.30f,
                0.03f
            )
        }

        val item = OverlayItem.DrawingItem(
            pageIndex = pageIdx,
            strokes = strokes,
            centerX = 0.5f,
            centerY = 0.5f,
            normWidth = w,
            normHeight = h,
            colorArgb = color,
            strokeWidthPt = 2.8f,
            opacity = 1f
        )

        mutateItemsWithUndo { map ->
            map + (pageIdx to (map[pageIdx].orEmpty() + item))
        }
        _uiState.update {
            it.copy(
                selectedItemId = item.id,
                activeTool = EditorTool.SELECT,
                isLayerVisible = true
            )
        }
    }

    fun duplicateItem(id: String) {
        val pageIdx = _uiState.value.currentPageIndex
        val original = _uiState.value.currentPageItems.firstOrNull { it.id == id } ?: return
        val newId = UUID.randomUUID().toString()
        val copy: OverlayItem = when (original) {
            is OverlayItem.TextItem -> original.copy(
                id = newId,
                normX = (original.normX + 0.04f).coerceIn(0.02f, 0.90f),
                normY = (original.normY + 0.04f).coerceIn(0.02f, 0.92f)
            )
            is OverlayItem.ImageItem -> original.copy(
                id = newId,
                centerX = (original.centerX + 0.04f).coerceIn(0.05f, 0.95f),
                centerY = (original.centerY + 0.04f).coerceIn(0.05f, 0.95f)
            )
            is OverlayItem.DrawingItem -> original.copy(
                id = newId,
                centerX = (original.centerX + 0.04f).coerceIn(0.05f, 0.95f),
                centerY = (original.centerY + 0.04f).coerceIn(0.05f, 0.95f)
            )
            is OverlayItem.ShapeItem -> original.copy(
                id = newId,
                centerX = (original.centerX + 0.04f).coerceIn(0.05f, 0.95f),
                centerY = (original.centerY + 0.04f).coerceIn(0.05f, 0.95f)
            )
        }
        mutateItemsWithUndo { map ->
            map + (pageIdx to (map[pageIdx].orEmpty() + copy))
        }
        _uiState.update { it.copy(selectedItemId = newId) }
    }

    fun captureUndoCheckpoint() {
        pushUndoSnapshot(_uiState.value.overlayItemsByPage)
    }

    fun moveItem(id: String, deltaNormX: Float, deltaNormY: Float) {
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithoutUndo { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item.id != id) item else shiftItem(item, deltaNormX, deltaNormY)
            }
            map + (pageIdx to updated)
        }
    }

    fun moveAllItemsOnPage(deltaNormX: Float, deltaNormY: Float, recordUndo: Boolean = false) {
        val pageIdx = _uiState.value.currentPageIndex
        val updater: (Map<Int, List<OverlayItem>>) -> Map<Int, List<OverlayItem>> = { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                shiftItem(item, deltaNormX, deltaNormY)
            }
            map + (pageIdx to updated)
        }
        if (recordUndo) mutateItemsWithUndo(updater) else mutateItemsWithoutUndo(updater)
    }

    fun nudgeSelectedOrAll(deltaNormX: Float, deltaNormY: Float) {
        val state = _uiState.value
        val pageIdx = state.currentPageIndex
        if (state.isMoveAllMode || state.selectedItemId == null) {
            if (state.currentPageItems.isNotEmpty()) {
                moveAllItemsOnPage(deltaNormX, deltaNormY, recordUndo = true)
            }
        } else {
            val targetId = state.selectedItemId
            mutateItemsWithUndo { map ->
                val updated = map[pageIdx].orEmpty().map { item ->
                    if (item.id != targetId) item else shiftItem(item, deltaNormX, deltaNormY)
                }
                map + (pageIdx to updated)
            }
        }
    }

    fun centerSelectedItem(horizontal: Boolean = true, vertical: Boolean = false) {
        val targetId = _uiState.value.selectedItemId ?: return
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithUndo { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item.id != targetId) item
                else when (item) {
                    is OverlayItem.TextItem -> item.copy(
                        normX = if (horizontal) 0.40f else item.normX,
                        normY = if (vertical) 0.48f else item.normY
                    )
                    is OverlayItem.ImageItem -> item.copy(
                        centerX = if (horizontal) 0.50f else item.centerX,
                        centerY = if (vertical) 0.50f else item.centerY
                    )
                    is OverlayItem.DrawingItem -> item.copy(
                        centerX = if (horizontal) 0.50f else item.centerX,
                        centerY = if (vertical) 0.50f else item.centerY
                    )
                    is OverlayItem.ShapeItem -> item.copy(
                        centerX = if (horizontal) 0.50f else item.centerX,
                        centerY = if (vertical) 0.50f else item.centerY
                    )
                }
            }
            map + (pageIdx to updated)
        }
    }

    fun bringItemToFront(id: String) {
        val pageIdx = _uiState.value.currentPageIndex
        val list = _uiState.value.overlayItemsByPage[pageIdx].orEmpty()
        val target = list.firstOrNull { it.id == id } ?: return
        if (list.lastOrNull()?.id == id) return
        mutateItemsWithUndo { map ->
            val reordered = map[pageIdx].orEmpty().filterNot { it.id == id } + target
            map + (pageIdx to reordered)
        }
    }

    private fun shiftItem(item: OverlayItem, dx: Float, dy: Float): OverlayItem = when (item) {
        is OverlayItem.TextItem -> item.copy(
            normX = (item.normX + dx).coerceIn(0.01f, 0.94f),
            normY = (item.normY + dy).coerceIn(0.01f, 0.96f)
        )
        is OverlayItem.ImageItem -> item.copy(
            centerX = (item.centerX + dx).coerceIn(0.03f, 0.97f),
            centerY = (item.centerY + dy).coerceIn(0.03f, 0.97f)
        )
        is OverlayItem.DrawingItem -> item.copy(
            centerX = (item.centerX + dx).coerceIn(0.03f, 0.97f),
            centerY = (item.centerY + dy).coerceIn(0.03f, 0.97f)
        )
        is OverlayItem.ShapeItem -> item.copy(
            centerX = (item.centerX + dx).coerceIn(0.03f, 0.97f),
            centerY = (item.centerY + dy).coerceIn(0.03f, 0.97f)
        )
    }

    fun scaleItem(id: String, scaleFactor: Float) {
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithoutUndo { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item.id != id) item
                else when (item) {
                    is OverlayItem.TextItem -> item.copy(
                        fontSizePt = (item.fontSizePt * scaleFactor).coerceIn(8f, 64f)
                    )
                    is OverlayItem.ImageItem -> {
                        val factor = scaleFactor.coerceIn(0.85f, 1.15f)
                        val newW = (item.normWidth * factor).coerceIn(0.06f, 0.95f)
                        val ratio = if (item.normWidth > 0f) item.normHeight / item.normWidth else 1f
                        val newH = (newW * ratio).coerceIn(0.04f, 0.95f)
                        item.copy(normWidth = newW, normHeight = newH)
                    }
                    is OverlayItem.DrawingItem -> {
                        val factor = scaleFactor.coerceIn(0.85f, 1.15f)
                        val newW = (item.normWidth * factor).coerceIn(0.04f, 0.95f)
                        val ratio = if (item.normWidth > 0f) item.normHeight / item.normWidth else 1f
                        val newH = (newW * ratio).coerceIn(0.02f, 0.95f)
                        item.copy(normWidth = newW, normHeight = newH)
                    }
                    is OverlayItem.ShapeItem -> {
                        val factor = scaleFactor.coerceIn(0.85f, 1.15f)
                        val newW = (item.normWidth * factor).coerceIn(0.04f, 0.95f)
                        val ratio = if (item.normWidth > 0f) item.normHeight / item.normWidth else 1f
                        val newH = (newW * ratio).coerceIn(0.02f, 0.95f)
                        item.copy(normWidth = newW, normHeight = newH)
                    }
                }
            }
            map + (pageIdx to updated)
        }
    }

    fun rotateItem(id: String, deltaDeg: Float, recordUndo: Boolean = false) {
        val pageIdx = _uiState.value.currentPageIndex
        val updater: (Map<Int, List<OverlayItem>>) -> Map<Int, List<OverlayItem>> = { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item.id != id) item
                else when (item) {
                    is OverlayItem.TextItem -> item.copy(rotationDeg = (item.rotationDeg + deltaDeg) % 360f)
                    is OverlayItem.ImageItem -> item.copy(rotationDeg = (item.rotationDeg + deltaDeg) % 360f)
                    is OverlayItem.DrawingItem -> item.copy(rotationDeg = (item.rotationDeg + deltaDeg) % 360f)
                    is OverlayItem.ShapeItem -> item.copy(rotationDeg = (item.rotationDeg + deltaDeg) % 360f)
                }
            }
            map + (pageIdx to updated)
        }
        if (recordUndo) mutateItemsWithUndo(updater) else mutateItemsWithoutUndo(updater)
    }

    fun updateDrawingStyle(id: String, color: Color? = null, strokeWidthPt: Float? = null) {
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithUndo { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item is OverlayItem.DrawingItem && item.id == id) {
                    item.copy(
                        colorArgb = color?.toArgb() ?: item.colorArgb,
                        strokeWidthPt = strokeWidthPt ?: item.strokeWidthPt
                    )
                } else item
            }
            map + (pageIdx to updated)
        }
    }

    fun updateShapeStyle(
        id: String,
        color: Color? = null,
        isFilled: Boolean? = null,
        strokeWidthPt: Float? = null,
        opacity: Float? = null
    ) {
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithUndo { map ->
            val updated = map[pageIdx].orEmpty().map { item ->
                if (item is OverlayItem.ShapeItem && item.id == id) {
                    item.copy(
                        colorArgb = color?.toArgb() ?: item.colorArgb,
                        isFilled = isFilled ?: item.isFilled,
                        strokeWidthPt = strokeWidthPt ?: item.strokeWidthPt,
                        opacity = opacity ?: item.opacity
                    )
                } else item
            }
            map + (pageIdx to updated)
        }
    }

    fun deleteItem(id: String) {
        val pageIdx = _uiState.value.currentPageIndex
        mutateItemsWithUndo { map ->
            val updated = map[pageIdx].orEmpty().filterNot { it.id == id }
            if (updated.isEmpty()) map - pageIdx else map + (pageIdx to updated)
        }
        _uiState.update {
            if (it.selectedItemId == id) it.copy(selectedItemId = null) else it
        }
    }

    fun clearAllOverlayLayers() {
        if (_uiState.value.overlayItemsByPage.isEmpty()) return
        mutateItemsWithUndo { emptyMap() }
        _uiState.update { it.copy(selectedItemId = null, isLayerVisible = true) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_uiState.value.overlayItemsByPage)
        _uiState.update {
            it.copy(
                overlayItemsByPage = previous,
                selectedItemId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_uiState.value.overlayItemsByPage)
        _uiState.update {
            it.copy(
                overlayItemsByPage = next,
                selectedItemId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun generatePreviewPdf(onReady: () -> Unit) {
        val doc = _uiState.value.document ?: return
        val items = _uiState.value.overlayItemsByPage
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingPreview = true,
                    errorMessage = null,
                    savedSuccessMessage = null
                )
            }
            val result = exporter.exportToPreviewFile(doc.sourceCachePath, items)
            result.onSuccess { outFile ->
                pdfEngine.clearBitmapCache("preview")
                val initialPage = _uiState.value.currentPageIndex.coerceIn(0, doc.pageCount - 1)
                val previewBmp = pdfEngine.renderPage(
                    pdfPath = outFile.absolutePath,
                    pageIndex = initialPage,
                    targetWidthPx = 1500,
                    cacheKeyPrefix = "preview"
                )
                _uiState.update {
                    it.copy(
                        isGeneratingPreview = false,
                        previewPdfPath = outFile.absolutePath,
                        previewPageIndex = initialPage,
                        previewPageBitmap = previewBmp
                    )
                }
                onReady()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingPreview = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_generate_preview)
                    )
                }
            }
        }
    }

    fun selectPreviewPage(index: Int) {
        val doc = _uiState.value.document ?: return
        val previewPath = _uiState.value.previewPdfPath ?: return
        val clamped = index.coerceIn(0, doc.pageCount - 1)
        _uiState.update { it.copy(previewPageIndex = clamped) }
        viewModelScope.launch {
            val bmp = pdfEngine.renderPage(
                pdfPath = previewPath,
                pageIndex = clamped,
                targetWidthPx = 1500,
                cacheKeyPrefix = "preview"
            )
            _uiState.update {
                if (it.previewPageIndex == clamped) it.copy(previewPageBitmap = bmp) else it
            }
        }
    }

    fun savePreviewPdfToUri(destUri: Uri) {
        val previewPath = _uiState.value.previewPdfPath ?: return
        val previewFile = File(previewPath)
        if (!previewFile.exists()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val result = copyFileToUri(previewFile, destUri)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        savedSuccessMessage = getApplication<Application>().getString(R.string.msg_saved_success)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun savePreviewPdfToDownloads(onNeedSafFallback: (suggestedName: String) -> Unit) {
        val doc = _uiState.value.document ?: return
        val previewPath = _uiState.value.previewPdfPath ?: return
        val previewFile = File(previewPath)
        if (!previewFile.exists()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val res = pdfEngine.saveFileToDownloads(
                sourceFile = previewFile,
                desiredName = doc.suggestedOutputName,
                mimeType = "application/pdf"
            )
            res.onSuccess { info ->
                if (info.requiresSafPicker) {
                    _uiState.update { it.copy(isSavingOutput = false) }
                    onNeedSafFallback(info.savedDisplayName)
                } else {
                    _uiState.update {
                        it.copy(
                            isSavingOutput = false,
                            savedSuccessMessage = getApplication<Application>().getString(
                                R.string.save_downloads_success,
                                info.savedDisplayName
                            )
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    // --- Page Organizer & Thumbnails (100% Offline) ---

    fun loadPageThumbnails() {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val loaded = mutableMapOf<Int, Bitmap>()
            for (i in 0 until doc.pageCount) {
                val thumb = runCatching {
                    pdfEngine.renderPage(
                        pdfPath = doc.sourceCachePath,
                        pageIndex = i,
                        targetWidthPx = 320,
                        cacheKeyPrefix = "thumb"
                    )
                }.getOrNull()
                if (thumb != null) {
                    loaded[i] = thumb
                    if (_uiState.value.document?.sourceCachePath == doc.sourceCachePath) {
                        _uiState.update { it.copy(pageThumbnails = loaded.toMap()) }
                    }
                }
            }
        }
    }

    fun rotateDocumentPage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        if (pageIndex !in 0 until doc.pageCount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOrganizingPages = true, errorMessage = null) }
            val res = pdfEngine.rotatePdfPage(doc.sourceCachePath, pageIndex, 90)
            res.onSuccess { updatedPages ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        document = doc.copy(pages = updatedPages),
                        pageThumbnails = emptyMap(),
                        previewPdfPath = null,
                        previewPageBitmap = null
                    )
                }
                loadPageBitmap(doc.sourceCachePath, _uiState.value.currentPageIndex)
                loadPageThumbnails()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun moveDocumentPage(fromIndex: Int, toIndex: Int) {
        val doc = _uiState.value.document ?: return
        if (fromIndex !in 0 until doc.pageCount || toIndex !in 0 until doc.pageCount || fromIndex == toIndex) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOrganizingPages = true, errorMessage = null) }
            val res = pdfEngine.movePdfPage(doc.sourceCachePath, fromIndex, toIndex)
            res.onSuccess { updatedPages ->
                undoStack.clear()
                redoStack.clear()

                val remappedOverlays = remapOverlaysAfterPageMove(
                    itemsByPage = _uiState.value.overlayItemsByPage,
                    pageCount = doc.pageCount,
                    fromIndex = fromIndex,
                    toIndex = toIndex
                )
                val currentIdx = _uiState.value.currentPageIndex
                val nextCurrentIdx = when {
                    currentIdx == fromIndex -> toIndex
                    fromIndex < toIndex && currentIdx in (fromIndex + 1)..toIndex -> currentIdx - 1
                    fromIndex > toIndex && currentIdx in toIndex until fromIndex -> currentIdx + 1
                    else -> currentIdx
                }.coerceIn(0, updatedPages.lastIndex)

                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        document = doc.copy(pages = updatedPages),
                        currentPageIndex = nextCurrentIdx,
                        selectedItemId = null,
                        overlayItemsByPage = remappedOverlays,
                        pageThumbnails = emptyMap(),
                        previewPdfPath = null,
                        previewPageBitmap = null,
                        canUndo = false,
                        canRedo = false
                    )
                }
                loadPageBitmap(doc.sourceCachePath, nextCurrentIdx)
                loadPageThumbnails()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun deleteDocumentPage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        if (doc.pageCount <= 1 || pageIndex !in 0 until doc.pageCount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOrganizingPages = true, errorMessage = null) }
            val res = pdfEngine.deletePdfPage(doc.sourceCachePath, pageIndex)
            res.onSuccess { updatedPages ->
                undoStack.clear()
                redoStack.clear()

                val shiftedOverlays = remapOverlaysAfterPageDelete(
                    itemsByPage = _uiState.value.overlayItemsByPage,
                    deletedIndex = pageIndex
                )
                val nextCurrentIdx = _uiState.value.currentPageIndex
                    .let { cur -> if (cur > pageIndex) cur - 1 else cur }
                    .coerceIn(0, updatedPages.lastIndex)

                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        document = doc.copy(pages = updatedPages),
                        currentPageIndex = nextCurrentIdx,
                        selectedItemId = null,
                        overlayItemsByPage = shiftedOverlays,
                        pageThumbnails = emptyMap(),
                        previewPdfPath = null,
                        previewPageBitmap = null,
                        canUndo = false,
                        canRedo = false
                    )
                }
                loadPageBitmap(doc.sourceCachePath, nextCurrentIdx)
                loadPageThumbnails()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun appendPdfToOpenDocument(uri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOrganizingPages = true, errorMessage = null) }
            val res = pdfEngine.appendPdfToWorkingFile(doc.sourceCachePath, uri)
            res.onSuccess { updatedPages ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        document = doc.copy(pages = updatedPages),
                        pageThumbnails = emptyMap(),
                        previewPdfPath = null,
                        previewPageBitmap = null
                    )
                }
                loadPageThumbnails()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOrganizingPages = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    private fun remapOverlaysAfterPageMove(
        itemsByPage: Map<Int, List<OverlayItem>>,
        pageCount: Int,
        fromIndex: Int,
        toIndex: Int
    ): Map<Int, List<OverlayItem>> {
        val order = (0 until pageCount).toMutableList()
        val moved = order.removeAt(fromIndex)
        order.add(toIndex, moved)

        val result = mutableMapOf<Int, List<OverlayItem>>()
        order.forEachIndexed { newIdx, oldIdx ->
            val items = itemsByPage[oldIdx].orEmpty().map { updateItemPageIndex(it, newIdx) }
            if (items.isNotEmpty()) {
                result[newIdx] = items
            }
        }
        return result
    }

    private fun remapOverlaysAfterPageDelete(
        itemsByPage: Map<Int, List<OverlayItem>>,
        deletedIndex: Int
    ): Map<Int, List<OverlayItem>> {
        val result = mutableMapOf<Int, List<OverlayItem>>()
        for ((idx, list) in itemsByPage) {
            when {
                idx < deletedIndex -> if (list.isNotEmpty()) result[idx] = list
                idx > deletedIndex -> {
                    val newIdx = idx - 1
                    val updated = list.map { updateItemPageIndex(it, newIdx) }
                    if (updated.isNotEmpty()) result[newIdx] = updated
                }
            }
        }
        return result
    }

    private fun updateItemPageIndex(item: OverlayItem, newPageIndex: Int): OverlayItem = when (item) {
        is OverlayItem.TextItem -> item.copy(pageIndex = newPageIndex)
        is OverlayItem.ImageItem -> item.copy(pageIndex = newPageIndex)
        is OverlayItem.DrawingItem -> item.copy(pageIndex = newPageIndex)
        is OverlayItem.ShapeItem -> item.copy(pageIndex = newPageIndex)
    }

    // --- PDF <-> Image Converter & Testing Actions ---

    fun convertExternalPdfUriToImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isConverting = true, converterStatusMessage = null, errorMessage = null)
            }
            val copied = pdfEngine.copyExternalPdfForConversion(uri)
            copied.onSuccess { (file, name) ->
                pdfEngine.clearBitmapCache("png_export")
                val exported = pdfEngine.exportPdfPageAsPng(file.absolutePath, name, 0)
                exported.onSuccess { preview ->
                    _uiState.update { it.copy(isConverting = false, pdfToImagePreview = preview) }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isConverting = false,
                            errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun convertCurrentEditedPdfPageToImage(pageIndex: Int, usePreviewPdf: Boolean) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isConverting = true, converterStatusMessage = null, errorMessage = null)
            }
            val targetPdfPath = if (usePreviewPdf && _uiState.value.previewPdfPath != null) {
                _uiState.value.previewPdfPath!!
            } else {
                val exported = exporter.exportToPreviewFile(doc.sourceCachePath, _uiState.value.overlayItemsByPage)
                exported.getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            isConverting = false,
                            errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_generate_preview)
                        )
                    }
                    return@launch
                }.absolutePath
            }

            pdfEngine.clearBitmapCache("png_export")
            val res = pdfEngine.exportPdfPageAsPng(targetPdfPath, doc.displayName, pageIndex)
            res.onSuccess { preview ->
                _uiState.update { it.copy(isConverting = false, pdfToImagePreview = preview) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_generate_preview)
                    )
                }
            }
        }
    }

    fun selectPdfToImagePage(pageIndex: Int) {
        val current = _uiState.value.pdfToImagePreview ?: return
        val clamped = pageIndex.coerceIn(0, current.pageCount - 1)
        if (clamped == current.currentPageIndex) return

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true) }
            val res = pdfEngine.exportPdfPageAsPng(current.pdfPath, current.displayName, clamped)
            res.onSuccess { updated ->
                _uiState.update { it.copy(isConverting = false, pdfToImagePreview = updated) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isConverting = false, errorMessage = err.message)
                }
            }
        }
    }

    fun saveConvertedPngToUri(destUri: Uri) {
        val pngFile = _uiState.value.pdfToImagePreview?.currentPngFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true) }
            val res = copyFileToUri(pngFile, destUri)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        converterStatusMessage = getApplication<Application>().getString(R.string.converter_saved_png_msg)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun saveConvertedPngToDownloads(onNeedSafFallback: (suggestedName: String) -> Unit) {
        val pngFile = _uiState.value.pdfToImagePreview?.currentPngFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val res = pdfEngine.saveFileToDownloads(
                sourceFile = pngFile,
                desiredName = pngFile.name,
                mimeType = "image/png"
            )
            res.onSuccess { info ->
                if (info.requiresSafPicker) {
                    _uiState.update { it.copy(isSavingOutput = false) }
                    onNeedSafFallback(info.savedDisplayName)
                } else {
                    _uiState.update {
                        it.copy(
                            isSavingOutput = false,
                            converterStatusMessage = getApplication<Application>().getString(
                                R.string.save_downloads_success,
                                info.savedDisplayName
                            )
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun clearPdfToImagePreview() {
        _uiState.update { it.copy(pdfToImagePreview = null, converterStatusMessage = null) }
    }

    fun convertSelectedImagesToPdf(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isConverting = true, converterStatusMessage = null, errorMessage = null)
            }
            val res = pdfEngine.convertImagesToPdf(uris)
            res.onSuccess { result ->
                _uiState.update { it.copy(isConverting = false, imageToPdfResult = result) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_import_image)
                    )
                }
            }
        }
    }

    fun saveConvertedPdfToUri(destUri: Uri) {
        val pdfFile = _uiState.value.imageToPdfResult?.pdfFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true) }
            val res = copyFileToUri(pdfFile, destUri)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        converterStatusMessage = getApplication<Application>().getString(R.string.converter_saved_pdf_msg)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun saveConvertedPdfToDownloads(onNeedSafFallback: (suggestedName: String) -> Unit) {
        val result = _uiState.value.imageToPdfResult ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val res = pdfEngine.saveFileToDownloads(
                sourceFile = result.pdfFile,
                desiredName = result.displayName,
                mimeType = "application/pdf"
            )
            res.onSuccess { info ->
                if (info.requiresSafPicker) {
                    _uiState.update { it.copy(isSavingOutput = false) }
                    onNeedSafFallback(info.savedDisplayName)
                } else {
                    _uiState.update {
                        it.copy(
                            isSavingOutput = false,
                            converterStatusMessage = getApplication<Application>().getString(
                                R.string.save_downloads_success,
                                info.savedDisplayName
                            )
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun openConvertedPdfInEditor(onOpened: () -> Unit) {
        val result = _uiState.value.imageToPdfResult ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDocument = true,
                    imageToPdfResult = null,
                    imagePageDrafts = emptyList(),
                    converterStatusMessage = null
                )
            }
            val loaded = pdfEngine.importLocalPdfToActive(result.pdfFile, result.displayName)
            loaded.onSuccess { meta ->
                applyOpenedDocument(meta, onOpened)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDocument = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun clearImageToPdfResult() {
        _uiState.update {
            it.copy(
                imageToPdfResult = null,
                imagePageDrafts = emptyList(),
                converterStatusMessage = null
            )
        }
    }

    // --- Editable & Orderable Image-to-PDF Staging ---

    fun startImageToPdfDrafts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConverting = true,
                    imageToPdfResult = null,
                    converterStatusMessage = null,
                    errorMessage = null
                )
            }
            val loaded = mutableListOf<ImagePageDraft>()
            var lastError: Throwable? = null
            for (uri in uris) {
                pdfEngine.prepareImageDraft(uri)
                    .onSuccess { loaded.add(it) }
                    .onFailure { lastError = it }
            }
            if (loaded.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = lastError?.message
                            ?: getApplication<Application>().getString(R.string.err_import_image)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        imagePageDrafts = loaded
                    )
                }
            }
        }
    }

    fun appendImageToPdfDrafts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }
            val extra = mutableListOf<ImagePageDraft>()
            var lastError: Throwable? = null
            for (uri in uris) {
                pdfEngine.prepareImageDraft(uri)
                    .onSuccess { extra.add(it) }
                    .onFailure { lastError = it }
            }
            _uiState.update {
                it.copy(
                    isConverting = false,
                    imagePageDrafts = it.imagePageDrafts + extra,
                    errorMessage = if (extra.isEmpty()) {
                        lastError?.message ?: getApplication<Application>().getString(R.string.err_import_image)
                    } else null
                )
            }
        }
    }

    fun moveImageDraft(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.imagePageDrafts.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            state.copy(imagePageDrafts = list)
        }
    }

    fun rotateImageDraft(id: String) {
        _uiState.update { state ->
            val updated = state.imagePageDrafts.map { draft ->
                if (draft.id == id) {
                    draft.copy(rotationDeg = (draft.rotationDeg + 90) % 360)
                } else draft
            }
            state.copy(imagePageDrafts = updated)
        }
    }

    fun toggleImageDraftBgRemoval(id: String) {
        val target = _uiState.value.imagePageDrafts.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }
            val res = pdfEngine.toggleImageDraftBgRemoval(target)
            res.onSuccess { updatedDraft ->
                _uiState.update { state ->
                    state.copy(
                        isConverting = false,
                        imagePageDrafts = state.imagePageDrafts.map {
                            if (it.id == id) updatedDraft else it
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_import_image)
                    )
                }
            }
        }
    }

    fun removeImageDraft(id: String) {
        _uiState.update { state ->
            state.copy(imagePageDrafts = state.imagePageDrafts.filterNot { it.id == id })
        }
    }

    fun generatePdfFromImageDrafts() {
        val drafts = _uiState.value.imagePageDrafts
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, converterStatusMessage = null, errorMessage = null) }
            val res = pdfEngine.createPdfFromImageDrafts(drafts)
            res.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        imageToPdfResult = result
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_import_image)
                    )
                }
            }
        }
    }

    fun clearImageDrafts() {
        _uiState.update {
            it.copy(
                imagePageDrafts = emptyList(),
                imageToPdfResult = null,
                converterStatusMessage = null
            )
        }
    }

    // --- Merge Multiple PDFs (100% Offline) ---

    fun openMergeDialog() {
        _uiState.update {
            it.copy(
                isMergeDialogOpen = true,
                converterStatusMessage = null,
                errorMessage = null
            )
        }
    }

    fun addMergePdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMergeDialogOpen = true,
                    isMergingPdfs = true,
                    mergedPdfResult = null,
                    converterStatusMessage = null,
                    errorMessage = null
                )
            }
            val added = mutableListOf<MergePdfSource>()
            var lastError: Throwable? = null
            for (uri in uris) {
                pdfEngine.inspectLocalPdfForMerge(uri)
                    .onSuccess { added.add(it) }
                    .onFailure { lastError = it }
            }
            _uiState.update { state ->
                state.copy(
                    isMergingPdfs = false,
                    mergeSources = state.mergeSources + added,
                    errorMessage = if (added.isEmpty()) {
                        lastError?.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    } else null
                )
            }
        }
    }

    fun moveMergeSource(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.mergeSources.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            state.copy(mergeSources = list, mergedPdfResult = null, converterStatusMessage = null)
        }
    }

    fun removeMergeSource(id: String) {
        _uiState.update { state ->
            state.copy(
                mergeSources = state.mergeSources.filterNot { it.id == id },
                mergedPdfResult = null,
                converterStatusMessage = null
            )
        }
    }

    fun executeMergePdfs(outputName: String = "mesclado_3ditdoc.pdf") {
        val sources = _uiState.value.mergeSources
        if (sources.size < 2) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.merge_need_two))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isMergingPdfs = true, converterStatusMessage = null, errorMessage = null)
            }
            val res = pdfEngine.mergePdfFiles(
                files = sources.map { File(it.localPath) },
                outputName = outputName
            )
            res.onSuccess { merged ->
                _uiState.update {
                    it.copy(
                        isMergingPdfs = false,
                        mergedPdfResult = merged
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isMergingPdfs = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun saveMergedPdfToDownloads(onNeedSafFallback: (suggestedName: String) -> Unit) {
        val result = _uiState.value.mergedPdfResult ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val res = pdfEngine.saveFileToDownloads(
                sourceFile = result.pdfFile,
                desiredName = result.displayName,
                mimeType = "application/pdf"
            )
            res.onSuccess { info ->
                if (info.requiresSafPicker) {
                    _uiState.update { it.copy(isSavingOutput = false) }
                    onNeedSafFallback(info.savedDisplayName)
                } else {
                    _uiState.update {
                        it.copy(
                            isSavingOutput = false,
                            converterStatusMessage = getApplication<Application>().getString(
                                R.string.save_downloads_success,
                                info.savedDisplayName
                            )
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun saveMergedPdfToUri(destUri: Uri) {
        val pdfFile = _uiState.value.mergedPdfResult?.pdfFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOutput = true, errorMessage = null) }
            val res = copyFileToUri(pdfFile, destUri)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        converterStatusMessage = getApplication<Application>().getString(R.string.merge_saved_msg)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingOutput = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_save_file)
                    )
                }
            }
        }
    }

    fun openMergedPdfInEditor(onOpened: () -> Unit) {
        val result = _uiState.value.mergedPdfResult ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDocument = true,
                    isMergeDialogOpen = false,
                    mergeSources = emptyList(),
                    mergedPdfResult = null,
                    converterStatusMessage = null
                )
            }
            val loaded = pdfEngine.importLocalPdfToActive(result.pdfFile, result.displayName)
            loaded.onSuccess { meta ->
                applyOpenedDocument(meta, onOpened)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDocument = false,
                        errorMessage = err.message ?: getApplication<Application>().getString(R.string.err_open_pdf)
                    )
                }
            }
        }
    }

    fun clearMergeState() {
        _uiState.update {
            it.copy(
                isMergeDialogOpen = false,
                mergeSources = emptyList(),
                mergedPdfResult = null,
                isMergingPdfs = false,
                converterStatusMessage = null
            )
        }
    }

    private suspend fun copyFileToUri(file: File, destUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                } ?: error(getApplication<Application>().getString(R.string.err_write_output))
                Unit
            }
        }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissSavedMessage() {
        _uiState.update { it.copy(savedSuccessMessage = null) }
    }

    private fun pushUndoSnapshot(snapshot: Map<Int, List<OverlayItem>>) {
        if (undoStack.size >= maxHistory) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    private fun mutateItemsWithUndo(transform: (Map<Int, List<OverlayItem>>) -> Map<Int, List<OverlayItem>>) {
        val current = _uiState.value.overlayItemsByPage
        pushUndoSnapshot(current)
        val next = transform(current)
        _uiState.update {
            it.copy(
                overlayItemsByPage = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    private fun mutateItemsWithoutUndo(transform: (Map<Int, List<OverlayItem>>) -> Map<Int, List<OverlayItem>>) {
        val next = transform(_uiState.value.overlayItemsByPage)
        _uiState.update {
            it.copy(
                overlayItemsByPage = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }
}
