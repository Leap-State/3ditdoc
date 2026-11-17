package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.PdfEngine
import com.example.data.PdfOverlayExporter
import com.example.model.GeometricShapeType
import com.example.model.NormPoint
import com.example.model.NormStroke
import com.example.model.OverlayFont
import com.example.model.OverlayItem
import com.example.viewmodel.PdfEditorViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("3ditdoc", appName)
    }

    @Test
    @Config(qualifiers = "pt")
    fun `resolves portuguese strings when device locale is pt`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Abrir PDF", context.getString(R.string.action_open_pdf))
        assertEquals("Escrever", context.getString(R.string.tool_text))
        assertEquals("Formas", context.getString(R.string.draw_mode_shapes))
        assertEquals("leapstate@protonmail.com", context.getString(R.string.dev_email_value))
        assertEquals("546efc0f-a2dc-47f0-a5f6-4386eaa62ed8", context.getString(R.string.dev_pix_key))
        assertTrue(context.getString(R.string.dev_card_promise).contains("gratuito"))
    }

    @Test
    @Config(qualifiers = "en")
    fun `resolves english strings when device locale is en`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Open PDF", context.getString(R.string.action_open_pdf))
        assertEquals("Write", context.getString(R.string.tool_text))
        assertEquals("Shapes", context.getString(R.string.draw_mode_shapes))
        assertEquals("leapstate@protonmail.com", context.getString(R.string.dev_email_value))
        assertEquals("546efc0f-a2dc-47f0-a5f6-4386eaa62ed8", context.getString(R.string.dev_pix_key))
        assertTrue(context.getString(R.string.dev_card_promise).contains("free"))
    }

    @Test
    fun `overlay exporter preserves original text and appends independent layer`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val sourcePdf = File(context.cacheDir, "test_original.pdf")

        // Create a real base PDF with original text
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(72f, 720f)
                stream.showText("Contrato Original Intacto")
                stream.endText()
            }
            doc.save(sourcePdf)
        }
        val originalBytes = sourcePdf.readBytes()

        // Create sample overlay PNG image
        val sampleImg = File(context.cacheDir, "sample_stamp.png")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLUE)
        FileOutputStream(sampleImg).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val exporter = PdfOverlayExporter(context)
        val items = mapOf(
            0 to listOf(
                OverlayItem.TextItem(
                    pageIndex = 0,
                    text = "Assinado por Maria",
                    normX = 0.2f,
                    normY = 0.4f,
                    fontSizePt = 16f,
                    font = OverlayFont.SANS
                ),
                OverlayItem.ImageItem(
                    pageIndex = 0,
                    localFilePath = sampleImg.absolutePath,
                    centerX = 0.5f,
                    centerY = 0.6f,
                    normWidth = 0.2f,
                    normHeight = 0.1f,
                    rotationDeg = 15f
                ),
                OverlayItem.DrawingItem(
                    pageIndex = 0,
                    strokes = listOf(
                        NormStroke(listOf(NormPoint(0.1f, 0.2f), NormPoint(0.9f, 0.8f)))
                    ),
                    centerX = 0.5f,
                    centerY = 0.8f,
                    normWidth = 0.3f,
                    normHeight = 0.1f,
                    colorArgb = android.graphics.Color.BLACK,
                    strokeWidthPt = 3f,
                    isSignature = true
                )
            )
        )

        val result = exporter.exportToPreviewFile(sourcePdf.absolutePath, items)
        assertTrue(result.isSuccess)
        val exportedFile = result.getOrThrow()
        assertTrue(exportedFile.exists() && exportedFile.length() > originalBytes.size)

        // Verify original source file bytes were NEVER modified
        assertTrue(sourcePdf.readBytes().contentEquals(originalBytes))

        // Verify both original text and newly appended overlay text exist in exported PDF
        PDDocument.load(exportedFile).use { outDoc ->
            assertEquals(1, outDoc.numberOfPages)
            val extracted = PDFTextStripper().getText(outDoc)
            assertTrue(extracted.contains("Contrato Original Intacto"))
            assertTrue(extracted.contains("Assinado por Maria"))
        }
    }

    @Test
    fun `exporter renders all geometric shape types in outline and filled modes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val sourcePdf = File(context.cacheDir, "test_shapes.pdf")

        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 750f)
                stream.showText("Base Form Document")
                stream.endText()
            }
            doc.save(sourcePdf)
        }
        val originalBytes = sourcePdf.readBytes()

        val shapeItems = GeometricShapeType.entries.flatMapIndexed { idx, type ->
            val y = 0.15f + (idx * 0.10f)
            listOf(
                OverlayItem.ShapeItem(
                    pageIndex = 0,
                    shapeType = type,
                    centerX = 0.30f,
                    centerY = y,
                    normWidth = 0.22f,
                    normHeight = 0.08f,
                    colorArgb = android.graphics.Color.BLUE,
                    strokeWidthPt = 2.6f,
                    isFilled = false,
                    opacity = 1f,
                    rotationDeg = 0f
                ),
                OverlayItem.ShapeItem(
                    pageIndex = 0,
                    shapeType = type,
                    centerX = 0.70f,
                    centerY = y,
                    normWidth = 0.22f,
                    normHeight = 0.08f,
                    colorArgb = android.graphics.Color.RED,
                    strokeWidthPt = 2.6f,
                    isFilled = true,
                    opacity = 0.40f,
                    rotationDeg = 25f
                )
            )
        }

        val exporter = PdfOverlayExporter(context)
        val result = exporter.exportToPreviewFile(sourcePdf.absolutePath, mapOf(0 to shapeItems))
        assertTrue(result.isSuccess)
        val exportedFile = result.getOrThrow()
        assertTrue(exportedFile.exists() && exportedFile.length() > originalBytes.size)
        assertTrue(sourcePdf.readBytes().contentEquals(originalBytes))

        PDDocument.load(exportedFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("Base Form Document"))
        }
    }

    @Test
    fun `viewmodel move all, nudge, center, bring to front, shape edit and undo work`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = PdfEditorViewModel(app)

        vm.addTextItem("Texto A", normX = 0.20f, normY = 0.20f)
        vm.addShapeItem(
            shapeType = GeometricShapeType.STAR,
            centerX = 0.60f,
            centerY = 0.40f,
            normWidth = 0.20f,
            normHeight = 0.20f
        )

        val initialItems = vm.uiState.value.currentPageItems
        assertEquals(2, initialItems.size)
        val textId = initialItems[0].id
        val shapeId = initialItems[1].id

        // Select bottom item (TextItem) and bring it to front
        vm.selectItem(textId)
        vm.bringItemToFront(textId)
        val reordered = vm.uiState.value.currentPageItems
        assertEquals(shapeId, reordered[0].id)
        assertEquals(textId, reordered[1].id)

        // Center selected item horizontally
        vm.centerSelectedItem(horizontal = true, vertical = false)
        val centeredText = vm.uiState.value.currentPageItems.first { it.id == textId } as OverlayItem.TextItem
        assertEquals(0.40f, centeredText.normX, 0.001f)

        // Enable Move All mode and nudge all items on page
        vm.toggleMoveAllMode(true)
        assertTrue(vm.uiState.value.isMoveAllMode)
        vm.nudgeSelectedOrAll(0.05f, 0.05f)

        val shiftedText = vm.uiState.value.currentPageItems.first { it.id == textId } as OverlayItem.TextItem
        val shiftedShape = vm.uiState.value.currentPageItems.first { it.id == shapeId } as OverlayItem.ShapeItem
        assertEquals(0.45f, shiftedText.normX, 0.001f)
        assertEquals(0.25f, shiftedText.normY, 0.001f)
        assertEquals(0.65f, shiftedShape.centerX, 0.001f)
        assertEquals(0.45f, shiftedShape.centerY, 0.001f)

        // Update shape style & duplicate
        vm.updateShapeStyle(shapeId, isFilled = true, opacity = 0.40f)
        val updatedShape = vm.uiState.value.currentPageItems.first { it.id == shapeId } as OverlayItem.ShapeItem
        assertTrue(updatedShape.isFilled)
        assertEquals(0.40f, updatedShape.opacity, 0.001f)

        vm.duplicateItem(shapeId)
        assertEquals(3, vm.uiState.value.currentPageItems.size)
        assertNotEquals(shapeId, vm.uiState.value.selectedItemId)

        // Undo duplicate & check count returns to 2
        vm.undo()
        assertEquals(2, vm.uiState.value.currentPageItems.size)
        vm.redo()
        assertEquals(3, vm.uiState.value.currentPageItems.size)
    }

    @Test
    fun `sample pdf generation creates valid 2 page document`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfEngine(context)

        val result = engine.createSampleTestPdf()
        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals(2, meta.pageCount)
        assertTrue(File(meta.sourceCachePath).exists())

        PDDocument.load(File(meta.sourceCachePath)).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("3ditdoc"))
        }
    }

    @Test
    fun `exporter handles white background text mask and translucent highlighter`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val sourcePdf = File(context.cacheDir, "test_mask_hl.pdf")

        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText("Campo Antigo")
                stream.endText()
            }
            doc.save(sourcePdf)
        }

        val exporter = PdfOverlayExporter(context)
        val items = mapOf(
            0 to listOf(
                OverlayItem.TextItem(
                    pageIndex = 0,
                    text = "Campo Corrigido",
                    normX = 0.1f,
                    normY = 0.2f,
                    fontSizePt = 14f,
                    font = OverlayFont.SANS,
                    hasWhiteBackground = true
                ),
                OverlayItem.DrawingItem(
                    pageIndex = 0,
                    strokes = listOf(
                        NormStroke(listOf(NormPoint(0.1f, 0.5f), NormPoint(0.8f, 0.5f)))
                    ),
                    centerX = 0.45f,
                    centerY = 0.5f,
                    normWidth = 0.7f,
                    normHeight = 0.05f,
                    colorArgb = android.graphics.Color.YELLOW,
                    strokeWidthPt = 14f,
                    opacity = 0.38f
                )
            )
        )

        val result = exporter.exportToPreviewFile(sourcePdf.absolutePath, items)
        assertTrue(result.isSuccess)
        val exported = result.getOrThrow()
        PDDocument.load(exported).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue(extracted.contains("Campo Corrigido"))
        }
    }

    @Test
    fun `images to pdf conversion creates multi-page pdf cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfEngine(context)

        // Create two sample PNG images (portrait & landscape)
        val img1 = File(context.cacheDir, "conv_test_1.png")
        val bmp1 = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bmp1.eraseColor(android.graphics.Color.BLUE)
        FileOutputStream(img1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp1.recycle()

        val img2 = File(context.cacheDir, "conv_test_2.png")
        val bmp2 = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        bmp2.eraseColor(android.graphics.Color.GREEN)
        FileOutputStream(img2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp2.recycle()

        // Convert images into a PDF
        val pdfResult = engine.convertImagesToPdf(
            listOf(Uri.fromFile(img1), Uri.fromFile(img2))
        ).getOrThrow()
        assertEquals(2, pdfResult.pageCount)
        assertTrue(pdfResult.pdfFile.exists())

        // Verify generated PDF has 2 pages via PDFBox & PdfEngine.readPdfPagesMeta
        val meta = engine.readPdfPagesMeta(pdfResult.pdfFile)
        assertEquals(2, meta.size)
        // Page 1 portrait, Page 2 landscape
        assertTrue(meta[0].heightPt > meta[0].widthPt)
        assertTrue(meta[1].widthPt > meta[1].heightPt)
    }

    @Test
    fun `page organizer rotates, moves, and deletes pages offline cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfEngine(context)
        val pdfFile = File(context.cacheDir, "organize_test.pdf")

        PDDocument().use { doc ->
            val p1 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            PDPageContentStream(doc, p1).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("PageOneMarker")
                cs.endText()
            }

            val p2 = PDPage(PDRectangle.A4)
            doc.addPage(p2)
            PDPageContentStream(doc, p2).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("PageTwoMarker")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        // Rotate page 0 by 90 degrees -> width > height
        val rotatedMeta = engine.rotatePdfPage(pdfFile.absolutePath, 0, 90).getOrThrow()
        assertEquals(2, rotatedMeta.size)
        assertTrue(rotatedMeta[0].widthPt > rotatedMeta[0].heightPt)

        // Move page 0 to index 1 -> PageTwoMarker is now page 1, PageOneMarker is page 2
        val movedMeta = engine.movePdfPage(pdfFile.absolutePath, 0, 1).getOrThrow()
        assertEquals(2, movedMeta.size)
        assertTrue(movedMeta[1].widthPt > movedMeta[1].heightPt)

        PDDocument.load(pdfFile).use { doc ->
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = 1
            assertTrue(stripper.getText(doc).contains("PageTwoMarker"))
            stripper.startPage = 2
            stripper.endPage = 2
            val p2Text = stripper.getText(doc).replace(Regex("\\s+"), "")
            assertTrue(p2Text.contains("PageOneMarker"))
        }

        // Delete page 0 -> 1 page remains (PageOneMarker)
        val deletedMeta = engine.deletePdfPage(pdfFile.absolutePath, 0).getOrThrow()
        assertEquals(1, deletedMeta.size)
        PDDocument.load(pdfFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val remainingText = PDFTextStripper().getText(doc).replace(Regex("\\s+"), "")
            assertTrue(remainingText.contains("PageOneMarker"))
        }
    }

    @Test
    fun `removeLightBackgroundFromImage makes white paper transparent and preserves dark ink`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfEngine(context)

        val srcFile = File(context.cacheDir, "paper_sig.png")
        val bmp = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        // Draw dark black ink pixel block in center
        for (x in 15..25) {
            for (y in 15..25) {
                bmp.setPixel(x, y, android.graphics.Color.BLACK)
            }
        }
        FileOutputStream(srcFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val processedPath = engine.removeLightBackgroundFromImage(srcFile.absolutePath).getOrThrow()
        val processedFile = File(processedPath)
        assertTrue(processedFile.exists())

        val outBmp = android.graphics.BitmapFactory.decodeFile(processedPath)
        val cornerAlpha = android.graphics.Color.alpha(outBmp.getPixel(2, 2))
        val inkAlpha = android.graphics.Color.alpha(outBmp.getPixel(20, 20))
        outBmp.recycle()

        assertEquals(0, cornerAlpha)
        assertEquals(255, inkAlpha)
    }

    @Test
    fun `mergePdfFiles combines documents in chosen order cleanly offline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val engine = PdfEngine(context)

        val pdfA = File(context.cacheDir, "merge_a.pdf")
        PDDocument().use { doc ->
            val p1 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            PDPageContentStream(doc, p1).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("DocAlphaOne")
                cs.endText()
            }
            doc.save(pdfA)
        }

        val pdfB = File(context.cacheDir, "merge_b.pdf")
        PDDocument().use { doc ->
            listOf("DocBetaOne", "DocBetaTwo").forEach { marker ->
                val p = PDPage(PDRectangle.A4)
                doc.addPage(p)
                PDPageContentStream(doc, p).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText(marker)
                    cs.endText()
                }
            }
            doc.save(pdfB)
        }

        // Merge with pdfB first, then pdfA -> total 3 pages in [BetaOne, BetaTwo, AlphaOne] order
        val merged = engine.mergePdfFiles(listOf(pdfB, pdfA), "ordem_teste.pdf").getOrThrow()
        assertEquals(3, merged.pageCount)
        assertTrue(merged.pdfFile.exists())

        PDDocument.load(merged.pdfFile).use { outDoc ->
            assertEquals(3, outDoc.numberOfPages)
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = 1
            assertTrue(stripper.getText(outDoc).replace(Regex("\\s+"), "").contains("DocBetaOne"))
            stripper.startPage = 3
            stripper.endPage = 3
            assertTrue(stripper.getText(outDoc).replace(Regex("\\s+"), "").contains("DocAlphaOne"))
        }
    }

    @Test
    fun `createPdfFromImageDrafts applies rotation and draft ordering accurately`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfEngine(context)

        // Portrait 300x600 image (will be rotated 90 deg -> becomes landscape PDF page)
        val portraitImg = File(context.cacheDir, "draft_portrait.png")
        val pBmp = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888)
        pBmp.eraseColor(android.graphics.Color.RED)
        FileOutputStream(portraitImg).use { pBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        pBmp.recycle()

        // Landscape 600x300 image (kept 0 deg -> stays landscape; rotate 90 -> becomes portrait)
        val landscapeImg = File(context.cacheDir, "draft_landscape.png")
        val lBmp = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        lBmp.eraseColor(android.graphics.Color.CYAN)
        FileOutputStream(landscapeImg).use { lBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        lBmp.recycle()

        val draft1 = engine.prepareImageDraft(Uri.fromFile(portraitImg)).getOrThrow().copy(rotationDeg = 90)
        val draft2 = engine.prepareImageDraft(Uri.fromFile(landscapeImg)).getOrThrow().copy(rotationDeg = 90)

        // Reversed order: [draft2 (600x300 rotated 90 -> portrait), draft1 (300x600 rotated 90 -> landscape)]
        val result = engine.createPdfFromImageDrafts(listOf(draft2, draft1)).getOrThrow()
        assertEquals(2, result.pageCount)

        val meta = engine.readPdfPagesMeta(result.pdfFile)
        assertEquals(2, meta.size)
        // First page: rotated landscape -> portrait
        assertTrue(meta[0].heightPt > meta[0].widthPt)
        // Second page: rotated portrait -> landscape
        assertTrue(meta[1].widthPt > meta[1].heightPt)
    }
}
