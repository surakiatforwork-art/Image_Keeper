package com.branchphotovault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.branchphotovault.data.model.BranchListItem

@Composable
fun BranchListItemCard(
    branch:BranchListItem,
    onClick:()->Unit,
    modifier:Modifier=Modifier
) {
    GlassCard(modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)),
                contentAlignment=Alignment.Center
            ) {
                Icon(Icons.Outlined.Storefront,null,Modifier.size(22.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(branch.shopName,
                    style=MaterialTheme.typography.titleMedium,
                    color=MaterialTheme.colorScheme.onSurface,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${branch.account} · ${branch.branchCode}",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment=Alignment.End,
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(branch.photoCount.toString(),
                    style=MaterialTheme.typography.titleMedium,
                    color=MaterialTheme.colorScheme.onSurface)
                Text("รูป",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
