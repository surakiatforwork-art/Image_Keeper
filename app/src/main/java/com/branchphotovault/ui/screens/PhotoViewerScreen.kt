package com.branchphotovault.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.branchphotovault.ui.viewmodel.PhotoViewerViewModel
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    viewModel: PhotoViewerViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { uiState.photos.size })
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var initialPageApplied by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(uiState.initialIndex, uiState.photos.size) {
        if (uiState.photos.isEmpty()) {
            initialPageApplied = false
            return@LaunchedEffect
        }

        val lastIndex = uiState.photos.lastIndex
        val targetPage = when {
            !initialPageApplied -> uiState.initialIndex.coerceIn(0, lastIndex)
            pagerState.currentPage > lastIndex -> lastIndex
            else -> null
        }

        if (targetPage != null && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
        initialPageApplied = true
    }

    val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage)

    if (showDeleteConfirmation && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Photo") },
            text = { Text("This permanently deletes this photo and its local files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deletePhoto(currentPhoto.id)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildString {
                            append(currentPhoto?.branchCode ?: "Photo Viewer")
                            if (uiState.photos.isNotEmpty()) {
                                append(" (${pagerState.currentPage + 1}/${uiState.photos.size})")
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (uiState.photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No photos found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val photo = uiState.photos[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = File(photo.localThumbPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        AsyncImage(
                            model = File(photo.localMainPath),
                            contentDescription = photo.shopName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                currentPhoto?.let { photo ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.savePhotoToGallery(photo.id) },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isSavingToGallery) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                }
                                Text("Save to Gallery")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.sendPhotoToGhostShift(context, photo) },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Send, contentDescription = null)
                                Text("Send to GhostShift")
                            }
                            OutlinedButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isDeleting) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Delete, contentDescription = null)
                                }
                                Text("Delete")
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(photo.shopName, style = MaterialTheme.typography.titleMedium)
                                Text("${photo.account} • ${photo.branchCode}")
                                Text("Route at capture: ${photo.routeAtCapture ?: "-"}")
                                Text(
                                    "Captured: ${
                                        DateFormat.getMediumDateFormat(context).format(Date(photo.createdAt))
                                    } ${
                                        DateFormat.getTimeFormat(context).format(Date(photo.createdAt))
                                    }"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
