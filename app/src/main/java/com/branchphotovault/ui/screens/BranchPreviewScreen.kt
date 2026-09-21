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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.ui.components.ExportProgressCard
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val defaultRouteAtCapture = uiState.branch?.currentRoute
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSendConfirmation by remember { mutableStateOf(false) }
    var routeEditorVisible by remember { mutableStateOf(false) }
    var routeDraft by remember(uiState.branch?.currentRoute, routeEditorVisible) {
        mutableStateOf(uiState.branch?.currentRoute?.toString().orEmpty())
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhoto(
                sourceUri = uri,
                routeAtCapture = defaultRouteAtCapture,
                note = null
            )
        }
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
            title = { Text("Send to GhostShift") },
            text = { Text("Send $selectedCount selected photo(s) to GhostShift in the selected order?") },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    viewModel.sendSelectedToGhostShift(context)
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (routeEditorVisible) {
        AlertDialog(
            onDismissRequest = { routeEditorVisible = false },
            title = { Text("Update Route") },
            text = {
                OutlinedTextField(
                    value = routeDraft,
                    onValueChange = { routeDraft = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Current Route") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        routeEditorVisible = false
                        viewModel.updateRoute(routeDraft.toIntOrNull())
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { routeEditorVisible = false }) {
                    Text("Cancel")
                }
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
                        IconButton(onClick = viewModel::saveSelectedToGallery) {
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
                    } else {
                        IconButton(onClick = { routeEditorVisible = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Update route")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.selectedPhotoIds.isEmpty()) {
                Surface(shadowElevation = 8.dp) {
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
                            Text("Capture")
                        }
                        FilledTonalButton(
                            onClick = { importLauncher.launch("image/*") },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Text("Import")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.branch?.let { branch ->
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = branch.shopName,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "${branch.account} • ${branch.branchCode}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    RouteBadge(route = branch.currentRoute)
                                }
                                Text(
                                    text = "${uiState.photos.size} photo(s)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                FilledTonalButton(
                                    onClick = viewModel::saveAllToGallery,
                                    enabled = uiState.photos.isNotEmpty()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Text("Save All to Gallery")
                                }
                            }
                        }
                    }

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
                        Text("No photos yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Capture or import images to build the branch vault.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(uiState.photos, key = { it.id }) { photo ->
                    PhotoGridItem(
                        photo = photo,
                        isSelected = photo.id in uiState.selectedPhotoIds,
                        selectionActive = uiState.selectedPhotoIds.isNotEmpty(),
                        onClick = {
                            if (uiState.selectedPhotoIds.isNotEmpty()) {
                                viewModel.toggleSelection(photo.id)
                            } else {
                                onOpenPhoto(photo.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(photo.id) }
                    )
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
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
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
                    .padding(8.dp)
            )
        }
    }
}
