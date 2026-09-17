package com.branchphotovault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.branchphotovault.data.model.ImageAspectRatioOption
import com.branchphotovault.data.model.ImageSizeOption
import com.branchphotovault.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Apps Script URL") },
                supportingText = { Text("Edit the full web app URL here.") }
            )
            OutlinedTextField(
                value = uiState.retentionMonths.toString(),
                onValueChange = viewModel::updateRetentionMonths,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Retention Months") },
                supportingText = { Text("Default cleanup target is 3 months.") },
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.exportFolderPattern,
                onValueChange = viewModel::updateExportPattern,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Export Folder Pattern") },
                supportingText = { Text("Supported placeholders: {account}, {branchCode}, {shopName}") }
            )
            SettingsChoiceCard(
                title = "Saved Image Size",
                supportingText = "Applied to new photos captured or imported into the app.",
                options = ImageSizeOption.entries.toList(),
                selectedOption = uiState.imageSizeOption,
                optionLabel = { it.label },
                onOptionSelected = viewModel::updateImageSizeOption
            )
            SettingsChoiceCard(
                title = "Saved Image Aspect Ratio",
                supportingText = "New photos are center-cropped before saving. Gallery save uses the processed result.",
                options = ImageAspectRatioOption.entries.toList(),
                selectedOption = uiState.imageAspectRatioOption,
                optionLabel = { it.label },
                onOptionSelected = viewModel::updateImageAspectRatioOption
            )
            Button(
                onClick = viewModel::saveSettings,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Save Settings")
                }
            }
            Button(
                onClick = viewModel::checkApi,
                enabled = !uiState.isCheckingApi,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCheckingApi) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Run API Health Check")
                }
            }
            Button(
                onClick = viewModel::cleanupExpiredPhotos,
                enabled = !uiState.isCleaningUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCleaningUp) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Cleanup Expired Photos")
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsChoiceCard(
    title: String,
    supportingText: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title)
                Text(text = supportingText)
            }
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = option == selectedOption,
                        onClick = null
                    )
                    Text(
                        text = optionLabel(option)
                    )
                }
            }
        }
    }
}
