package com.branchphotovault.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import com.branchphotovault.ui.components.GlassFloatingActionButton as FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import com.branchphotovault.ui.components.GlassTextField as OutlinedTextField
import com.branchphotovault.ui.components.GlassScaffold as Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.branchphotovault.ui.components.GlassToolbar as TopAppBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.branchphotovault.R
import com.branchphotovault.ui.components.GlassSearchBar
import com.branchphotovault.ui.components.GlassDropdown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.branchphotovault.data.model.BranchSortOption
import com.branchphotovault.ui.components.BranchListItemCard
import com.branchphotovault.ui.viewmodel.AddBranchViewModel
import com.branchphotovault.ui.viewmodel.BranchListViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchListScreen(
    viewModel: BranchListViewModel,
    onOpenSettings: () -> Unit,
    onAddBranch: () -> Unit,
    onOpenBranch: (account: String, branchCode: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.keeper_brand),null,Modifier.size(36.dp).graphicsLayer { scaleX=1.4f; scaleY=1.4f })
                    Text("Image Keeper",style=androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } },
                actions = {
                    IconButton(
                        onClick = viewModel::syncBranches,
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Sync branches")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBranch) {
                Icon(Icons.Rounded.Add, contentDescription = "Add branch")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        BranchListContent(
            uiState = uiState,
            modifier = Modifier.padding(innerPadding),
            onSearchChange = viewModel::updateSearch,
            onAccountFilterChange = viewModel::updateAccountFilter,
            onSortChange = viewModel::updateSortOption,
            onOnlyWithPhotosChange = viewModel::updateOnlyWithPhotos,
            onBranchClick = onOpenBranch
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchListContent(
    uiState: com.branchphotovault.ui.viewmodel.BranchListUiState,
    modifier: Modifier = Modifier,
    onSearchChange: (String) -> Unit,
    onAccountFilterChange: (String?) -> Unit,
    onSortChange: (BranchSortOption) -> Unit,
    onOnlyWithPhotosChange: (Boolean) -> Unit,
    onBranchClick: (account: String, branchCode: String) -> Unit
) {
    var accountExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked=uiState.onlyWithPhotos,onCheckedChange=onOnlyWithPhotosChange,
                        modifier=Modifier.semantics { contentDescription="เฉพาะร้านที่มีภาพ" })
                    Text("เฉพาะร้านที่มีภาพ",style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.clickable { onOnlyWithPhotosChange(!uiState.onlyWithPhotos) })
                }
                GlassSearchBar(
                    value = uiState.searchText,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        GlassDropdown(
                            value = uiState.selectedAccount ?: "All Accounts",
                            label = "Account",
                            onClick = { accountExpanded = !accountExpanded }
                        )
                        DropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Accounts") },
                                onClick = {
                                    onAccountFilterChange(null)
                                    accountExpanded = false
                                }
                            )
                            AddBranchViewModel.ACCOUNTS.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account) },
                                    onClick = {
                                        onAccountFilterChange(account)
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        GlassDropdown(
                            value = uiState.sortOption.label,
                            label = "จัดเรียง",
                            onClick = { sortExpanded = !sortExpanded }
                        )
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            BranchSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSortChange(option)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                val lastSyncText = uiState.lastSyncAt?.let {
                    DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(Date(it)) + " " + DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(Date(it))
                } ?: "Never"

                Text(
                    text = "Last sync: $lastSyncText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.branches.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No branches found",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Sync the sheet or try a different search.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(
                items = uiState.branches,
                key = { "${it.account}_${it.branchCode}" }
            ) { branch ->
                BranchListItemCard(
                    branch = branch,
                    onClick = { onBranchClick(branch.account, branch.branchCode) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
