package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.R
import com.example.model.ImagePageDraft
import com.example.model.MergePdfSource
import com.example.model.PdfDocumentMeta
import com.example.model.PdfPageMeta
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PreparedImage(
    val localPath: String,
    val aspectRatio: Float
)

data class PdfConversionPreview(
    val pdfPath: String,
    val displayName: String,
    val pageCount: Int,
    val currentPageIndex: Int,
    val currentPngFile: File,
    val currentBitmap: Bitmap
)

data class ImageToPdfResult(
    val pdfFile: File,
    val displayName: String,
    val pageCount: Int,
    val firstPageBitmap: Bitmap?
)

data class MergedPdfResult(
    val pdfFile: File,
    val displayName: String,
    val pageCount: Int,
    val firstPageBitmap: Bitmap?
)

data class SavedDownloadResult(
    val savedDisplayName: String,
    val requiresSafPicker: Boolean
)

class PdfEngine(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private val renderMutex = Mutex()
    private val bitmapCache = LinkedHashMap<String, Bitmap>(24, 0.75f, true)
    private val maxCachedPages = 24

    suspend fun importPdfToCache(uri: Uri): Result<PdfDocumentMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceDir = File(context.cacheDir, "source_pdfs").apply { mkdirs() }
            val destFile = File(sourceDir, "active_document.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error(context.getString(R.string.err_read_file))

            if (destFile.length() == 0L) {
                error(context.getString(R.string.err_empty_pdf))
            }

            val rawName = resolveDisplayName(uri) ?: context.getString(R.string.default_pdf_filename)
            val displayName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
            buildMetaFromFile(destFile, displayName)
        }
    }

    suspend fun importLocalPdfToActive(file: File, displayName: String): Result<PdfDocumentMeta> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceDir = File(context.cacheDir, "source_pdfs").apply { mkdirs() }
                val destFile = File(sourceDir, "active_document.pdf")
                if (file.absolutePath != destFile.absolutePath) {
                    file.copyTo(destFile, overwrite = true)
                }
                buildMetaFromFile(destFile, displayName)
            }
        }

    private suspend fun buildMetaFromFile(file: File, displayName: String): PdfDocumentMeta {
        val baseName = displayName.removeSuffix(".pdf").removeSuffix(".PDF")
        val suggestedOutput = "${baseName}${context.getString(R.string.edited_suffix)}"
        val pages = readPdfPagesMeta(file)
        if (pages.isEmpty()) {
            error(context.getString(R.string.err_no_pages))
        }
        clearBitmapCache()
        return PdfDocumentMeta(
            displayName = displayName,
            suggestedOutputName = suggestedOutput,
            sourceCachePath = file.absolutePath,
            pages = pages
        )
    }

    suspend fun createSampleTestPdf(): Result<PdfDocumentMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceDir = File(context.cacheDir, "source_pdfs").apply { mkdirs() }
            val destFile = File(sourceDir, "active_document.pdf")
            val sampleName = context.getString(R.string.sample_pdf_name)

            PDDocument().use { doc ->
                val font = context.resources.openRawResource(R.font.inter).use {
                    PDType0Font.load(doc, it, true)
                }

                // Page 1: Realistic Form
                val page1 = PDPage(PDRectangle.A4)
                doc.addPage(page1)
                val w1 = page1.mediaBox.width
                val h1 = page1.mediaBox.height

                PDPageContentStream(doc, page1).use { cs ->
                    // Header band
                    cs.setNonStrokingColor(0.11f, 0.30f, 0.85f)
                    cs.addRect(0f, h1 - 92f, w1, 92f)
                    cs.fill()

                    cs.beginText()
                    cs.setNonStrokingColor(1f, 1f, 1f)
                    cs.setFont(font, 20f)
                    cs.newLineAtOffset(48f, h1 - 46f)
                    cs.showText("3ditdoc — FORMULARIO DE TESTE")
                    cs.endText()

                    cs.beginText()
                    cs.setFont(font, 11f)
                    cs.newLineAtOffset(48f, h1 - 68f)
                    cs.showText("Documento local de demonstracao. O arquivo original nunca e alterado.")
                    cs.endText()

                    // Form fields
                    cs.setNonStrokingColor(0.08f, 0.12f, 0.18f)
                    drawSampleField(cs, font, 48f, h1 - 150f, w1 - 96f, "Nome completo / Full name:")
                    drawSampleField(cs, font, 48f, h1 - 230f, (w1 - 116f) / 2f, "Documento / ID:")
                    drawSampleField(cs, font, 68f + (w1 - 116f) / 2f, h1 - 230f, (w1 - 116f) / 2f, "Data / Date:")
                    drawSampleField(cs, font, 48f, h1 - 310f, w1 - 96f, "Observacoes / Notes:")

                    // Checkboxes
                    cs.setStrokingColor(0.25f, 0.32f, 0.42f)
                    cs.setLineWidth(1.2f)
                    cs.addRect(48f, h1 - 375f, 16f, 16f)
                    cs.stroke()
                    cs.beginText()
                    cs.setFont(font, 12f)
                    cs.newLineAtOffset(74f, h1 - 372f)
                    cs.showText("[ ] Autorizo o processamento 100% offline neste aparelho.")
                    cs.endText()

                    cs.addRect(48f, h1 - 408f, 16f, 16f)
                    cs.stroke()
                    cs.beginText()
                    cs.setFont(font, 12f)
                    cs.newLineAtOffset(74f, h1 - 405f)
                    cs.showText("[ ] Confirmo os dados preenchidos acima.")
                    cs.endText()

                    // Signature & Photo box
                    cs.setNonStrokingColor(0.95f, 0.96f, 0.98f)
                    cs.addRect(48f, 110f, 250f, 110f)
                    cs.fill()
                    cs.setStrokingColor(0.65f, 0.70f, 0.78f)
                    cs.addRect(48f, 110f, 250f, 110f)
                    cs.stroke()

                    cs.beginText()
                    cs.setNonStrokingColor(0.35f, 0.42f, 0.52f)
                    cs.setFont(font, 10f)
                    cs.newLineAtOffset(58f, 124f)
                    cs.showText("Assinatura / Signature")
                    cs.endText()

                    cs.setNonStrokingColor(0.95f, 0.96f, 0.98f)
                    cs.addRect(w1 - 238f, 110f, 190f, 110f)
                    cs.fill()
                    cs.setStrokingColor(0.65f, 0.70f, 0.78f)
                    cs.addRect(w1 - 238f, 110f, 190f, 110f)
                    cs.stroke()

                    cs.beginText()
                    cs.setNonStrokingColor(0.35f, 0.42f, 0.52f)
                    cs.setFont(font, 10f)
                    cs.newLineAtOffset(w1 - 228f, 124f)
                    cs.showText("Foto / Carimbo (Opcional)")
                    cs.endText()
                }

                // Page 2: Clauses for Highlighting & Multi-page testing
                val page2 = PDPage(PDRectangle.A4)
                doc.addPage(page2)
                PDPageContentStream(doc, page2).use { cs ->
                    cs.beginText()
                    cs.setNonStrokingColor(0.11f, 0.30f, 0.85f)
                    cs.setFont(font, 16f)
                    cs.newLineAtOffset(48f, h1 - 64f)
                    cs.showText("PAGINA 2 — TESTE DE MARCA-TEXTO E CONVERSAO")
                    cs.endText()

                    val lines = listOf(
                        "1. Use o Marca-texto para destacar esta clausula importante.",
                        "2. Use o Carimbo Visto/X para marcar opcoes rapidamente.",
                        "3. Exporte em 'PDF para Imagem' e converta de volta em 'Imagem para PDF'.",
                        "4. Tudo permanece 100% offline no seu celular, sem marcas d'agua."
                    )
                    lines.forEachIndexed { idx, line ->
                        cs.beginText()
                        cs.setNonStrokingColor(0.12f, 0.16f, 0.22f)
                        cs.setFont(font, 12f)
                        cs.newLineAtOffset(48f, h1 - 120f - (idx * 36f))
                        cs.showText(line)
                        cs.endText()
                    }
                }

                doc.save(destFile)
            }

            buildMetaFromFile(destFile, sampleName)
        }
    }

    private fun drawSampleField(
        cs: PDPageContentStream,
        font: PDType0Font,
        x: Float,
        y: Float,
        width: Float,
        label: String
    ) {
        cs.beginText()
        cs.setFont(font, 11f)
        cs.newLineAtOffset(x, y + 28f)
        cs.showText(label)
        cs.endText()

        cs.setStrokingColor(0.70f, 0.74f, 0.82f)
        cs.setLineWidth(1f)
        cs.addRect(x, y, width, 24f)
        cs.stroke()
    }

    suspend fun copyExternalPdfForConversion(uri: Uri): Result<Pair<File, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "convert_src").apply { mkdirs() }
                val dest = File(dir, "to_image.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: error(context.getString(R.string.err_read_file))
                if (dest.length() == 0L) error(context.getString(R.string.err_empty_pdf))
                val name = resolveDisplayName(uri) ?: context.getString(R.string.default_pdf_filename)
                dest to name
            }
        }

    suspend fun exportPdfPageAsPng(
        pdfPath: String,
        displayName: String,
        pageIndex: Int
    ): Result<PdfConversionPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(pdfPath)
            if (!file.exists()) error(context.getString(R.string.err_source_missing))

            val pages = readPdfPagesMeta(file)
            if (pages.isEmpty()) error(context.getString(R.string.err_no_pages))
            val clamped = pageIndex.coerceIn(0, pages.lastIndex)

            val bmp = renderPage(
                pdfPath = pdfPath,
                pageIndex = clamped,
                targetWidthPx = 1800,
                cacheKeyPrefix = "png_export"
            ) ?: error(context.getString(R.string.err_generate_preview))

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val base = displayName.removeSuffix(".pdf").removeSuffix(".PDF")
            val pngFile = File(exportDir, "${base}_pag${clamped + 1}.png")
            FileOutputStream(pngFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            PdfConversionPreview(
                pdfPath = pdfPath,
                displayName = displayName,
                pageCount = pages.size,
                currentPageIndex = clamped,
                currentPngFile = pngFile,
                currentBitmap = bmp
            )
        }
    }

    suspend fun convertImagesToPdf(imageUris: List<Uri>): Result<ImageToPdfResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (imageUris.isEmpty()) error(context.getString(R.string.err_read_image))
                val drafts = imageUris.map { uri ->
                    val prepared = prepareOverlayImage(uri).getOrThrow()
                    ImagePageDraft(
                        originalPath = prepared.localPath,
                        localPath = prepared.localPath,
                        aspectRatio = prepared.aspectRatio
                    )
                }
                createPdfFromImageDrafts(drafts).getOrThrow()
            }
        }

    suspend fun prepareImageDraft(uri: Uri): Result<ImagePageDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val prepared = prepareOverlayImage(uri).getOrThrow()
            val preview = decodePreviewBitmap(prepared.localPath, maxDimPx = 420)
            ImagePageDraft(
                originalPath = prepared.localPath,
                localPath = prepared.localPath,
                rotationDeg = 0,
                isBackgroundRemoved = false,
                aspectRatio = prepared.aspectRatio,
                previewBitmap = preview
            )
        }
    }

    suspend fun toggleImageDraftBgRemoval(draft: ImagePageDraft): Result<ImagePageDraft> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (draft.isBackgroundRemoved) {
                    val preview = decodePreviewBitmap(draft.originalPath, maxDimPx = 420)
                    draft.copy(
                        localPath = draft.originalPath,
                        isBackgroundRemoved = false,
                        previewBitmap = preview
                    )
                } else {
                    val processedPath = removeLightBackgroundFromImage(draft.originalPath).getOrThrow()
                    val preview = decodePreviewBitmap(processedPath, maxDimPx = 420)
                    draft.copy(
                        localPath = processedPath,
                        isBackgroundRemoved = true,
                        previewBitmap = preview
                    )
                }
            }
        }

    suspend fun createPdfFromImageDrafts(drafts: List<ImagePageDraft>): Result<ImageToPdfResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (drafts.isEmpty()) error(context.getString(R.string.err_read_image))
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outPdf = File(exportDir, "imagens_convertidas.pdf")

                PDDocument().use { doc ->
                    for (draft in drafts) {
                        val imgFile = File(draft.localPath)
                        if (!imgFile.exists()) error(context.getString(R.string.err_read_image))
                        val normRot = ((draft.rotationDeg % 360) + 360) % 360
                        val swapDims = normRot == 90 || normRot == 270

                        var decodedBmp: Bitmap? = null
                        val rawWidth: Float
                        val rawHeight: Float
                        val pdImage = if (imgFile.extension.equals("jpg", true)) {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(imgFile.absolutePath, bounds)
                            rawWidth = max(1, bounds.outWidth).toFloat()
                            rawHeight = max(1, bounds.outHeight).toFloat()
                            FileInputStream(imgFile).use { fis -> JPEGFactory.createFromStream(doc, fis) }
                        } else {
                            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                                ?: error(context.getString(R.string.err_unsupported_image))
                            decodedBmp = bmp
                            rawWidth = max(1, bmp.width).toFloat()
                            rawHeight = max(1, bmp.height).toFloat()
                            LosslessFactory.createFromImage(doc, bmp)
                        }

                        val finalWidth = if (swapDims) rawHeight else rawWidth
                        val finalHeight = if (swapDims) rawWidth else rawHeight

                        try {
                            val pageRect = if (finalWidth > finalHeight) {
                                PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                            } else {
                                PDRectangle.A4
                            }
                            val page = PDPage(pageRect)
                            doc.addPage(page)

                            val margin = 24f
                            val availW = pageRect.width - margin * 2f
                            val availH = pageRect.height - margin * 2f
                            val scale = min(availW / finalWidth, availH / finalHeight)
                            val unrotW = rawWidth * scale
                            val unrotH = rawHeight * scale
                            val cx = pageRect.width / 2f
                            val cy = pageRect.height / 2f

                            PDPageContentStream(doc, page).use { cs ->
                                if (normRot == 0) {
                                    cs.drawImage(pdImage, cx - unrotW / 2f, cy - unrotH / 2f, unrotW, unrotH)
                                } else {
                                    cs.saveGraphicsState()
                                    cs.transform(PdfMatrix.getTranslateInstance(cx, cy))
                                    cs.transform(PdfMatrix.getRotateInstance(Math.toRadians(-normRot.toDouble()), 0f, 0f))
                                    cs.drawImage(pdImage, -unrotW / 2f, -unrotH / 2f, unrotW, unrotH)
                                    cs.restoreGraphicsState()
                                }
                            }
                        } finally {
                            decodedBmp?.recycle()
                        }
                    }
                    doc.save(outPdf)
                }

                val firstBmp = runCatching {
                    renderPage(
                        pdfPath = outPdf.absolutePath,
                        pageIndex = 0,
                        targetWidthPx = 1200,
                        cacheKeyPrefix = "img2pdf"
                    )
                }.getOrNull()

                ImageToPdfResult(
                    pdfFile = outPdf,
                    displayName = "imagens_3ditdoc.pdf",
                    pageCount = drafts.size,
                    firstPageBitmap = firstBmp
                )
            }
        }

    suspend fun inspectLocalPdfForMerge(uri: Uri): Result<MergePdfSource> = withContext(Dispatchers.IO) {
        runCatching {
            val mergeDir = File(context.cacheDir, "merge_src").apply { mkdirs() }
            val dest = File(mergeDir, "src_${UUID.randomUUID()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error(context.getString(R.string.err_read_file))

            if (dest.length() == 0L) {
                dest.delete()
                error(context.getString(R.string.err_empty_pdf))
            }

            val pages = readPdfPagesMeta(dest)
            if (pages.isEmpty()) {
                dest.delete()
                error(context.getString(R.string.err_no_pages))
            }

            val rawName = resolveDisplayName(uri) ?: context.getString(R.string.default_pdf_filename)
            val displayName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
            val thumb = runCatching {
                renderPage(
                    pdfPath = dest.absolutePath,
                    pageIndex = 0,
                    targetWidthPx = 300,
                    cacheKeyPrefix = "merge_thumb_${dest.nameWithoutExtension}"
                )
            }.getOrNull()

            MergePdfSource(
                localPath = dest.absolutePath,
                displayName = displayName,
                pageCount = pages.size,
                previewBitmap = thumb
            )
        }
    }

    suspend fun mergePdfFiles(
        files: List<File>,
        outputName: String = "mesclado_3ditdoc.pdf"
    ): Result<MergedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (files.size < 2) error(context.getString(R.string.merge_need_two))
            files.forEach { file ->
                if (!file.exists() || file.length() == 0L) error(context.getString(R.string.err_read_file))
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = if (outputName.endsWith(".pdf", ignoreCase = true)) outputName else "$outputName.pdf"
            val outPdf = File(exportDir, "merged_${System.currentTimeMillis()}.pdf")

            renderMutex.withLock {
                val merger = PDFMergerUtility()
                files.forEach { merger.addSource(it) }
                merger.destinationFileName = outPdf.absolutePath
                merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            }

            val pages = readPdfPagesMeta(outPdf)
            if (pages.isEmpty()) error(context.getString(R.string.err_no_pages))

            clearBitmapCache("merged_preview")
            val firstBmp = runCatching {
                renderPage(
                    pdfPath = outPdf.absolutePath,
                    pageIndex = 0,
                    targetWidthPx = 1100,
                    cacheKeyPrefix = "merged_preview"
                )
            }.getOrNull()

            MergedPdfResult(
                pdfFile = outPdf,
                displayName = safeName,
                pageCount = pages.size,
                firstPageBitmap = firstBmp
            )
        }
    }

    suspend fun appendPdfToWorkingFile(
        workingPdfPath: String,
        extraPdfUri: Uri
    ): Result<List<PdfPageMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val workingFile = File(workingPdfPath)
            if (!workingFile.exists()) error(context.getString(R.string.err_source_missing))

            val mergeDir = File(context.cacheDir, "merge_src").apply { mkdirs() }
            val extraTemp = File(mergeDir, "append_${UUID.randomUUID()}.pdf")
            val mergedTemp = File(mergeDir, "merged_work_${UUID.randomUUID()}.pdf")
            try {
                context.contentResolver.openInputStream(extraPdfUri)?.use { input ->
                    FileOutputStream(extraTemp).use { output -> input.copyTo(output) }
                } ?: error(context.getString(R.string.err_read_file))

                if (extraTemp.length() == 0L) error(context.getString(R.string.err_empty_pdf))
                if (readPdfPagesMeta(extraTemp).isEmpty()) error(context.getString(R.string.err_no_pages))

                renderMutex.withLock {
                    val merger = PDFMergerUtility()
                    merger.addSource(workingFile)
                    merger.addSource(extraTemp)
                    merger.destinationFileName = mergedTemp.absolutePath
                    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
                    mergedTemp.copyTo(workingFile, overwrite = true)
                }
            } finally {
                extraTemp.delete()
                mergedTemp.delete()
            }

            clearBitmapCache()
            readPdfPagesMeta(workingFile)
        }
    }

    private fun decodePreviewBitmap(path: String, maxDimPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx, maxDimPx)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, opts)
    }

    suspend fun readPdfPagesMeta(file: File): List<PdfPageMeta> = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            PDDocument.load(file).use { doc ->
                val list = ArrayList<PdfPageMeta>(doc.numberOfPages)
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val rot = ((page.rotation % 360) + 360) % 360
                    val isSwapped = rot == 90 || rot == 270
                    val w = if (isSwapped) box.height else box.width
                    val h = if (isSwapped) box.width else box.height
                    list.add(
                        PdfPageMeta(
                            index = i,
                            widthPt = max(1f, w),
                            heightPt = max(1f, h)
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun renderPage(
        pdfPath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1440,
        cacheKeyPrefix: String = "src"
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${cacheKeyPrefix}_${pdfPath.hashCode()}_${pageIndex}_$targetWidthPx"
        synchronized(bitmapCache) {
            bitmapCache[key]?.let { if (!it.isRecycled) return@withContext it }
        }

        val file = File(pdfPath)
        if (!file.exists()) return@withContext null

        renderMutex.withLock {
            synchronized(bitmapCache) {
                bitmapCache[key]?.let { if (!it.isRecycled) return@withLock it }
            }

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                    renderer.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / max(1, page.width).toFloat()
                        val w = targetWidthPx.coerceIn(220, 2200)
                        val h = (w * aspect).roundToInt().coerceAtLeast(1)

                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        synchronized(bitmapCache) {
                            if (bitmapCache.size >= maxCachedPages) {
                                val oldestKey = bitmapCache.entries.firstOrNull()?.key
                                if (oldestKey != null) bitmapCache.remove(oldestKey)
                            }
                            bitmapCache[key] = bmp
                        }
                        bmp
                    }
                }
            }
        }
    }

    fun clearBitmapCache(prefix: String? = null) {
        synchronized(bitmapCache) {
            if (prefix == null) {
                bitmapCache.clear()
            } else {
                val keysToRemove = bitmapCache.keys.filter { it.startsWith(prefix) }
                keysToRemove.forEach { bitmapCache.remove(it) }
            }
        }
    }

    suspend fun prepareOverlayImage(uri: Uri): Result<PreparedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val imageDir = File(context.cacheDir, "overlay_images").apply { mkdirs() }
            val tempRaw = File(imageDir, "raw_${UUID.randomUUID()}")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRaw).use { output ->
                    input.copyTo(output)
                }
            } ?: error(context.getString(R.string.err_read_image))

            val exifRotation = runCatching {
                ExifInterface(tempRaw.absolutePath).rotationDegrees
            }.getOrDefault(0)

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempRaw.absolutePath, boundsOpts)
            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 2048, 2048)

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeFile(tempRaw.absolutePath, decodeOpts)
                ?: error(context.getString(R.string.err_unsupported_image))

            if (exifRotation != 0) {
                val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            if (max(bitmap.width, bitmap.height) > 2048) {
                val scale = 2048f / max(bitmap.width, bitmap.height).toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            val hasAlpha = bitmap.hasAlpha()
            val ext = if (hasAlpha) "png" else "jpg"
            val outFile = File(imageDir, "img_${UUID.randomUUID()}.$ext")
            FileOutputStream(outFile).use { out ->
                if (hasAlpha) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }

            val aspect = bitmap.width.toFloat() / max(1, bitmap.height).toFloat()
            bitmap.recycle()
            tempRaw.delete()

            PreparedImage(
                localPath = outFile.absolutePath,
                aspectRatio = aspect
            )
        }
    }

    fun createCameraCaptureFile(): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "foto_${System.currentTimeMillis()}.jpg")
    }

    suspend fun saveFileToDownloads(
        sourceFile: File,
        desiredName: String,
        mimeType: String
    ): Result<SavedDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sourceFile.exists()) error(context.getString(R.string.err_source_missing))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@runCatching SavedDownloadResult(
                    savedDisplayName = desiredName,
                    requiresSafPicker = true
                )
            }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, desiredName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/3ditdoc")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching SavedDownloadResult(
                    savedDisplayName = desiredName,
                    requiresSafPicker = true
                )

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: error(context.getString(R.string.err_write_output))

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                val actualName = resolveDisplayName(itemUri) ?: desiredName
                SavedDownloadResult(savedDisplayName = actualName, requiresSafPicker = false)
            } catch (t: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw t
            }
        }
    }

    suspend fun rotatePdfPage(
        pdfPath: String,
        pageIndex: Int,
        deltaDeg: Int = 90
    ): Result<List<PdfPageMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(pdfPath)
            if (!file.exists()) error(context.getString(R.string.err_source_missing))
            renderMutex.withLock {
                PDDocument.load(file).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) error(context.getString(R.string.err_no_pages))
                    val page = doc.getPage(pageIndex)
                    val nextRotation = (((page.rotation + deltaDeg) % 360) + 360) % 360
                    page.rotation = nextRotation
                    doc.save(file)
                }
            }
            clearBitmapCache()
            readPdfPagesMeta(file)
        }
    }

    suspend fun movePdfPage(
        pdfPath: String,
        fromIndex: Int,
        toIndex: Int
    ): Result<List<PdfPageMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(pdfPath)
            if (!file.exists()) error(context.getString(R.string.err_source_missing))
            renderMutex.withLock {
                PDDocument.load(file).use { doc ->
                    val count = doc.numberOfPages
                    if (fromIndex !in 0 until count || toIndex !in 0 until count) {
                        error(context.getString(R.string.err_no_pages))
                    }
                    if (fromIndex != toIndex) {
                        val movingPage = doc.getPage(fromIndex)
                        val anchorPage = doc.getPage(toIndex)
                        doc.pages.remove(movingPage)
                        if (toIndex < fromIndex) {
                            doc.pages.insertBefore(movingPage, anchorPage)
                        } else {
                            doc.pages.insertAfter(movingPage, anchorPage)
                        }
                        doc.save(file)
                    }
                }
            }
            clearBitmapCache()
            readPdfPagesMeta(file)
        }
    }

    suspend fun deletePdfPage(
        pdfPath: String,
        pageIndex: Int
    ): Result<List<PdfPageMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(pdfPath)
            if (!file.exists()) error(context.getString(R.string.err_source_missing))
            renderMutex.withLock {
                PDDocument.load(file).use { doc ->
                    if (doc.numberOfPages <= 1) error(context.getString(R.string.err_no_pages))
                    if (pageIndex !in 0 until doc.numberOfPages) error(context.getString(R.string.err_no_pages))
                    doc.removePage(pageIndex)
                    doc.save(file)
                }
            }
            clearBitmapCache()
            readPdfPagesMeta(file)
        }
    }

    suspend fun removeLightBackgroundFromImage(originalPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(originalPath)
                if (!srcFile.exists()) error(context.getString(R.string.err_read_image))
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val decoded = BitmapFactory.decodeFile(srcFile.absolutePath, opts)
                    ?: error(context.getString(R.string.err_unsupported_image))

                val mutableBmp = if (decoded.config == Bitmap.Config.ARGB_8888 && decoded.isMutable) {
                    decoded
                } else {
                    val copy = decoded.copy(Bitmap.Config.ARGB_8888, true)
                    decoded.recycle()
                    copy
                }

                val w = mutableBmp.width
                val h = mutableBmp.height
                val pixels = IntArray(w * h)
                mutableBmp.getPixels(pixels, 0, w, 0, 0, w, h)

                for (i in pixels.indices) {
                    val c = pixels[i]
                    val a = (c ushr 24) and 0xFF
                    if (a == 0) continue

                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF

                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val chroma = maxC - minC

                    if (lum > 208f && chroma < 42) {
                        pixels[i] = 0x00000000
                    } else if (lum > 168f && chroma < 48) {
                        val factor = ((208f - lum) / 40f).coerceIn(0f, 1f)
                        val newA = (a * factor).roundToInt().coerceIn(0, 255)
                        val nr = (r * 0.85f).roundToInt().coerceIn(0, 255)
                        val ng = (g * 0.85f).roundToInt().coerceIn(0, 255)
                        val nb = (b * 0.85f).roundToInt().coerceIn(0, 255)
                        pixels[i] = (newA shl 24) or (nr shl 16) or (ng shl 8) or nb
                    } else {
                        // Slightly boost ink contrast for scanned paper strokes
                        val nr = (r * 0.90f).roundToInt().coerceIn(0, 255)
                        val ng = (g * 0.90f).roundToInt().coerceIn(0, 255)
                        val nb = (b * 0.90f).roundToInt().coerceIn(0, 255)
                        pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                    }
                }

                mutableBmp.setPixels(pixels, 0, w, 0, 0, w, h)

                val imageDir = File(context.cacheDir, "overlay_images").apply { mkdirs() }
                val outFile = File(imageDir, "nobg_${UUID.randomUUID()}.png")
                FileOutputStream(outFile).use { out ->
                    mutableBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                mutableBmp.recycle()
                outFile.absolutePath
            }
        }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
