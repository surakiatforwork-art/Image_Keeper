package com.branchphotovault.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.Circle
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

/** Display settings live inside every branch folder and are applied immediately. */
@Composable
fun BranchGalleryControls(
    config: GalleryViewConfig,
    onLayoutChange: (GalleryLayout) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onSortChange: (GalleryPhotoSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.background(scheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    GalleryModeButton(
                        selected = config.layout == GalleryLayout.GRID,
                        label = "ตาราง",
                        onClick = { onLayoutChange(GalleryLayout.GRID) }
                    ) { Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    GalleryModeButton(
                        selected = config.layout == GalleryLayout.LIST,
                        label = "รายการ",
                        onClick = { onLayoutChange(GalleryLayout.LIST) }
                    ) { Icon(Icons.Outlined.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                }

                Box {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (config.sort == GalleryPhotoSort.NEWEST_FIRST) "ล่าสุดก่อน" else "เก่าสุดก่อน",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("ล่าสุด → เก่าสุด") },
                            onClick = { onSortChange(GalleryPhotoSort.NEWEST_FIRST); sortExpanded = false },
                            leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("เก่าสุด → ล่าสุด") },
                            onClick = { onSortChange(GalleryPhotoSort.OLDEST_FIRST); sortExpanded = false },
                            leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) }
                        )
                    }
                }
            }
            if (config.layout == GalleryLayout.GRID) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "คอลัมน์",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    for (count in 3..6) {
                        val active = config.columns == count
                        Surface(
                            onClick = { onColumnsChange(count) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = if (active) scheme.primary else scheme.surfaceVariant,
                            contentColor = if (active) scheme.onPrimary else scheme.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else {
                Text(
                    "รูปภาพ · ชื่อไฟล์ · วันที่",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GalleryModeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primary else Color.Transparent,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
    ) {
        Row(
            Modifier.height(38.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium)
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
