package com.branchphotovault.ui.screens

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
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
import kotlinx.coroutines.delay

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
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
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
                    onSettings = onOpenSettings,
                    onAddBranch = onAddBranch
                )
            },
            bottomBar = {
                GallerySearchDock(
                    value = uiState.searchText,
                    onValueChange = viewModel::updateSearch,
                    keyboardVisible = keyboardVisible
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbar) }
        ) { padding ->
            BranchListContent(
                uiState = uiState,
                modifier = Modifier.padding(padding),
                onToggleAccountFilter = viewModel::toggleAccountFilter,
                onClearAccountFilters = viewModel::clearAccountFilters,
                onSortChange = viewModel::updateSortOption,
                onOnlyWithPhotosChange = viewModel::updateOnlyWithPhotos,
                onBranchClick = onOpenBranch
            )
        }
    }
}

@Composable
internal fun GalleryRibbonHeader(
    dark: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onAddBranch: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }
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
            // Keep secondary actions together; the floating button is for search.
            Box {
                IconButton(onClick={overflowExpanded=true}) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription="เมนูเพิ่มเติม",
                        tint=Color.White,
                        modifier=Modifier.size(29.dp)
                    )
                }
                DropdownMenu(
                    expanded=overflowExpanded,
                    onDismissRequest={overflowExpanded=false}
                ) {
                    DropdownMenuItem(
                        text={Text("เพิ่มรายการร้าน")},
                        leadingIcon={Icon(Icons.Rounded.Add,contentDescription=null)},
                        onClick={
                            overflowExpanded=false
                            onAddBranch()
                        }
                    )
                    DropdownMenuItem(
                        text={Text(if(syncing) "กำลังรีเฟรช..." else "รีเฟรชรายการสาขา")},
                        leadingIcon={
                            if(syncing) {
                                CircularProgressIndicator(
                                    modifier=Modifier.size(21.dp), strokeWidth=2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh,contentDescription=null)
                            }
                        },
                        enabled=!syncing,
                        onClick={
                            overflowExpanded=false
                            onSync()
                        }
                    )
                    DropdownMenuItem(
                        text={Text("ตั้งค่า")},
                        leadingIcon={Icon(Icons.Rounded.Settings,contentDescription=null)},
                        onClick={
                            overflowExpanded=false
                            onSettings()
                        }
                    )
                }
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
private fun GallerySearchButton(expanded: Boolean, onClick: () -> Unit) {
    val shape=RoundedCornerShape(22.dp)
    Box(
        Modifier.size(61.dp).shadow(12.dp,shape).clip(shape)
            .background(Brush.linearGradient(listOf(
                Color(0xFFAB73FF),Color(0xFF744BEA),Color(0xFF5136CB))))
            .clickable(role=Role.Button,onClick=onClick),
        contentAlignment=Alignment.Center
    ) {
        Icon(
            if (expanded) Icons.Rounded.Close else Icons.Rounded.Search,
            contentDescription=if (expanded) "ปิดการค้นหา" else "ค้นหาร้าน",
            tint=Color.White,modifier=Modifier.size(30.dp)
        )
    }
}

@Composable
private fun BranchListContent(
    uiState: BranchListUiState,
    modifier: Modifier=Modifier,
    onToggleAccountFilter: (String)->Unit,
    onClearAccountFilters: ()->Unit,
    onSortChange: (BranchSortOption)->Unit,
    onOnlyWithPhotosChange: (Boolean)->Unit,
    onBranchClick: (account:String,branchCode:String)->Unit
) {
    var accountExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val scheme=MaterialTheme.colorScheme
    val filtered=uiState.searchText.isNotBlank() ||
        uiState.selectedAccounts.isNotEmpty() || uiState.onlyWithPhotos

    LazyColumn(
        modifier=modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=15.dp,bottom=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                // 60% Filter | 30% Sort | 10% Photos only, all on one row.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(4.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(6f)) {
                        CompactBranchFilter(
                            value = when (uiState.selectedAccounts.size) {
                                0 -> "ทุก Account"
                                1 -> uiState.selectedAccounts.first()
                                else -> "${uiState.selectedAccounts.size} Account"
                            },
                            label="Filter", onClick={accountExpanded=true},
                            modifier=Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded=accountExpanded,
                            onDismissRequest={accountExpanded=false}
                        ) {
                            DropdownMenuItem(
                                text={Text("ทุก Account")},
                                leadingIcon={
                                    Checkbox(
                                        checked=uiState.selectedAccounts.isEmpty(),
                                        onCheckedChange=null,
                                        modifier=Modifier.size(28.dp)
                                    )
                                },
                                onClick={onClearAccountFilters()}
                            )
                            HorizontalDivider()
                            AddBranchViewModel.ACCOUNTS.forEach { account ->
                                DropdownMenuItem(
                                    text={Text(account)},
                                    leadingIcon={
                                        Checkbox(
                                            checked=account in uiState.selectedAccounts,
                                            onCheckedChange=null,
                                            modifier=Modifier.size(28.dp)
                                        )
                                    },
                                    onClick={onToggleAccountFilter(account)}
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(3f)) {
                        CompactBranchFilter(
                            value=uiState.sortOption.label,
                            label="Sort", onClick={sortExpanded=true},
                            modifier=Modifier.fillMaxWidth(),
                            short=true
                        )
                        DropdownMenu(
                            expanded=sortExpanded,
                            onDismissRequest={sortExpanded=false}
                        ) {
                            BranchSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text={Text(option.label)},
                                    onClick={onSortChange(option); sortExpanded=false}
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                        PhotosOnlyCheckbox(
                            checked=uiState.onlyWithPhotos,
                            onCheckedChange=onOnlyWithPhotosChange
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.End
                ) {
                    Text(
                        uiState.branches.size.toString()+" สาขา",
                        style=MaterialTheme.typography.labelMedium,
                        color=scheme.onSurfaceVariant
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top=4.dp),
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


/** A floating search button that reveals the search field toward the left. */
@Composable
internal fun GallerySearchDock(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardVisible: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(value.isNotEmpty()) }
    var focusOnOpen by remember { mutableStateOf(false) }
    val requester=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current

    fun closeSearch() {
        focusOnOpen=false
        keyboard?.hide()
        focus.clearFocus()
        onValueChange("")
        expanded=false
    }

    BackHandler(enabled=expanded && !keyboardVisible) { closeSearch() }
    LaunchedEffect(expanded,focusOnOpen) {
        if (expanded && focusOnOpen) {
            // Let the field finish sliding into position before showing the IME.
            delay(240)
            requester.requestFocus()
            keyboard?.show()
            focusOnOpen=false
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start=14.dp,end=16.dp,top=8.dp,bottom=12.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) {
            androidx.compose.animation.AnimatedVisibility(
                visible=expanded,
                enter=expandHorizontally(tween(220),expandFrom=Alignment.End)+fadeIn(tween(180)),
                exit=shrinkHorizontally(tween(180),shrinkTowards=Alignment.End)+fadeOut(tween(120))
            ) {
                GlassSearchBar(
                    value=value,
                    onValueChange=onValueChange,
                    modifier=Modifier.fillMaxWidth().focusRequester(requester)
                )
            }
        }
        if (expanded) Spacer(Modifier.width(8.dp))
        GallerySearchButton(expanded=expanded,onClick={
            if(expanded) closeSearch()
            else {
                expanded=true
                focusOnOpen=true
            }
        })
    }
}

/** One-line 44dp filter controls in place of full-size 66dp cards. */
@Composable
private fun CompactBranchFilter(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier=Modifier,
    short: Boolean=false
) {
    val scheme=MaterialTheme.colorScheme
    val dark=scheme.background.luminance()<.35f
    val shape=RoundedCornerShape(13.dp)
    Row(
        modifier
            .clip(shape)
            .background(if(dark) Color(0xFF2F2A54) else Color(0xFFF3F0FF))
            .border(1.dp,if(dark) Color(0xFF61578D) else Color(0xFFE2D9F7),shape)
            .clickable(role=Role.Button,onClick=onClick)
            .heightIn(min=44.dp)
            .padding(horizontal=if (short) 7.dp else 11.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(4.dp)
    ) {
        if (!short) {
            Text(label,style=MaterialTheme.typography.labelSmall,
                color=scheme.onSurfaceVariant,maxLines=1)
        }
        Text(value,modifier=Modifier.weight(1f),
            style=MaterialTheme.typography.labelMedium,
            color=scheme.onSurface,maxLines=1,
            overflow=TextOverflow.Ellipsis)
        Icon(Icons.Outlined.KeyboardArrowDown,contentDescription=null,
            modifier=Modifier.size(16.dp),tint=scheme.onSurfaceVariant)
    }
}

/** Accessible icon-only photos filter fitting the rightmost 10%. */
@Composable
private fun PhotosOnlyCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme=MaterialTheme.colorScheme
    Box(
        Modifier.size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if(checked) scheme.primaryContainer else scheme.surfaceVariant)
            .clickable(role=Role.Checkbox, onClick={onCheckedChange(!checked)})
            .semantics {
                contentDescription="เฉพาะสาขาที่มีรูป"
                stateDescription=if(checked) "เปิด" else "ปิด"
            },
        contentAlignment=Alignment.Center
    ) {
        Icon(
            imageVector=if(checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription=null,
            tint=if(checked) scheme.primary else scheme.onSurfaceVariant,
            modifier=Modifier.size(25.dp)
        )
    }
}
