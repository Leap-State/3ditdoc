package com.example.model

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.R
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.CursiveOverlayFontFamily
import com.example.ui.theme.MonoOverlayFontFamily
import com.example.ui.theme.SerifOverlayFontFamily
import java.util.UUID

enum class OverlayFont(
    @param:StringRes val displayNameResId: Int,
    val composeFamily: FontFamily,
    @param:FontRes val fontResId: Int
) {
    SANS(R.string.font_sans, BodyFontFamily, R.font.inter),
    SERIF(R.string.font_serif, SerifOverlayFontFamily, R.font.merriweather),
    MONO(R.string.font_mono, MonoOverlayFontFamily, R.font.jetbrains_mono),
    CURSIVE(R.string.font_cursive, CursiveOverlayFontFamily, R.font.caveat)
}

enum class EditorTool {
    SELECT,
    TEXT,
    DRAW
}

enum class DrawMode {
    PEN,
    HIGHLIGHTER,
    SHAPES
}

enum class GeometricShapeType(
    @param:StringRes val labelResId: Int
) {
    RECTANGLE(R.string.shape_rectangle),
    CIRCLE(R.string.shape_circle),
    TRIANGLE(R.string.shape_triangle),
    ARROW(R.string.shape_arrow),
    LINE(R.string.shape_line),
    DIAMOND(R.string.shape_diamond),
    STAR(R.string.shape_star)
}

enum class QuickStampType {
    CHECK,
    CROSS,
    BOX,
    LINE
}

enum class LegalTab {
    TERMS,
    PRIVACY,
    ABOUT
}

data class NormPoint(
    val x: Float,
    val y: Float
)

data class NormStroke(
    val points: List<NormPoint>
)

sealed interface OverlayItem {
    val id: String
    val pageIndex: Int
    val centerX: Float
    val centerY: Float
    val rotationDeg: Float

    data class TextItem(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val text: String,
        val normX: Float,
        val normY: Float,
        val fontSizePt: Float = 16f,
        val font: OverlayFont = OverlayFont.SANS,
        val colorArgb: Int = 0xFF0F172A.toInt(),
        val hasWhiteBackground: Boolean = false,
        override val rotationDeg: Float = 0f
    ) : OverlayItem {
        override val centerX: Float get() = normX
        override val centerY: Float get() = normY
    }

    data class ImageItem(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val localFilePath: String,
        val originalFilePath: String = localFilePath,
        val isBackgroundRemoved: Boolean = false,
        override val centerX: Float,
        override val centerY: Float,
        val normWidth: Float,
        val normHeight: Float,
        override val rotationDeg: Float = 0f
    ) : OverlayItem

    data class DrawingItem(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val strokes: List<NormStroke>,
        override val centerX: Float,
        override val centerY: Float,
        val normWidth: Float,
        val normHeight: Float,
        val colorArgb: Int,
        val strokeWidthPt: Float,
        val opacity: Float = 1f,
        val isSignature: Boolean = false,
        override val rotationDeg: Float = 0f
    ) : OverlayItem

    data class ShapeItem(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val shapeType: GeometricShapeType,
        override val centerX: Float,
        override val centerY: Float,
        val normWidth: Float,
        val normHeight: Float,
        val colorArgb: Int,
        val strokeWidthPt: Float = 2.6f,
        val isFilled: Boolean = false,
        val opacity: Float = 1f,
        override val rotationDeg: Float = 0f
    ) : OverlayItem
}

data class PdfPageMeta(
    val index: Int,
    val widthPt: Float,
    val heightPt: Float
)

data class PdfDocumentMeta(
    val displayName: String,
    val suggestedOutputName: String,
    val sourceCachePath: String,
    val pages: List<PdfPageMeta>
) {
    val pageCount: Int get() = pages.size
}

data class MergePdfSource(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val displayName: String,
    val pageCount: Int,
    val previewBitmap: android.graphics.Bitmap? = null
)

data class ImagePageDraft(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val localPath: String = originalPath,
    val rotationDeg: Int = 0,
    val isBackgroundRemoved: Boolean = false,
    val aspectRatio: Float = 1f,
    val previewBitmap: android.graphics.Bitmap? = null
)

object OverlayPalette {
    val inkColors: List<Color> = listOf(
        Color(0xFF0F172A), // Preto Tinta
        Color(0xFF1E40AF), // Azul Caneta
        Color(0xFFDC2626), // Vermelho Carimbo
        Color(0xFF15803D), // Verde
        Color(0xFF7C3AED), // Roxo
        Color(0xFFFFFFFF)  // Branco
    )

    val highlighterColors: List<Color> = listOf(
        Color(0xFFFACC15), // Amarelo Marca-texto
        Color(0xFF4ADE80), // Verde Limão
        Color(0xFF38BDF8), // Azul Claro
        Color(0xFFF472B6), // Rosa
        Color(0xFFFB923C)  // Laranja
    )
}
