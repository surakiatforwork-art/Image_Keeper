package com.branchphotovault.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.data.repository.GalleryLayout
import com.branchphotovault.data.repository.GalleryPhotoSort
import com.branchphotovault.data.repository.GalleryViewConfig
import java.io.File
import java.util.Date

/**
 * Separate sorting from display configuration:
 * sorting is always visible in the toolbar; grid/list and column density live under ⋮.
 */
@Composable
fun GallerySortDropdown(
    config: GalleryViewConfig,
    onSortChange: (GalleryPhotoSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Outlined.Sort,
                contentDescription = "จัดเรียงภาพ",
                modifier = Modifier.size(20.dp),
                tint = scheme.onPrimary
            )
            Text(
                if (config.sort == GalleryPhotoSort.NEWEST_FIRST) "ล่าสุด" else "เก่าสุด",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimary,
                maxLines = 1
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.onPrimary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GallerySortChoice(
                label = "ล่าสุด → เก่าสุด",
                selected = config.sort == GalleryPhotoSort.NEWEST_FIRST,
                onClick = {
                    onSortChange(GalleryPhotoSort.NEWEST_FIRST)
                    expanded = false
                }
            )
            GallerySortChoice(
                label = "เก่าสุด → ล่าสุด",
                selected = config.sort == GalleryPhotoSort.OLDEST_FIRST,
                onClick = {
                    onSortChange(GalleryPhotoSort.OLDEST_FIRST)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun GallerySortChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) Icon(
                Icons.Outlined.Check,
                contentDescription = "เลือกอยู่",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick
    )
}

/** Only a three-dot toolbar action — no additional gallery configuration card. */
@Composable
fun GalleryViewOptionsMenu(
    config: GalleryViewConfig,
    onLayoutChange: (GalleryLayout) -> Unit,
    onColumnsChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "เพิ่มเติม: มุมมองภาพและจำนวนคอลัมน์")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp)
        ) {
            Text(
                "รูปแบบการแสดงผล",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            DropdownMenuItem(
                text = { Text("ตาราง") },
                leadingIcon = {
                    Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (config.layout == GalleryLayout.GRID) {
                        Icon(Icons.Outlined.Check, contentDescription = "เลือกอยู่", tint = scheme.primary)
                    }
                },
                onClick = {
                    onLayoutChange(GalleryLayout.GRID)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("รายการ") },
                leadingIcon = {
                    Icon(Icons.Outlined.ViewList, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (config.layout == GalleryLayout.LIST) {
                        Icon(Icons.Outlined.Check, contentDescription = "เลือกอยู่", tint = scheme.primary)
                    }
                },
                onClick = {
                    onLayoutChange(GalleryLayout.LIST)
                    expanded = false
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = scheme.outlineVariant
            )
            Text(
                "จำนวนคอลัมน์ (มุมมองตาราง)",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (count in 3..6) {
                    val selected = config.columns == count && config.layout == GalleryLayout.GRID
                    Surface(
                        onClick = {
                            // Selecting a column count immediately changes to the grid view.
                            onColumnsChange(count)
                            expanded = false
                        },
                        modifier = Modifier.size(width = 48.dp, height = 44.dp),
                        color = if (selected) scheme.primary else scheme.surfaceVariant,
                        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** A compact file-manager row, including date and original stored filename. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BranchPhotoListItem(
    photo: PhotoEntity,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val date = remember(photo.createdAt) {
        val value = Date(photo.createdAt)
        DateFormat.getMediumDateFormat(context).format(value) + "  ·  " +
            DateFormat.getTimeFormat(context).format(value)
    }
    val name = remember(photo.localMainPath) { File(photo.localMainPath).name.ifEmpty { "รูปภาพ" } }
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = .12f) else scheme.surface)
            .border(1.dp, if (selected) scheme.primary.copy(alpha = .45f) else scheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = File(photo.localThumbPath),
            contentDescription = null,
            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionActive) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "เลือกแล้ว" else "ยังไม่เลือก",
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
