package com.branchphotovault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * Image Keeper's restrained archive UI. Component names remain unchanged so
 * all existing app screens continue to use the same APIs and navigation.
 * Inspired by unslop (Damir Sokolovsky, CC BY 4.0):
 * https://github.com/heydamir/unslop
 */
@Composable
fun glassBrush(): Brush = SolidColor(MaterialTheme.colorScheme.surface)

@Composable
fun glassBorder(): Color = MaterialTheme.colorScheme.outline.copy(alpha=.78f)

@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.35f
    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF17142F), Color(0xFF121326), Color(0xFF191536)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF4F2FF), Color(0xFFF8F8FF), Color(0xFFEEEAFE)))
    }
    Box(Modifier.fillMaxSize().background(background), content=content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier,content: @Composable ColumnScope.() -> Unit) {
    val shape=RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(.8.dp,glassBorder(),shape),
        content=content
    )
}

@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    GlassBackdrop {
        Scaffold(
            modifier=modifier,
            topBar=topBar,
            bottomBar=bottomBar,
            snackbarHost=snackbarHost,
            floatingActionButton=floatingActionButton,
            containerColor=Color.Transparent,
            content=content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassToolbar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val scheme=MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.35f
    // Light: blue → violet. Dark: deep navy → indigo. Readable white/chalk text.
    val chrome = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF171739), Color(0xFF27205E), Color(0xFF45258A)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF2477DA), Color(0xFF5551D9), Color(0xFF8051E8)))
    }
    val foreground = if (isDark) scheme.onPrimaryContainer else scheme.onPrimary
    TopAppBar(
        title=title,
        modifier=modifier.background(chrome),
        navigationIcon=navigationIcon,
        actions=actions,
        colors=TopAppBarDefaults.topAppBarColors(
            containerColor=Color.Transparent,
            scrolledContainerColor=Color.Transparent,
            titleContentColor=foreground,
            navigationIconContentColor=foreground,
            actionIconContentColor=foreground
        )
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,modifier: Modifier = Modifier,enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
)=GlassControl(onClick,modifier,enabled,true,content)

@Composable
fun GlassSecondaryButton(
    onClick: () -> Unit,modifier: Modifier = Modifier,enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
)=GlassControl(onClick,modifier,enabled,false,content)

@Composable
private fun GlassControl(
    onClick: () -> Unit,modifier: Modifier,enabled: Boolean,primary: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val source=remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .98f else 1f,tween(140),label="controlPress")
    val shape=RoundedCornerShape(12.dp)
    val scheme=MaterialTheme.colorScheme
    val fill=if(primary) scheme.primary else scheme.surfaceVariant
    val foreground=if(!enabled) scheme.onSurface.copy(alpha=.42f)
        else if(primary)scheme.onPrimary else scheme.onSurface
    CompositionLocalProvider(LocalContentColor provides foreground) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier.heightIn(min=48.dp)
                    .graphicsLayer{scaleX=scale;scaleY=scale}
                    .clip(shape)
                    .background(if(enabled)fill else fill.copy(alpha=.48f))
                    .border(.8.dp,if(primary) Color.Transparent else glassBorder(),shape)
                    .clickable(
                        interactionSource=source,
                        indication=ripple(),
                        enabled=enabled,
                        role=Role.Button,
                        onClick=onClick
                    )
                    .padding(horizontal=16.dp,vertical=12.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),
                verticalAlignment=Alignment.CenterVertically,
                content=content
            )
        }
    }
}

@Composable
fun GlassFloatingActionButton(onClick: () -> Unit,content: @Composable () -> Unit) {
    Surface(
        onClick=onClick,
        color=MaterialTheme.colorScheme.primary,
        contentColor=MaterialTheme.colorScheme.onPrimary,
        shape=RoundedCornerShape(16.dp),
        modifier=Modifier.size(56.dp)
    ) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){content()}
    }
}

@Composable
fun GlassTextField(
    value: String,onValueChange: (String) -> Unit,modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    enabled: Boolean = true,readOnly: Boolean = false,isError: Boolean = false
) {
    val shape=RoundedCornerShape(12.dp)
    OutlinedTextField(
        value,onValueChange,modifier=modifier,
        enabled=enabled,readOnly=readOnly,label=label,
        placeholder=placeholder,supportingText=supportingText,
        trailingIcon=trailingIcon,leadingIcon=leadingIcon,
        singleLine=singleLine,isError=isError,shape=shape,
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=MaterialTheme.colorScheme.primary,
            unfocusedBorderColor=glassBorder(),
            focusedContainerColor=MaterialTheme.colorScheme.surface,
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            disabledContainerColor=MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null,text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val shape=RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest=onDismissRequest,
        confirmButton=confirmButton,title=title,text=text,
        dismissButton=dismissButton,icon=icon,properties=properties,
        shape=shape,containerColor=MaterialTheme.colorScheme.surface,
        tonalElevation=0.dp,
        modifier=Modifier.border(.8.dp,glassBorder(),shape)
    )
}

@Composable
fun GlassSearchBar(
    value: String,onValueChange: (String) -> Unit,modifier: Modifier = Modifier
) {
    val keyboard=LocalSoftwareKeyboardController.current
    val shape=RoundedCornerShape(12.dp)
    BasicTextField(
        value,onValueChange,
        singleLine=true,
        textStyle=MaterialTheme.typography.bodyMedium.copy(
            color=MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
        keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
        modifier=modifier.heightIn(min=52.dp)
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(.8.dp,glassBorder(),shape),
        decorationBox={field->
            Row(
                Modifier.fillMaxWidth().heightIn(min=52.dp).padding(start=16.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search,null,Modifier.size(20.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if(value.isEmpty()) Text(
                        "ค้นหาชื่อร้าน รหัสสาขา หรือ Account",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=1,overflow=TextOverflow.Ellipsis
                    )
                    field()
                }
                if(value.isNotEmpty()) {
                    IconButton(onClick={onValueChange("")}) {
                        Icon(Icons.Outlined.Close,"ล้างคำค้นหา",Modifier.size(20.dp))
                    }
                } else Spacer(Modifier.width(12.dp))
            }
        }
    )
}

@Composable
fun GlassDropdown(
    label: String,value: String,onClick: () -> Unit,modifier: Modifier = Modifier
) {
    val shape=RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(.8.dp,glassBorder(),shape)
            .clickable(role=Role.Button,onClick=onClick)
            .heightIn(min=56.dp).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(label,style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.bodyMedium,
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(20.dp),
            tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
