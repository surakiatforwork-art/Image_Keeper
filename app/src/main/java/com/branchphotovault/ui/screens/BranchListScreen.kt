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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
                title = { Text("Branches") },
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
            onSortChange = viewModel::updateSortOption,
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
    onSortChange: (BranchSortOption) -> Unit,
    onBranchClick: (account: String, branchCode: String) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.searchText,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search account, branch code, shop name") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                        expanded = sortExpanded,
                        onExpandedChange = { sortExpanded = !sortExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.sortOption.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sort By") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
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

                val lastSyncText = uiState.lastSyncAt?.let {
                    DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(Date(it)) + " " + DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(Date(it))
                } ?: "Never"

                Text(
                    text = "Last sync: $lastSyncText",
                    style = MaterialTheme.typography.bodyMedium
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
