package com.branchphotovault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.branchphotovault.data.model.BranchListItem

/** Photo folder rows matching the approved light/dark gallery mockup. */
@Composable
fun BranchListItemCard(
    branch: BranchListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .35f
    val shape = RoundedCornerShape(21.dp)
    val fill = if (dark)
        Brush.horizontalGradient(listOf(Color(0xFF2B2A5C), Color(0xFF24234B)))
    else Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF7F5FF)))
    val outline = if (dark) Color(0xFF5D568D) else Color(0xFFD8CEF9)
    Row(
        modifier = modifier.fillMaxWidth()
            .shadow(if (dark) 6.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(1.dp, outline, shape)
            .clickable(onClick=onClick)
            .padding(horizontal=13.dp, vertical=15.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(13.dp)
    ) {
        val iconShape=RoundedCornerShape(17.dp)
        Box(
            modifier=Modifier.size(62.dp).clip(iconShape)
                .background(Brush.linearGradient(
                    if(dark) listOf(Color(0xFF8868EE),Color(0xFF432897))
                    else listOf(Color(0xFFDCD9FF),Color(0xFFEEEAFF))
                )),
            contentAlignment=Alignment.Center
        ) {
            Box(
                modifier=Modifier.size(43.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF9D79FF),Color(0xFF5C40D8),Color(0xFF432EB9))
                    )),
                contentAlignment=Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription=null,
                    tint=Color.White,
                    modifier=Modifier.size(29.dp)
                )
            }
        }
        Column(
            modifier=Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(4.dp)
        ) {
            Text(
                branch.shopName,
                style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.SemiBold,
                color=colors.onSurface,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            Text(
                branch.account+" · "+branch.branchCode,
                style=MaterialTheme.typography.bodyMedium,
                color=colors.onSurfaceVariant,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary,
                    contentDescription=null,
                    tint=if(dark) Color(0xFFC2A6FF) else colors.primary,
                    modifier=Modifier.size(17.dp))
                Text(
                    branch.photoCount.toString()+" รูป",
                    style=MaterialTheme.typography.bodySmall,
                    color=colors.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription="เปิดสาขา",
            tint=colors.onSurfaceVariant,
            modifier=Modifier.size(26.dp)
        )
    }
}
