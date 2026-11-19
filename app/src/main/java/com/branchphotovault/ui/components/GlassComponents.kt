package com.branchphotovault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role

@Composable
fun glassBrush(): Brush = if (MaterialTheme.colorScheme.background.luminance() < .3f)
    Brush.linearGradient(listOf(Color(0xEB34314D),Color(0xE025243A)))
else Brush.linearGradient(listOf(Color(0xEFFFFFFF),Color(0xA6F7F4FF)))

@Composable
fun glassBorder(): Color = Color.White.copy(alpha=if(MaterialTheme.colorScheme.background.luminance()<.3f) .12f else .65f)

@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    Box(Modifier.fillMaxSize().background(if (dark) Brush.linearGradient(listOf(Color(0xFF10111D),Color(0xFF211C35),Color(0xFF152A35)))
        else Brush.linearGradient(listOf(Color(0xFFF4EFFB),Color(0xFFE0DAF3),Color(0xFFE7F2FA),Color(0xFFE5D8F5))))) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(
            listOf(if (dark) Color(0x203EA8BD) else Color(0x88C4E5F8),Color.Transparent),center = Offset(850f,300f),radius = 950f)))
        content()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier,content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.shadow(7.dp,shape,ambientColor = Color(0xFF6D5D9C).copy(alpha=.12f),spotColor = Color(0xFF6D5D9C).copy(alpha=.12f))
        .clip(shape).background(glassBrush()).border(.8.dp,glassBorder(),shape),content = content)
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
        Scaffold(modifier = modifier,topBar = topBar,bottomBar = bottomBar,snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,containerColor = Color.Transparent,content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassToolbar(title: @Composable () -> Unit,modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = title,modifier = modifier.background(glassBrush()),navigationIcon = navigationIcon,actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface))
}

@Composable
fun GlassButton(onClick: () -> Unit,modifier: Modifier = Modifier,enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) = GlassControl(onClick,modifier,enabled,true,content)

@Composable
fun GlassSecondaryButton(onClick: () -> Unit,modifier: Modifier = Modifier,enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) = GlassControl(onClick,modifier,enabled,false,content)

@Composable
private fun GlassControl(onClick: () -> Unit,modifier: Modifier,enabled: Boolean,primary: Boolean,
    content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f,tween(120),label = "glassPress")
    val shape = RoundedCornerShape(20.dp)
    val brush = if (primary && enabled) Brush.linearGradient(listOf(Color(0xFFA88AEE),Color(0xFF7955DF),Color(0xFF6541C8))) else glassBrush()
    val foreground = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha=.4f)
        else if (primary) Color.White else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides foreground) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(modifier.heightIn(min=48.dp).graphicsLayer { scaleX=scale; scaleY=scale }
                .shadow(if (primary && enabled) 6.dp else 2.dp,shape,
                    ambientColor=Color(0xFF6D5D9C).copy(alpha=.16f),spotColor=Color(0xFF6D5D9C).copy(alpha=.16f))
                .clip(shape).background(brush)
                .border(.8.dp,if(primary) Color.White.copy(alpha=.3f) else glassBorder(),shape)
                .clickable(interactionSource=source,indication=ripple(),enabled=enabled,role=Role.Button,onClick=onClick)
                .padding(horizontal=16.dp,vertical=10.dp),
                horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}

@Composable
fun GlassFloatingActionButton(onClick: () -> Unit,content: @Composable () -> Unit) {
    Surface(onClick=onClick,color=Color.Transparent,contentColor=Color.White,shape=CircleShape,
        border=BorderStroke(.8.dp,Color.White.copy(alpha=.3f)),
        modifier=Modifier.size(56.dp).shadow(8.dp,CircleShape).background(
            Brush.linearGradient(listOf(Color(0xFFB89AFF),Color(0xFF7955DF))),CircleShape)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { content() }
    }
}

@Composable
fun GlassTextField(value: String,onValueChange: (String) -> Unit,modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,singleLine: Boolean = false,enabled: Boolean = true,
    readOnly: Boolean = false,isError: Boolean = false) {
    val shape=RoundedCornerShape(20.dp)
    OutlinedTextField(value,onValueChange,modifier.clip(shape).background(glassBrush()),enabled=enabled,readOnly=readOnly,
        label=label,placeholder=placeholder,supportingText=supportingText,trailingIcon=trailingIcon,leadingIcon=leadingIcon,
        singleLine=singleLine,isError=isError,shape=shape,colors=OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor=MaterialTheme.colorScheme.outline.copy(alpha=.6f),
            focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent))
}

@Composable
fun GlassDialog(onDismissRequest: () -> Unit,confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null,text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,icon: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()) {
    val shape=RoundedCornerShape(28.dp)
    AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,title=title,text=text,
        dismissButton=dismissButton,icon=icon,properties=properties,shape=shape,
        containerColor=Color.Transparent,tonalElevation=0.dp,
        modifier=Modifier.clip(shape).background(glassBrush()).border(.8.dp,glassBorder(),shape))
}

@Composable
fun GlassSearchBar(value: String,onValueChange: (String) -> Unit,modifier: Modifier = Modifier) {
    val keyboard=LocalSoftwareKeyboardController.current
    val shape=RoundedCornerShape(24.dp)
    BasicTextField(value,onValueChange,singleLine=true,
        textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={ keyboard?.hide() }),
        modifier=modifier.heightIn(min=48.dp).clip(shape).background(glassBrush()).border(.8.dp,glassBorder(),shape),
        decorationBox={ field ->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if(value.isEmpty()) Text("ค้นหา Account, รหัสสาขา, ชื่อร้าน",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    field()
                }
                if(value.isNotEmpty()) IconButton(onClick={ onValueChange("") }) { Icon(Icons.Outlined.Close,"ล้างคำค้นหา",Modifier.size(20.dp)) }
                else Spacer(Modifier.width(14.dp))
            }
        })
}

@Composable
fun GlassDropdown(label: String,value: String,onClick: () -> Unit,modifier: Modifier = Modifier) {
    val shape=RoundedCornerShape(20.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(glassBrush()).border(.8.dp,glassBorder(),shape)
        .clickable(onClick=onClick).heightIn(min=56.dp).padding(horizontal=13.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(20.dp))
    }
}
