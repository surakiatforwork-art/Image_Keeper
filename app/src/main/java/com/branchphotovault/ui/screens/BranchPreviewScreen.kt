package com.branchphotovault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import com.branchphotovault.ui.components.GlassDialog as AlertDialog
import com.branchphotovault.ui.components.GlassButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.branchphotovault.ui.components.GlassSecondaryButton as FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.branchphotovault.ui.components.GlassTextField as OutlinedTextField
import com.branchphotovault.ui.components.GlassScaffold as Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.branchphotovault.ui.components.GlassToolbar as TopAppBar
import com.branchphotovault.ui.components.GlassCard
import com.branchphotovault.ui.components.rememberGallerySavePermission
import com.branchphotovault.ui.components.glassBrush
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.ui.components.ExportProgressCard
import com.branchphotovault.ui.components.BranchGalleryControls
import com.branchphotovault.ui.components.BranchPhotoListItem
import com.branchphotovault.data.repository.GalleryLayout
import com.branchphotovault.data.repository.GalleryPhotoSort
import com.branchphotovault.ui.components.RouteBadge
import com.branchphotovault.ui.viewmodel.BranchPreviewViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BranchPreviewScreen(
    viewModel: BranchPreviewViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onOpenCamera: () -> Unit,
    capturedTempPath: String?,
    capturedMirrorHorizontally: Boolean,
    onCapturedPathConsumed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val galleryViewConfig by viewModel.galleryViewConfig.collectAsStateWithLifecycle()
    // Purely visual sorting: selection/export order stays as explicitly tapped.
    val visiblePhotos = remember(uiState.photos, galleryViewConfig.sort) {
        when (galleryViewConfig.sort) {
            GalleryPhotoSort.NEWEST_FIRST ->
                uiState.photos.sortedWith(compareByDescending<PhotoEntity> { it.createdAt }.thenBy { it.id })
            GalleryPhotoSort.OLDEST_FIRST ->
                uiState.photos.sortedWith(compareBy<PhotoEntity> { it.createdAt }.thenBy { it.id })
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val saveWithPermission = rememberGallerySavePermission {
        coroutineScope.launch { snackbarHostState.showSnackbar("กรุณาอนุญาตให้บันทึกภาพลงแกลเลอรี่") }
    }
    val context = LocalContext.current

    val defaultRouteAtCapture: Int? = null
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSendConfirmation by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.importPhotos(uris)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onOpenCamera()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is required.")
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(capturedTempPath, capturedMirrorHorizontally) {
        if (!capturedTempPath.isNullOrBlank()) {
            viewModel.saveCapturedPhoto(
                tempFilePath = capturedTempPath,
                routeAtCapture = defaultRouteAtCapture,
                note = null,
                mirrorHorizontally = capturedMirrorHorizontally
            )
            onCapturedPathConsumed()
        }
    }

    if (showDeleteConfirmation) {
        val selectedCount = uiState.selectedPhotoIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(if (selectedCount <= 1) "Delete Photo" else "Delete Photos")
            },
            text = {
                Text(
                    if (selectedCount <= 1) {
                        "This permanently deletes the selected photo and its local files."
                    } else {
                        "This permanently deletes the selected photos and their local files."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSendConfirmation) {
        val selectedCount = uiState.selectedPhotoIds.size
        AlertDialog(
            onDismissRequest = { showSendConfirmation = false },
            title = { Text("Send photos") },
            text = { Text("Choose where to send $selectedCount selected photo(s) in the selected order.") },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    viewModel.sendSelectedToGhostShift(context)
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    viewModel.sendSelectedToGhostShift(context, toTimestamp = true)
                }) { Text("Timestamp") }
                TextButton(onClick = { showSendConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectedPhotoIds.isNotEmpty()) {
                            "${uiState.selectedPhotoIds.size} selected"
                        } else {
                            uiState.branch?.branchCode ?: "Branch"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedPhotoIds.isNotEmpty()) {
                        IconButton(onClick = { saveWithPermission(viewModel::saveSelectedToGallery) },enabled=!uiState.isProcessing) {
                            Icon(Icons.Rounded.Download, contentDescription = "Save selected to gallery")
                        }
                        IconButton(onClick = { showSendConfirmation = true }) {
                            Icon(Icons.Rounded.Send, contentDescription = "Send selected to GhostShift")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete selected")
                        }
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.selectedPhotoIds.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    onOpenCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Text("ถ่ายภาพ")
                        }
                        FilledTonalButton(
                            onClick = { importLauncher.launch("image/*") },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Text("นำเข้า")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(
                if (galleryViewConfig.layout == GalleryLayout.LIST) 1 else galleryViewConfig.columns
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(
                if (galleryViewConfig.layout == GalleryLayout.LIST) 8.dp else 4.dp
            )
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.branch?.let { branch ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = branch.shopName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${branch.account} • ${branch.branchCode}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = "${uiState.photos.size} รูป",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    BranchGalleryControls(
                        config = galleryViewConfig,
                        onLayoutChange = viewModel::setGalleryLayout,
                        onColumnsChange = viewModel::setGalleryColumns,
                        onSortChange = viewModel::setGallerySort,
                        modifier = Modifier.fillMaxWidth()
                    )

                    uiState.exportProgress?.let { exportProgress ->
                        ExportProgressCard(progress = exportProgress)
                    }

                    if (uiState.isProcessing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            if (uiState.photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ยังไม่มีภาพในสาขานี้", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "ถ่ายภาพหรือนำเข้ารูปเพื่อเก็บไว้ในสาขานี้",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(visiblePhotos, key = { it.id }) { photo ->
                    val isSelected = photo.id in uiState.selectedPhotoIds
                    val onPhotoClick: () -> Unit = {
                        if (uiState.selectedPhotoIds.isNotEmpty()) {
                            viewModel.toggleSelection(photo.id)
                        } else {
                            onOpenPhoto(photo.id)
                        }
                    }
                    val onPhotoLongClick: () -> Unit = { viewModel.toggleSelection(photo.id) }
                    if (galleryViewConfig.layout == GalleryLayout.LIST) {
                        BranchPhotoListItem(
                            photo = photo,
                            selected = isSelected,
                            selectionActive = uiState.selectedPhotoIds.isNotEmpty(),
                            onClick = onPhotoClick,
                            onLongClick = onPhotoLongClick
                        )
                    } else {
                        PhotoGridItem(
                            photo = photo,
                            isSelected = isSelected,
                            selectionActive = uiState.selectedPhotoIds.isNotEmpty(),
                            onClick = onPhotoClick,
                            onLongClick = onPhotoLongClick,
                            compact = galleryViewConfig.columns >= 5
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photo: PhotoEntity,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    compact: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = File(photo.localThumbPath),
            contentDescription = photo.shopName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (selectionActive) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (compact) 3.dp else 8.dp)
                    .size(if (compact) 20.dp else 24.dp)
            )
        }
    }
}
