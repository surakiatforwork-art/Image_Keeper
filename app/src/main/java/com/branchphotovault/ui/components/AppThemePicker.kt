package com.branchphotovault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.branchphotovault.data.model.AppThemeMode

/**
 * Theme is a user preference, not a temporary Settings form value:
 * switching a tile updates the whole app and writes to DataStore immediately.
 */
@Composable
fun AppThemePicker(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("รูปลักษณ์แอป", style = MaterialTheme.typography.titleMedium)
            Text(
                "เลือกธีมที่ใช้งานสะดวกที่สุด",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeTile(
                    mode = AppThemeMode.SYSTEM,
                    icon = Icons.Outlined.PhoneAndroid,
                    selected = selected == AppThemeMode.SYSTEM,
                    onClick = { onSelected(AppThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                AppThemeTile(
                    mode = AppThemeMode.LIGHT,
                    icon = Icons.Outlined.LightMode,
                    selected = selected == AppThemeMode.LIGHT,
                    onClick = { onSelected(AppThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                AppThemeTile(
                    mode = AppThemeMode.DARK,
                    icon = Icons.Outlined.DarkMode,
                    selected = selected == AppThemeMode.DARK,
                    onClick = { onSelected(AppThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                selected.description + " • บันทึกการเลือกอัตโนมัติ",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppThemeTile(
    mode: AppThemeMode,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(94.dp),
        shape = shape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(8.dp))
            Text(mode.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
