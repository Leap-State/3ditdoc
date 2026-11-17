package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import com.example.R
import com.example.model.OverlayFont
import com.example.model.OverlayItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import com.example.model.GeometricShapeType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PdfOverlayExporter(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun exportToPreviewFile(
        sourcePdfPath: String,
        itemsByPage: Map<Int, List<OverlayItem>>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, "preview_export.pdf")
            val sourceFile = File(sourcePdfPath)
            if (!sourceFile.exists()) error(context.getString(R.string.err_source_missing))

            PDDocument.load(sourceFile).use { document ->
                val fontCache = mutableMapOf<OverlayFont, PDType0Font>()

                for ((pageIndex, items) in itemsByPage) {
                    if (items.isEmpty()) continue
                    if (pageIndex !in 0 until document.numberOfPages) continue

                    val page = document.getPage(pageIndex)
                    writeOverlayLayerToPage(document, page, items, fontCache)
                }

                document.save(outFile)
            }
            outFile
        }
    }

    private fun writeOverlayLayerToPage(
        document: PDDocument,
        page: PDPage,
        items: List<OverlayItem>,
        fontCache: MutableMap<OverlayFont, PDType0Font>
    ) {
        val cropBox = page.cropBox ?: page.mediaBox
        val llx = cropBox.lowerLeftX
        val lly = cropBox.lowerLeftY
        val cw = cropBox.width
        val ch = cropBox.height
        val rotation = ((page.rotation % 360) + 360) % 360

        val visualW: Float
        val visualH: Float
        val visualToPdfMatrix: Matrix

        when (rotation) {
            90 -> {
                visualW = ch
                visualH = cw
                visualToPdfMatrix = Matrix(0f, 1f, -1f, 0f, llx + cw, lly)
            }
            180 -> {
                visualW = cw
                visualH = ch
                visualToPdfMatrix = Matrix(-1f, 0f, 0f, -1f, llx + cw, lly + ch)
            }
            270 -> {
                visualW = ch
                visualH = cw
                visualToPdfMatrix = Matrix(0f, -1f, 1f, 0f, llx, lly + ch)
            }
            else -> {
                visualW = cw
                visualH = ch
                visualToPdfMatrix = Matrix(1f, 0f, 0f, 1f, llx, lly)
            }
        }

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        ).use { stream ->
            stream.saveGraphicsState()
            stream.transform(visualToPdfMatrix)

            for (item in items) {
                when (item) {
                    is OverlayItem.TextItem -> drawTextItem(document, stream, item, visualW, visualH, fontCache)
                    is OverlayItem.ImageItem -> drawImageItem(document, stream, item, visualW, visualH)
                    is OverlayItem.DrawingItem -> drawDrawingItem(stream, item, visualW, visualH)
                    is OverlayItem.ShapeItem -> drawShapeItem(stream, item, visualW, visualH)
                }
            }

            stream.restoreGraphicsState()
        }
    }

    private fun drawTextItem(
        document: PDDocument,
        stream: PDPageContentStream,
        item: OverlayItem.TextItem,
        visualW: Float,
        visualH: Float,
        fontCache: MutableMap<OverlayFont, PDType0Font>
    ) {
        if (item.text.isBlank()) return
        val font = fontCache.getOrPut(item.font) {
            context.resources.openRawResource(item.font.fontResId).use { resStream ->
                PDType0Font.load(document, resStream, true)
            }
        }

        val r = ((item.colorArgb shr 16) and 0xFF) / 255f
        val g = ((item.colorArgb shr 8) and 0xFF) / 255f
        val b = (item.colorArgb and 0xFF) / 255f

        val fontSize = item.fontSizePt.coerceIn(6f, 120f)
        val lineHeight = fontSize * 1.25f
        val ascentPt = runCatching {
            (font.fontDescriptor?.ascent ?: 800f) / 1000f * fontSize
        }.getOrDefault(fontSize * 0.8f)

        val anchorX = item.normX * visualW
        val anchorTopY = (1f - item.normY) * visualH
        val lines = item.text.replace("\r\n", "\n").split("\n")

        stream.saveGraphicsState()

        if (item.hasWhiteBackground) {
            var maxLineW = 0f
            for (rawLine in lines) {
                val safe = sanitizeTextForFont(font, rawLine)
                val w = runCatching { font.getStringWidth(safe) / 1000f * fontSize }.getOrDefault(0f)
                if (w > maxLineW) maxLineW = w
            }
            val padX = 4f
            val padY = 3f
            val boxH = (lines.size * lineHeight) + padY * 2f
            val boxW = (maxLineW + padX * 2f).coerceAtLeast(16f)
            stream.setNonStrokingColor(1f, 1f, 1f)
            stream.addRect(anchorX - padX, anchorTopY - boxH + padY, boxW, boxH)
            stream.fill()
        }

        stream.setNonStrokingColor(r, g, b)

        for ((idx, rawLine) in lines.withIndex()) {
            val safeLine = sanitizeTextForFont(font, rawLine)
            if (safeLine.isEmpty()) continue
            val baselineY = anchorTopY - ascentPt - (idx * lineHeight)

            stream.beginText()
            stream.setFont(font, fontSize)
            if (item.rotationDeg != 0f) {
                val rad = Math.toRadians(-item.rotationDeg.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                stream.setTextMatrix(Matrix(cosA, sinA, -sinA, cosA, anchorX, baselineY))
            } else {
                stream.newLineAtOffset(anchorX, baselineY)
            }
            stream.showText(safeLine)
            stream.endText()
        }
        stream.restoreGraphicsState()
    }

    private fun drawImageItem(
        document: PDDocument,
        stream: PDPageContentStream,
        item: OverlayItem.ImageItem,
        visualW: Float,
        visualH: Float
    ) {
        val file = File(item.localFilePath)
        if (!file.exists()) return

        val pdImage = if (file.extension.equals("jpg", ignoreCase = true) ||
            file.extension.equals("jpeg", ignoreCase = true)
        ) {
            FileInputStream(file).use { fis ->
                JPEGFactory.createFromStream(document, fis)
            }
        } else {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
            try {
                LosslessFactory.createFromImage(document, bmp)
            } finally {
                bmp.recycle()
            }
        }

        val cx = item.centerX * visualW
        val cy = (1f - item.centerY) * visualH
        val wPt = item.normWidth * visualW
        val hPt = item.normHeight * visualH

        val matrix = Matrix().apply {
            translate(cx, cy)
            if (item.rotationDeg != 0f) {
                rotate(Math.toRadians(-item.rotationDeg.toDouble()))
            }
            translate(-wPt / 2f, -hPt / 2f)
            scale(wPt, hPt)
        }

        stream.saveGraphicsState()
        stream.drawImage(pdImage, matrix)
        stream.restoreGraphicsState()
    }

    private fun drawDrawingItem(
        stream: PDPageContentStream,
        item: OverlayItem.DrawingItem,
        visualW: Float,
        visualH: Float
    ) {
        if (item.strokes.isEmpty()) return

        val r = ((item.colorArgb shr 16) and 0xFF) / 255f
        val g = ((item.colorArgb shr 8) and 0xFF) / 255f
        val b = (item.colorArgb and 0xFF) / 255f

        val cx = item.centerX * visualW
        val cy = (1f - item.centerY) * visualH
        val wPt = item.normWidth * visualW
        val hPt = item.normHeight * visualH

        stream.saveGraphicsState()

        if (item.opacity < 0.99f) {
            val gs = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = item.opacity.coerceIn(0.05f, 1f)
                nonStrokingAlphaConstant = item.opacity.coerceIn(0.05f, 1f)
            }
            stream.setGraphicsStateParameters(gs)
        }

        val itemMatrix = Matrix().apply {
            translate(cx, cy)
            if (item.rotationDeg != 0f) {
                rotate(Math.toRadians(-item.rotationDeg.toDouble()))
            }
        }
        stream.transform(itemMatrix)

        stream.setStrokingColor(r, g, b)
        stream.setLineWidth(item.strokeWidthPt.coerceIn(0.5f, 32f))
        stream.setLineCapStyle(1) // Round cap
        stream.setLineJoinStyle(1) // Round join

        for (stroke in item.strokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                val px = (pts[0].x - 0.5f) * wPt
                val py = (0.5f - pts[0].y) * hPt
                stream.moveTo(px, py)
                stream.lineTo(px + 0.1f, py + 0.1f)
                stream.stroke()
            } else {
                val p0x = (pts[0].x - 0.5f) * wPt
                val p0y = (0.5f - pts[0].y) * hPt
                stream.moveTo(p0x, p0y)
                for (i in 1 until pts.size) {
                    val px = (pts[i].x - 0.5f) * wPt
                    val py = (0.5f - pts[i].y) * hPt
                    stream.lineTo(px, py)
                }
                stream.stroke()
            }
        }

        stream.restoreGraphicsState()
    }

    private fun drawShapeItem(
        stream: PDPageContentStream,
        item: OverlayItem.ShapeItem,
        visualW: Float,
        visualH: Float
    ) {
        val r = ((item.colorArgb shr 16) and 0xFF) / 255f
        val g = ((item.colorArgb shr 8) and 0xFF) / 255f
        val b = (item.colorArgb and 0xFF) / 255f

        val cx = item.centerX * visualW
        val cy = (1f - item.centerY) * visualH
        val wPt = (item.normWidth * visualW).coerceAtLeast(4f)
        val hPt = (item.normHeight * visualH).coerceAtLeast(4f)
        val hw = wPt / 2f
        val hh = hPt / 2f

        stream.saveGraphicsState()

        if (item.opacity < 0.99f) {
            val gs = PDExtendedGraphicsState().apply {
                strokingAlphaConstant = item.opacity.coerceIn(0.05f, 1f)
                nonStrokingAlphaConstant = item.opacity.coerceIn(0.05f, 1f)
            }
            stream.setGraphicsStateParameters(gs)
        }

        val itemMatrix = Matrix().apply {
            translate(cx, cy)
            if (item.rotationDeg != 0f) {
                rotate(Math.toRadians(-item.rotationDeg.toDouble()))
            }
        }
        stream.transform(itemMatrix)

        stream.setStrokingColor(r, g, b)
        stream.setNonStrokingColor(r, g, b)
        stream.setLineWidth(item.strokeWidthPt.coerceIn(0.5f, 24f))
        stream.setLineCapStyle(1)
        stream.setLineJoinStyle(1)

        when (item.shapeType) {
            GeometricShapeType.RECTANGLE -> {
                stream.addRect(-hw, -hh, wPt, hPt)
                if (item.isFilled) stream.fill() else stream.stroke()
            }
            GeometricShapeType.CIRCLE -> {
                val k = 0.55228475f
                stream.moveTo(hw, 0f)
                stream.curveTo(hw, hh * k, hw * k, hh, 0f, hh)
                stream.curveTo(-hw * k, hh, -hw, hh * k, -hw, 0f)
                stream.curveTo(-hw, -hh * k, -hw * k, -hh, 0f, -hh)
                stream.curveTo(hw * k, -hh, hw, -hh * k, hw, 0f)
                stream.closePath()
                if (item.isFilled) stream.fill() else stream.stroke()
            }
            GeometricShapeType.TRIANGLE -> {
                stream.moveTo(0f, hh)
                stream.lineTo(hw, -hh)
                stream.lineTo(-hw, -hh)
                stream.closePath()
                if (item.isFilled) stream.fill() else stream.stroke()
            }
            GeometricShapeType.DIAMOND -> {
                stream.moveTo(0f, hh)
                stream.lineTo(hw, 0f)
                stream.lineTo(0f, -hh)
                stream.lineTo(-hw, 0f)
                stream.closePath()
                if (item.isFilled) stream.fill() else stream.stroke()
            }
            GeometricShapeType.STAR -> {
                for (i in 0 until 10) {
                    val scale = if (i % 2 == 0) 1f else 0.42f
                    val angleRad = Math.toRadians(90.0 + i * 36.0)
                    val px = (hw * scale * cos(angleRad)).toFloat()
                    val py = (hh * scale * sin(angleRad)).toFloat()
                    if (i == 0) stream.moveTo(px, py) else stream.lineTo(px, py)
                }
                stream.closePath()
                if (item.isFilled) stream.fill() else stream.stroke()
            }
            GeometricShapeType.LINE -> {
                stream.moveTo(-hw, 0f)
                stream.lineTo(hw, 0f)
                stream.stroke()
            }
            GeometricShapeType.ARROW -> {
                val headLen = min(wPt * 0.34f, min(wPt, hPt) * 0.72f).coerceAtLeast(8f)
                val backX = (hw - headLen).coerceAtLeast(-hw * 0.5f)
                if (item.isFilled) {
                    val shaftHalfH = (hh * 0.28f).coerceAtLeast(2f)
                    stream.moveTo(-hw, shaftHalfH)
                    stream.lineTo(backX, shaftHalfH)
                    stream.lineTo(backX, hh)
                    stream.lineTo(hw, 0f)
                    stream.lineTo(backX, -hh)
                    stream.lineTo(backX, -shaftHalfH)
                    stream.lineTo(-hw, -shaftHalfH)
                    stream.closePath()
                    stream.fill()
                } else {
                    val wingH = (hh * 0.78f).coerceAtLeast(6f)
                    stream.moveTo(-hw, 0f)
                    stream.lineTo(hw, 0f)
                    stream.moveTo(backX, wingH)
                    stream.lineTo(hw, 0f)
                    stream.lineTo(backX, -wingH)
                    stream.stroke()
                }
            }
        }

        stream.restoreGraphicsState()
    }

    private fun sanitizeTextForFont(font: PDType0Font, text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch == '\t') {
                sb.append("    ")
                continue
            }
            if (ch.code < 32) continue
            val canEncode = runCatching {
                font.encode(ch.toString())
                true
            }.getOrDefault(false)
            if (canEncode) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
