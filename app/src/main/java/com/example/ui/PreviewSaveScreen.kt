package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.PdfCanvasBackdrop
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewSaveScreen(
    state: EditorUiState,
    onBackToEditor: () -> Unit,
    onSelectPreviewPage: (Int) -> Unit,
    onSaveToDownloads: () -> Unit,
    onSaveOtherFolder: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveAsImage: () -> Unit
) {
    val doc = state.document ?: return
    val pageMeta = doc.pages.getOrNull(state.previewPageIndex)
    var zoom by remember(state.previewPageIndex) { mutableFloatStateOf(1f) }
    var pan by remember(state.previewPageIndex) { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.preview_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.preview_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToEditor,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.preview_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveAsImage,
                        enabled = !state.isConverting && state.previewPdfPath != null,
                        modifier = Modifier.testTag("preview_save_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = stringResource(R.string.preview_save_image_btn)
                        )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.savedSuccessMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SuccessGreenContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.savedSuccessMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.preview_intact_note, doc.suggestedOutputName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSharePdf,
                            enabled = !state.isSavingOutput && state.previewPdfPath != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("share_pdf_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.preview_share_btn))
                        }

                        Button(
                            onClick = onSaveToDownloads,
                            enabled = !state.isSavingOutput && state.previewPdfPath != null,
                            modifier = Modifier
                                .weight(1.6f)
                                .height(54.dp)
                                .testTag("save_new_pdf_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (state.isSavingOutput) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.preview_saving))
                            } else {
                                Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.save_downloads_btn))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.share_offline_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = onSaveOtherFolder,
                            enabled = !state.isSavingOutput && state.previewPdfPath != null,
                            modifier = Modifier.testTag("save_other_folder_button")
                        ) {
                            Text(
                                text = stringResource(R.string.save_other_folder_btn),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PdfCanvasBackdrop)
                .pointerInput(state.previewPageIndex) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        zoom = nextZoom
                        pan = if (nextZoom <= 1.01f) {
                            Offset.Zero
                        } else {
                            val maxX = size.width * (nextZoom - 1f) * 0.5f
                            val maxY = size.height * (nextZoom - 1f) * 0.5f
                            Offset(
                                (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                (pan.y + panChange.y).coerceIn(-maxY, maxY)
                            )
                        }
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

            val bmp = state.previewPageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.preview_image_desc),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(with(density) { pageW.toDp() }, with(density) { pageH.toDp() })
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .shadow(12.dp, RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }

            if (doc.pageCount > 1) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { onSelectPreviewPage(state.previewPageIndex - 1) },
                            enabled = state.previewPageIndex > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.editor_prev_page)
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.editor_page_indicator,
                                state.previewPageIndex + 1,
                                doc.pageCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { onSelectPreviewPage(state.previewPageIndex + 1) },
                            enabled = state.previewPageIndex < doc.pageCount - 1,
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
}
