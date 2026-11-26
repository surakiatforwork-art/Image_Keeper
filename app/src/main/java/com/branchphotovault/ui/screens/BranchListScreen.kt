package com.branchphotovault.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Unslop: branch photos are the product. Quiet archive chrome, consistent
 * 4dp spacing, restrained single-accent controls, original ghost artwork.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchListScreen(
    viewModel: BranchListViewModel,
    onOpenSettings: () -> Unit,
    onAddBranch: () -> Unit,
    onOpenBranch: (account: String, branchCode: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState=remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message=uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }
    GlassScaffold(
        topBar={
            GlassToolbar(
                title={
                    Row(
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Image(painterResource(R.drawable.keeper_brand),
                            contentDescription="Image Keeper",modifier=Modifier.size(40.dp))
                        Column(verticalArrangement=Arrangement.spacedBy(0.dp)) {
                            Text("Image Keeper",
                                style=MaterialTheme.typography.titleMedium,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("Branch Photo Vault",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.84f))
                        }
                    }
                },
                actions={
                    IconButton(
                        onClick=viewModel::syncBranches,
                        enabled=!uiState.isSyncing
                    ) {
                        if(uiState.isSyncing) CircularProgressIndicator(
                            modifier=Modifier.size(20.dp),strokeWidth=2.dp
                        ) else Icon(Icons.Rounded.Refresh,
                            contentDescription="ซิงก์รายการสาขา")
                    }
                    IconButton(onClick=onOpenSettings) {
                        Icon(Icons.Rounded.Settings,contentDescription="ตั้งค่า")
                    }
                }
            )
        },
        floatingActionButton={
            GlassFloatingActionButton(onClick=onAddBranch) {
                Icon(Icons.Rounded.Add,contentDescription="เพิ่มสาขา")
            }
        },
        snackbarHost={SnackbarHost(hostState=snackbarHostState)}
    ) {innerPadding->
        BranchListContent(
            uiState=uiState,modifier=Modifier.padding(innerPadding),
            onSearchChange=viewModel::updateSearch,
            onAccountFilterChange=viewModel::updateAccountFilter,
            onSortChange=viewModel::updateSortOption,
            onOnlyWithPhotosChange=viewModel::updateOnlyWithPhotos,
            onBranchClick=onOpenBranch
        )
    }
}

@Composable
private fun BranchListContent(
    uiState:BranchListUiState,
    modifier:Modifier=Modifier,
    onSearchChange:(String)->Unit,
    onAccountFilterChange:(String?)->Unit,
    onSortChange:(BranchSortOption)->Unit,
    onOnlyWithPhotosChange:(Boolean)->Unit,
    onBranchClick:(account:String,branchCode:String)->Unit
) {
    var accountExpanded by remember {mutableStateOf(false)}
    var sortExpanded by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val isFiltered=uiState.searchText.isNotBlank() ||
        uiState.selectedAccount!=null || uiState.onlyWithPhotos

    LazyColumn(
        modifier=modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=88.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                GlassSearchBar(
                    value=uiState.searchText,
                    onValueChange=onSearchChange,
                    modifier=Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        GlassDropdown(
                            value=uiState.selectedAccount ?: "ทุก Account",
                            label="Account",onClick={accountExpanded=true}
                        )
                        DropdownMenu(expanded=accountExpanded,
                            onDismissRequest={accountExpanded=false}) {
                            DropdownMenuItem(text={Text("ทุก Account")},onClick={
                                onAccountFilterChange(null)
                                accountExpanded=false
                            })
                            AddBranchViewModel.ACCOUNTS.forEach {account->
                                DropdownMenuItem(text={Text(account)},onClick={
                                    onAccountFilterChange(account)
                                    accountExpanded=false
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        GlassDropdown(value=uiState.sortOption.label,
                            label="จัดเรียง",onClick={sortExpanded=true})
                        DropdownMenu(expanded=sortExpanded,
                            onDismissRequest={sortExpanded=false}) {
                            BranchSortOption.entries.forEach {option->
                                DropdownMenuItem(text={Text(option.label)},onClick={
                                    onSortChange(option)
                                    sortExpanded=false
                                })
                            }
                        }
                    }
                }

                Row(
                    modifier=Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment=Alignment.CenterVertically,
                        modifier=Modifier.clickable {
                            onOnlyWithPhotosChange(!uiState.onlyWithPhotos)
                        }
                    ) {
                        Checkbox(
                            checked=uiState.onlyWithPhotos,
                            onCheckedChange=onOnlyWithPhotosChange,
                            modifier=Modifier.semantics {
                                contentDescription="เฉพาะสาขาที่มีรูป"
                            }
                        )
                        Text("เฉพาะสาขาที่มีรูป",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "ทั้งหมด ${uiState.branches.size} สาขา",
                        style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier=Modifier.fillMaxWidth().padding(top=4.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Storefront, contentDescription=null,
                        tint=MaterialTheme.colorScheme.primary, modifier=Modifier.size(20.dp))
                    Text("รายการสาขา", style=MaterialTheme.typography.titleMedium)
                }
                val lastSyncText=uiState.lastSyncAt?.let {
                    DateFormat.getMediumDateFormat(context).format(Date(it))+" · "+
                        DateFormat.getTimeFormat(context).format(Date(it))
                } ?: "ยังไม่เคยซิงก์"
                Text(
                    "ซิงก์ล่าสุด $lastSyncText",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if(uiState.branches.isEmpty()) {
            item {
                Column(
                    modifier=Modifier.fillMaxWidth()
                        .padding(top=64.dp,bottom=40.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp)),
                        contentAlignment=Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary,contentDescription=null,
                            modifier=Modifier.size(28.dp),
                            tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if(isFiltered) "ไม่พบสาขาที่ตรงกับตัวกรอง"
                        else "ยังไม่มีสาขาในรายการ",
                        style=MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if(isFiltered) "ลองเปลี่ยนคำค้นหาหรือตัวกรอง"
                        else "กดซิงก์ด้านบนเพื่อโหลดรายชื่อสาขา",
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }else{
            items(uiState.branches,
                key={"${it.account}_${it.branchCode}"}) {branch->
                BranchListItemCard(
                    branch=branch,
                    onClick={onBranchClick(branch.account,branch.branchCode)}
                )
            }
        }
    }
}
