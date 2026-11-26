package com.branchphotovault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.branchphotovault.ui.components.GlassButton as Button
import com.branchphotovault.ui.components.GlassSecondaryButton as SecondaryButton
import com.branchphotovault.ui.components.GlassCard as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.branchphotovault.ui.components.GlassTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
import com.branchphotovault.ui.components.GlassScaffold as Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.branchphotovault.ui.components.GlassToolbar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.branchphotovault.ui.components.AppThemePicker
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
    val selectedTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตั้งค่า") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal=16.dp,vertical=16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppThemePicker(
                selected = selectedTheme,
                onSelected = viewModel::updateThemeMode,
                modifier = Modifier.fillMaxWidth()
            )
            PhotoTransferCard()
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("การเชื่อมต่อ Apps Script") },
                supportingText = { Text("ที่อยู่เว็บแอปสำหรับซิงก์ข้อมูลสาขา") },
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.retentionMonths.toString(),
                onValueChange = viewModel::updateRetentionMonths,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ระยะเวลาเก็บภาพ (เดือน)") },
                supportingText = { Text("ค่าเริ่มต้น 3 เดือน") },
                singleLine = true
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("โฟลเดอร์แกลเลอรี่",style=androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("Pictures/Image Keeper",style=androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ชื่อไฟล์: Account_เลขสาขา_วันเวลาบันทึก_รหัสไม่ซ้ำ.jpg",
                        style=androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsChoiceCard(
                title = "ขนาดภาพที่บันทึก",
                supportingText = "มีผลกับภาพที่ถ่ายหรือนำเข้าใหม่",
                options = ImageSizeOption.entries.toList(),
                selectedOption = uiState.imageSizeOption,
                optionLabel = { it.label },
                onOptionSelected = viewModel::updateImageSizeOption
            )
            SettingsChoiceCard(
                title = "อัตราส่วนภาพที่บันทึก",
                supportingText = "ภาพใหม่จะถูกครอปจากกึ่งกลางก่อนบันทึก",
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
                    Text("บันทึกการตั้งค่า")
                }
            }
            SecondaryButton(
                onClick = viewModel::checkApi,
                enabled = !uiState.isCheckingApi,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCheckingApi) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("ตรวจสอบการเชื่อมต่อ")
                }
            }
            SecondaryButton(
                onClick = viewModel::cleanupExpiredPhotos,
                enabled = !uiState.isCleaningUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCleaningUp) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("ล้างภาพที่หมดอายุ")
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
                Text(text = title,style=androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(text = supportingText,
                    style=androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
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
