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
import com.branchphotovault.ui.components.GlassDialog as AlertDialog
import com.branchphotovault.ui.components.GlassButton as Button
import com.branchphotovault.ui.components.GlassCard as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.branchphotovault.ui.components.GlassSecondaryButton as FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.branchphotovault.ui.components.GlassSecondaryButton as OutlinedButton
import com.branchphotovault.ui.components.GlassScaffold as Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.branchphotovault.ui.components.GlassToolbar as TopAppBar
import com.branchphotovault.ui.components.ZoomableImageViewport
import com.branchphotovault.ui.components.ImageZoomState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import coil.request.ImageRequest
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var activeZoomed by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var initialPageApplied by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSendConfirmation by remember { mutableStateOf(false) }
    var retouchPhoto by remember { mutableStateOf<com.branchphotovault.data.local.PhotoEntity?>(null) }
    retouchPhoto?.let { photo -> MoustacheRetouchDialog(photo) { retouchPhoto = null } }

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
    LaunchedEffect(currentPhoto?.id) { activeZoomed=false }

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

    if (showSendConfirmation && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showSendConfirmation = false },
            title = { Text("Send photos") },
            text = { Text("Choose where to send this photo.") },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    viewModel.sendPhotoToGhostShift(context, currentPhoto)
                }) { Text("GhostShift") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    viewModel.sendPhotoToGhostShift(context, currentPhoto, toTimestamp = true)
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
            BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            val scrollLayout = maxHeight < 560.dp || LocalDensity.current.fontScale > 1.3f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if(scrollLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    key = { uiState.photos[it].id },
                    userScrollEnabled = !activeZoomed,
                    modifier = (if(scrollLayout) Modifier.height(300.dp) else Modifier.weight(1f))
                        .fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha=.4f))
                ) { page ->
                    val photo = uiState.photos[page]
                    var sourceSize by remember(photo.id) { mutableStateOf(IntSize.Zero) }
                    val zoom=remember(photo.id) { ImageZoomState() }
                    val request=remember(photo.localMainPath) { ImageRequest.Builder(context).data(File(photo.localMainPath))
                        .size(coil.size.Size.ORIGINAL).build() }
                    ZoomableImageViewport(photo.id,Modifier.fillMaxSize(),sourceSize,
                        onZoomChanged={ if(page==pagerState.currentPage) activeZoomed=it },state=zoom) {
                        AsyncImage(
                            model = File(photo.localThumbPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        AsyncImage(
                            model = request,
                            contentDescription = photo.shopName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onSuccess = { sourceSize=IntSize(it.result.drawable.intrinsicWidth,it.result.drawable.intrinsicHeight) }
                        )
                    }
                }

                LazyRow(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    itemsIndexed(uiState.photos,key={ _,photo -> photo.id }) { index,photo ->
                        AsyncImage(File(photo.localThumbPath),"ดูภาพ ${index+1}",
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                .border(if(index==pagerState.currentPage) 2.dp else .5.dp,
                                    if(index==pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=.4f),RoundedCornerShape(10.dp))
                                .clickable { scope.launch { activeZoomed=false; pagerState.animateScrollToPage(index) } },contentScale=ContentScale.Crop)
                    }
                }

                currentPhoto?.let { photo ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { retouchPhoto = photo },
                            enabled = !uiState.isDeleting && !uiState.isSavingToGallery,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("แต่งภาพ · หนวดและเครา")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.savePhotoToGallery(photo.id) },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    if (uiState.isSavingToGallery) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                                    else Icon(Icons.Rounded.Download,null,Modifier.size(21.dp))
                                    Text("บันทึก",style=MaterialTheme.typography.labelMedium,maxLines=1)
                                }
                            }
                            FilledTonalButton(
                                onClick = { showSendConfirmation = true },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Rounded.Send,null,Modifier.size(21.dp))
                                    Text("ส่งภาพ",style=MaterialTheme.typography.labelMedium,maxLines=1)
                                }
                            }
                            OutlinedButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !uiState.isSavingToGallery && !uiState.isDeleting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    if(uiState.isDeleting) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                                    else Icon(Icons.Rounded.Delete,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.error)
                                    Text("ลบ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error,maxLines=1)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(photo.shopName, style = MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                if (photo.note?.contains("Retouched moustache; source=") == true) {
                                    Text("ฉบับรีทัชหนวด · ต้นฉบับเก็บแยกไว้", color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${photo.account} • ${photo.branchCode}",style=MaterialTheme.typography.bodySmall)
                                Text("Route at capture: ${photo.routeAtCapture ?: "-"}",style=MaterialTheme.typography.labelSmall)
                                Text(
                                    "Captured: ${
                                        DateFormat.getMediumDateFormat(context).format(Date(photo.createdAt))
                                    } ${
                                        DateFormat.getTimeFormat(context).format(Date(photo.createdAt))
                                    }",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
