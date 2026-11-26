package com.branchphotovault.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.branchphotovault.R
import com.branchphotovault.data.model.BranchSortOption
import com.branchphotovault.ui.components.*
import com.branchphotovault.ui.viewmodel.AddBranchViewModel
import com.branchphotovault.ui.viewmodel.BranchListUiState
import com.branchphotovault.ui.viewmodel.BranchListViewModel
import java.util.Date

/** Real implementation of the selected Ghost Gallery mockup, both light and dark. */
@Composable
fun BranchListScreen(
    viewModel: BranchListViewModel,
    onOpenSettings: () -> Unit,
    onAddBranch: () -> Unit,
    onOpenBranch: (account: String, branchCode: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearMessage()
    }
    Box(Modifier.fillMaxSize()) {
        GalleryBackgroundWaves(dark)
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                GalleryRibbonHeader(
                    dark = dark,
                    syncing = uiState.isSyncing,
                    onSync = viewModel::syncBranches,
                    onSettings = onOpenSettings
                )
            },
            floatingActionButton = { GalleryAddButton(onAddBranch) },
            snackbarHost = { SnackbarHost(hostState = snackbar) }
        ) { padding ->
            BranchListContent(
                uiState = uiState,
                modifier = Modifier.padding(padding).offset(y = (-18).dp),
                onSearchChange = viewModel::updateSearch,
                onAccountFilterChange = viewModel::updateAccountFilter,
                onSortChange = viewModel::updateSortOption,
                onOnlyWithPhotosChange = viewModel::updateOnlyWithPhotos,
                onBranchClick = onOpenBranch
            )
        }
    }
}

@Composable
private fun GalleryRibbonHeader(
    dark: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    onSettings: () -> Unit
) {
    val gradient = if (dark) {
        listOf(Color(0xFF1D1C4D), Color(0xFF353075), Color(0xFF5936A4))
    } else {
        listOf(Color(0xFF4B93F8), Color(0xFF5265DB), Color(0xFF8555E8))
    }
    Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(gradient))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val ribbon = Path().apply {
                moveTo(0f, h*.66f)
                cubicTo(w*.24f, h*.92f, w*.41f, h*.87f, w*.60f, h*.44f)
                cubicTo(w*.76f, h*.17f, w*.87f, h*.26f, w, h*.20f)
                lineTo(w, 0f); lineTo(0f, 0f); close()
            }
            drawPath(ribbon, Color.White.copy(alpha=if(dark) .06f else .13f))
            val wave = Path().apply {
                moveTo(0f,h*.85f)
                cubicTo(w*.18f,h*.64f,w*.36f,h*1.03f,w*.58f,h*.82f)
                cubicTo(w*.79f,h*.53f,w*.89f,h*.91f,w,h*.66f)
                lineTo(w,h); lineTo(0f,h);close()
            }
            drawPath(wave, Color(0xFFCAA6FF).copy(alpha=if(dark) .12f else .26f))
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .height(110.dp)
                .padding(start=15.dp,end=9.dp,bottom=10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(R.drawable.keeper_ghost_inapp),
                contentDescription="ผีน้อย Image Keeper",
                modifier=Modifier.size(61.dp)
            )
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(
                    "Image Keeper", style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Bold, color=Color.White,
                    maxLines=1, overflow=TextOverflow.Ellipsis
                )
                Text("Branch Photo Vault", style=MaterialTheme.typography.bodySmall,
                    color=Color.White.copy(alpha=.85f))
            }
            IconButton(onClick=onSync,enabled=!syncing) {
                if(syncing) CircularProgressIndicator(Modifier.size(22.dp),
                    color=Color.White,strokeWidth=2.dp)
                else Icon(Icons.Rounded.Refresh,contentDescription="ซิงก์รายการสาขา",
                    tint=Color.White,modifier=Modifier.size(28.dp))
            }
            IconButton(onClick=onSettings) {
                Icon(Icons.Rounded.Settings,contentDescription="ตั้งค่า",
                    tint=Color.White,modifier=Modifier.size(27.dp))
            }
        }
    }
}

@Composable
private fun GalleryBackgroundWaves(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w=size.width
        val h=size.height
        val back=Path().apply{
            moveTo(0f,h*.86f)
            cubicTo(w*.25f,h*.80f,w*.39f,h*.95f,w*.58f,h*.91f)
            cubicTo(w*.79f,h*.84f,w*.91f,h*.89f,w,h*.92f)
            lineTo(w,h);lineTo(0f,h);close()
        }
        val front=Path().apply{
            moveTo(0f,h*.94f)
            cubicTo(w*.25f,h*.88f,w*.42f,h*1.01f,w*.64f,h*.95f)
            cubicTo(w*.84f,h*.92f,w*.92f,h*.98f,w,h*.96f)
            lineTo(w,h);lineTo(0f,h);close()
        }
        drawPath(back,Brush.horizontalGradient(if(dark)
            listOf(Color(0xFF35297E),Color(0xFF222356))
            else listOf(Color(0xFFDCD4FF),Color(0xFFD2D0FA))))
        drawPath(front,Brush.horizontalGradient(if(dark)
            listOf(Color(0xFF282069),Color(0xFF1C1B48))
            else listOf(Color(0xFFE9E4FF),Color(0xFFE4E2FF))))
    }
}

@Composable
private fun GalleryAddButton(onClick: () -> Unit) {
    val shape=RoundedCornerShape(22.dp)
    Box(
        Modifier.size(61.dp).shadow(12.dp,shape).clip(shape)
            .background(Brush.linearGradient(listOf(
                Color(0xFFAB73FF),Color(0xFF744BEA),Color(0xFF5136CB))))
            .clickable(onClick=onClick),
        contentAlignment=Alignment.Center
    ) {
        Icon(Icons.Rounded.Add,contentDescription="เพิ่มสาขา",
            tint=Color.White,modifier=Modifier.size(35.dp))
    }
}

@Composable
private fun BranchListContent(
    uiState: BranchListUiState,
    modifier: Modifier=Modifier,
    onSearchChange: (String)->Unit,
    onAccountFilterChange: (String?)->Unit,
    onSortChange: (BranchSortOption)->Unit,
    onOnlyWithPhotosChange: (Boolean)->Unit,
    onBranchClick: (account:String,branchCode:String)->Unit
) {
    var accountExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val scheme=MaterialTheme.colorScheme
    val filtered=uiState.searchText.isNotBlank() ||
        uiState.selectedAccount!=null || uiState.onlyWithPhotos

    LazyColumn(
        modifier=modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=100.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(15.dp)) {
                Box(Modifier.shadow(7.dp,RoundedCornerShape(26.dp))) {
                    GlassSearchBar(
                        value=uiState.searchText,
                        onValueChange=onSearchChange,
                        modifier=Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).shadow(4.dp,RoundedCornerShape(17.dp))) {
                        GlassDropdown(
                            value=uiState.selectedAccount ?: "ทุก Account",
                            label="Account",onClick={accountExpanded=true}
                        )
                        DropdownMenu(expanded=accountExpanded,onDismissRequest={accountExpanded=false}) {
                            DropdownMenuItem(text={Text("ทุก Account")},onClick={
                                onAccountFilterChange(null);accountExpanded=false
                            })
                            AddBranchViewModel.ACCOUNTS.forEach {account->
                                DropdownMenuItem(text={Text(account)},onClick={
                                    onAccountFilterChange(account);accountExpanded=false
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f).shadow(4.dp,RoundedCornerShape(17.dp))) {
                        GlassDropdown(value=uiState.sortOption.label,
                            label="จัดเรียง",onClick={sortExpanded=true})
                        DropdownMenu(expanded=sortExpanded,onDismissRequest={sortExpanded=false}) {
                            BranchSortOption.entries.forEach {option->
                                DropdownMenuItem(text={Text(option.label)},onClick={
                                    onSortChange(option);sortExpanded=false
                                })
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top=2.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween
                ) {
                    Row(Modifier.clickable {onOnlyWithPhotosChange(!uiState.onlyWithPhotos)},
                        verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(
                            checked=uiState.onlyWithPhotos,
                            onCheckedChange=onOnlyWithPhotosChange,
                            modifier=Modifier.semantics {
                                contentDescription="เฉพาะสาขาที่มีรูป"
                            }
                        )
                        Text("เฉพาะสาขาที่มีรูป",style=MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "ทั้งหมด "+uiState.branches.size+" สาขา",
                        style=MaterialTheme.typography.bodySmall,
                        color=scheme.onSurfaceVariant
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top=8.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Storefront,contentDescription=null,
                        tint=scheme.primary,modifier=Modifier.size(26.dp))
                    Text("รายการสาขา",style=MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold)
                }
                val lastSyncText=uiState.lastSyncAt?.let {
                    DateFormat.getMediumDateFormat(context).format(Date(it))+" · "+
                        DateFormat.getTimeFormat(context).format(Date(it))
                } ?: "ยังไม่เคยซิงก์"
                Text("ซิงก์ล่าสุด "+lastSyncText,
                    style=MaterialTheme.typography.bodySmall,
                    color=scheme.onSurfaceVariant)
            }
        }
        if(uiState.branches.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top=64.dp,bottom=40.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(64.dp).background(scheme.primaryContainer,
                        RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center) {
                        Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(28.dp),
                            tint=scheme.primary)
                    }
                    Text(if(filtered) "ไม่พบสาขาที่ตรงกับตัวกรอง"
                        else "ยังไม่มีสาขาในรายการ",
                        style=MaterialTheme.typography.titleMedium)
                    Text(if(filtered) "ลองเปลี่ยนคำค้นหาหรือตัวกรอง"
                        else "กดซิงก์ด้านบนเพื่อโหลดรายชื่อสาขา",
                        style=MaterialTheme.typography.bodyMedium,
                        color=scheme.onSurfaceVariant)
                }
            }
        } else {
            items(uiState.branches,key={it.account+"_"+it.branchCode}) {branch->
                BranchListItemCard(branch=branch,
                    onClick={onBranchClick(branch.account,branch.branchCode)})
            }
        }
    }
}
