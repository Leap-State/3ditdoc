package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.LegalTab
import com.example.ui.AppNavigationDrawerSheet
import com.example.ui.EditorScreen
import com.example.ui.HomeScreen
import com.example.ui.ImageToPdfDialog
import com.example.ui.ImageToPdfStagingDialog
import com.example.ui.LegalScreen
import com.example.ui.MergePdfDialog
import com.example.ui.PdfToImageDialog
import com.example.ui.PreviewSaveScreen
import com.example.ui.TutorialScreen
import com.example.ui.theme.ThreeDitDocTheme
import com.example.viewmodel.PdfEditorViewModel
import kotlinx.coroutines.launch
import java.io.File

private object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val PREVIEW = "preview"
    const val TUTORIAL = "tutorial"
    const val LEGAL = "legal"
}

class MainActivity : ComponentActivity() {

    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        incomingPdfUri = extractPdfUri(intent)

        setContent {
            ThreeDitDocTheme {
                val viewModel: PdfEditorViewModel = viewModel()
                ThreeDitDocApp(
                    viewModel = viewModel,
                    initialPdfUri = incomingPdfUri,
                    onConsumeIncomingUri = { incomingPdfUri = null },
                    onShareFile = { file, suggestedName ->
                        shareFileWithType(file, "application/pdf", suggestedName)
                    },
                    onSharePngFile = { file ->
                        shareFileWithType(file, "image/png", file.name)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPdfUri(intent)?.let { uri ->
            incomingPdfUri = uri
        }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
    }

    private fun shareFileWithType(file: File, mimeType: String, subject: String) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
    }
}

@Composable
private fun ThreeDitDocApp(
    viewModel: PdfEditorViewModel,
    initialPdfUri: Uri?,
    onConsumeIncomingUri: () -> Unit,
    onShareFile: (File, String) -> Unit,
    onSharePngFile: (File) -> Unit
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle external PDF opened from WhatsApp, Email, Drive, Teams, or Files
    LaunchedEffect(initialPdfUri) {
        if (initialPdfUri != null) {
            viewModel.openPdfFromUri(initialPdfUri) {
                onConsumeIncomingUri()
                navController.navigate(Routes.EDITOR) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissError()
    }

    // SAF Open PDF launcher (zero broad storage permissions)
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdfFromUri(uri) {
                navController.navigate(Routes.EDITOR) {
                    launchSingleTop = true
                }
            }
        }
    }

    // SAF Create New PDF launcher (never overwrites original without user choice)
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.savePreviewPdfToUri(destUri)
        }
    }

    // Zero-permission Android Photo Picker (single image for overlay)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addImageFromUri(uri)
        }
    }

    // User-initiated Camera Capture via FileProvider
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.onCameraCaptureSuccess()
        }
    }

    // Converter launchers: PDF -> Image (PNG) & Image -> PDF
    val pdfToImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.convertExternalPdfUriToImage(uri)
        }
    }

    val imageToPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.startImageToPdfDrafts(uris)
        }
    }

    val appendImageDraftsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.appendImageToPdfDrafts(uris)
        }
    }

    val mergePdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMergePdfs(uris)
        }
    }

    val appendPdfToDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.appendPdfToOpenDocument(uri)
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destUri ->
        if (destUri != null) {
            viewModel.saveConvertedPngToUri(destUri)
        }
    }

    val saveConvertedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.saveConvertedPdfToUri(destUri)
        }
    }

    val saveMergedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.saveMergedPdfToUri(destUri)
        }
    }

    val allowDrawerSwipe = currentRoute == Routes.HOME ||
        currentRoute == Routes.TUTORIAL ||
        currentRoute?.startsWith(Routes.LEGAL) == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = allowDrawerSwipe,
        drawerContent = {
            AppNavigationDrawerSheet(
                currentRoute = currentRoute,
                onOpenPdf = {
                    scope.launch { drawerState.close() }
                    openPdfLauncher.launch(arrayOf("application/pdf"))
                },
                onOpenSamplePdf = {
                    scope.launch { drawerState.close() }
                    viewModel.openSampleTestPdf {
                        navController.navigate(Routes.EDITOR) {
                            launchSingleTop = true
                        }
                    }
                },
                onMergePdfs = {
                    scope.launch { drawerState.close() }
                    viewModel.openMergeDialog()
                    if (state.mergeSources.isEmpty()) {
                        mergePdfPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                },
                onPdfToImage = {
                    scope.launch { drawerState.close() }
                    pdfToImagePickerLauncher.launch(arrayOf("application/pdf"))
                },
                onImageToPdf = {
                    scope.launch { drawerState.close() }
                    imageToPdfPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenTutorial = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.TUTORIAL) {
                        launchSingleTop = true
                    }
                },
                onOpenLegal = { tab ->
                    scope.launch { drawerState.close() }
                    navController.navigate("${Routes.LEGAL}/${tab.name}") {
                        launchSingleTop = true
                    }
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            isLoadingDocument = state.isLoadingDocument,
                            onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            },
                            onOpenPdfClick = {
                                openPdfLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    }

                    composable(Routes.EDITOR) {
                        if (state.document == null) {
                            LaunchedEffect(Unit) {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                        } else {
                            EditorScreen(
                                state = state,
                                onBackHome = {
                                    viewModel.closeDocument()
                                    navController.popBackStack(Routes.HOME, inclusive = false)
                                },
                                onSelectPage = viewModel::selectPage,
                                onSetActiveTool = viewModel::setActiveTool,
                                onSetDrawMode = viewModel::setDrawMode,
                                onSetActiveShapeType = viewModel::setActiveShapeType,
                                onSetShapeFilled = viewModel::setShapeFilled,
                                onSetShapeOpacity = viewModel::setShapeOpacity,
                                onToggleMoveAllMode = viewModel::toggleMoveAllMode,
                                onToggleMovePanel = viewModel::toggleMovePanel,
                                onNudgeSelectedOrAll = viewModel::nudgeSelectedOrAll,
                                onCenterHorizontal = {
                                    viewModel.centerSelectedItem(horizontal = true, vertical = false)
                                },
                                onBringToFront = viewModel::bringItemToFront,
                                onToggleLayerVisibility = viewModel::toggleLayerVisibility,
                                onSelectItem = viewModel::selectItem,
                                onAddText = viewModel::addTextItem,
                                onUpdateText = { id, text, size, font, color, hasWhiteBg ->
                                    viewModel.updateTextItem(
                                        id = id,
                                        text = text,
                                        fontSizePt = size,
                                        font = font,
                                        color = color,
                                        hasWhiteBackground = hasWhiteBg,
                                        recordUndo = true
                                    )
                                },
                                onPickGalleryImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onTakeCameraPhoto = {
                                    val file = viewModel.prepareCameraOutputFile()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.applicationContext.packageName}.fileprovider",
                                        file
                                    )
                                    takePhotoLauncher.launch(uri)
                                },
                                onSetDrawColor = viewModel::setDrawColor,
                                onSetDrawStrokeWidth = viewModel::setDrawStrokeWidth,
                                onCommitDrawing = viewModel::addDrawingStrokes,
                                onCommitShape = { shapeType, cx, cy, nw, nh ->
                                    viewModel.addShapeItem(
                                        shapeType = shapeType,
                                        centerX = cx,
                                        centerY = cy,
                                        normWidth = nw,
                                        normHeight = nh
                                    )
                                },
                                onInsertDefaultShape = { viewModel.addShapeItem() },
                                onReuseLastSignature = viewModel::reuseLastSignature,
                                onAddQuickStamp = viewModel::addQuickStamp,
                                onBeginGestureUndo = viewModel::captureUndoCheckpoint,
                                onMoveItem = viewModel::moveItem,
                                onMoveAllItems = { dx, dy -> viewModel.moveAllItemsOnPage(dx, dy, recordUndo = false) },
                                onScaleItem = viewModel::scaleItem,
                                onRotateItem = viewModel::rotateItem,
                                onToggleImageBgRemoval = viewModel::toggleImageBackgroundRemoval,
                                onLoadPageThumbnails = viewModel::loadPageThumbnails,
                                onRotatePage = viewModel::rotateDocumentPage,
                                onMovePage = viewModel::moveDocumentPage,
                                onDeletePage = viewModel::deleteDocumentPage,
                                onAppendPdf = {
                                    appendPdfToDocLauncher.launch(arrayOf("application/pdf"))
                                },
                                onChangeDrawingColor = { id, color ->
                                    viewModel.updateDrawingStyle(id, color = color)
                                },
                                onUpdateShapeStyle = { id, color, filled, opacity ->
                                    viewModel.updateShapeStyle(id, color = color, isFilled = filled, opacity = opacity)
                                },
                                onDuplicateItem = viewModel::duplicateItem,
                                onDeleteItem = viewModel::deleteItem,
                                onClearAllLayers = viewModel::clearAllOverlayLayers,
                                onUndo = viewModel::undo,
                                onRedo = viewModel::redo,
                                onOpenPreviewSave = {
                                    viewModel.generatePreviewPdf {
                                        navController.navigate(Routes.PREVIEW)
                                    }
                                },
                                onExportCurrentPageAsImage = {
                                    viewModel.convertCurrentEditedPdfPageToImage(
                                        pageIndex = state.currentPageIndex,
                                        usePreviewPdf = false
                                    )
                                },
                                onOpenTutorial = {
                                    navController.navigate(Routes.TUTORIAL)
                                },
                                onOpenLegal = {
                                    navController.navigate("${Routes.LEGAL}/${LegalTab.PRIVACY.name}")
                                }
                            )
                        }
                    }

                    composable(Routes.PREVIEW) {
                        PreviewSaveScreen(
                            state = state,
                            onBackToEditor = {
                                viewModel.dismissSavedMessage()
                                navController.popBackStack()
                            },
                            onSelectPreviewPage = viewModel::selectPreviewPage,
                            onSaveToDownloads = {
                                viewModel.savePreviewPdfToDownloads { suggested ->
                                    createPdfLauncher.launch(suggested)
                                }
                            },
                            onSaveOtherFolder = {
                                val suggested = state.document?.suggestedOutputName
                                    ?: context.getString(R.string.default_edited_filename)
                                createPdfLauncher.launch(suggested)
                            },
                            onSharePdf = {
                                val previewPath = state.previewPdfPath ?: return@PreviewSaveScreen
                                val file = File(previewPath)
                                if (file.exists()) {
                                    val suggested = state.document?.suggestedOutputName
                                        ?: context.getString(R.string.default_edited_filename)
                                    onShareFile(file, suggested)
                                }
                            },
                            onSaveAsImage = {
                                viewModel.convertCurrentEditedPdfPageToImage(
                                    pageIndex = state.previewPageIndex,
                                    usePreviewPdf = true
                                )
                            }
                        )
                    }

                    composable(Routes.TUTORIAL) {
                        TutorialScreen(
                            onFinish = {
                                viewModel.markTutorialSeen()
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(
                        route = "${Routes.LEGAL}/{tab}",
                        arguments = listOf(navArgument("tab") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val tabName = backStackEntry.arguments?.getString("tab") ?: LegalTab.PRIVACY.name
                        val initialTab = runCatching { LegalTab.valueOf(tabName) }.getOrDefault(LegalTab.PRIVACY)
                        LegalScreen(
                            initialTab = initialTab,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Converter dialogs (PDF -> PNG & Image -> PDF)
                state.pdfToImagePreview?.let { preview ->
                    PdfToImageDialog(
                        preview = preview,
                        isBusy = state.isConverting || state.isSavingOutput,
                        statusMessage = state.converterStatusMessage,
                        onSelectPage = viewModel::selectPdfToImagePage,
                        onSavePng = {
                            viewModel.saveConvertedPngToDownloads { suggested ->
                                savePngLauncher.launch(suggested)
                            }
                        },
                        onSharePng = {
                            onSharePngFile(preview.currentPngFile)
                        },
                        onDismiss = viewModel::clearPdfToImagePreview
                    )
                }

                if (state.imagePageDrafts.isNotEmpty() && state.imageToPdfResult == null) {
                    ImageToPdfStagingDialog(
                        drafts = state.imagePageDrafts,
                        isBusy = state.isConverting,
                        onMoveDraft = viewModel::moveImageDraft,
                        onRotateDraft = viewModel::rotateImageDraft,
                        onToggleBgRemoval = viewModel::toggleImageDraftBgRemoval,
                        onRemoveDraft = viewModel::removeImageDraft,
                        onAddMorePhotos = {
                            appendImageDraftsPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onGeneratePdf = viewModel::generatePdfFromImageDrafts,
                        onDismiss = viewModel::clearImageDrafts
                    )
                }

                state.imageToPdfResult?.let { result ->
                    ImageToPdfDialog(
                        result = result,
                        isBusy = state.isConverting || state.isSavingOutput,
                        statusMessage = state.converterStatusMessage,
                        onEditInApp = {
                            viewModel.openConvertedPdfInEditor {
                                navController.navigate(Routes.EDITOR) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onSavePdf = {
                            viewModel.saveConvertedPdfToDownloads { suggested ->
                                saveConvertedPdfLauncher.launch(suggested)
                            }
                        },
                        onDismiss = viewModel::clearImageToPdfResult
                    )
                }

                if (state.isMergeDialogOpen) {
                    MergePdfDialog(
                        sources = state.mergeSources,
                        mergedResult = state.mergedPdfResult,
                        isBusy = state.isMergingPdfs || state.isSavingOutput,
                        statusMessage = state.converterStatusMessage,
                        onAddPdfsClick = {
                            mergePdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onMoveSource = viewModel::moveMergeSource,
                        onRemoveSource = viewModel::removeMergeSource,
                        onExecuteMerge = { viewModel.executeMergePdfs() },
                        onSavePdf = {
                            viewModel.saveMergedPdfToDownloads { suggested ->
                                saveMergedPdfLauncher.launch(suggested)
                            }
                        },
                        onEditInApp = {
                            viewModel.openMergedPdfInEditor {
                                navController.navigate(Routes.EDITOR) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onDismiss = viewModel::clearMergeState
                    )
                }

                val showGlobalBlockingOverlay =
                    (state.isConverting &&
                        state.pdfToImagePreview == null &&
                        state.imagePageDrafts.isEmpty() &&
                        state.imageToPdfResult == null) ||
                        (state.isMergingPdfs && !state.isMergeDialogOpen)

                if (showGlobalBlockingOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.converter_processing),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
