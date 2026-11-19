package com.branchphotovault.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

@Stable
class ImageZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
    var imageSize by mutableStateOf(IntSize.Zero)
    val isZoomed get() = scale > 1.001f
    fun reset() { scale=1f; offset=Offset.Zero }
    fun transform(zoom: Float,pan: Offset,centroid: Offset) {
        if (!zoom.isFinite() || !pan.x.isFinite() || !pan.y.isFinite() || !centroid.x.isFinite() || !centroid.y.isFinite()) return
        val next=(scale*zoom).coerceIn(1f,6f)
        val ratio=next/scale
        val center=Offset(viewport.width/2f,viewport.height/2f)
        val candidate=offset*ratio+(centroid-center)*(1-ratio)+pan
        val fit=if(imageSize.width>0 && imageSize.height>0) min(viewport.width.toFloat()/imageSize.width,viewport.height.toFloat()/imageSize.height) else 1f
        val fitWidth=if(imageSize.width>0) imageSize.width*fit else viewport.width.toFloat()
        val fitHeight=if(imageSize.height>0) imageSize.height*fit else viewport.height.toFloat()
        val maxX=max(0f,(fitWidth*next-viewport.width)/2)
        val maxY=max(0f,(fitHeight*next-viewport.height)/2)
        scale=next
        offset=if(next==1f) Offset.Zero else Offset(candidate.x.coerceIn(-maxX,maxX),candidate.y.coerceIn(-maxY,maxY))
    }
}

@Composable
fun ZoomableImageViewport(
    imageKey: Any,
    modifier: Modifier = Modifier,
    imageSize: IntSize = IntSize.Zero,
    onZoomChanged: (Boolean) -> Unit = {},
    state: ImageZoomState = remember(imageKey) { ImageZoomState() },
    content: @Composable BoxScope.() -> Unit
) {
    val notify by rememberUpdatedState(onZoomChanged)
    LaunchedEffect(imageSize) { state.imageSize=imageSize; state.transform(1f,Offset.Zero,Offset.Zero) }
    LaunchedEffect(state.isZoomed) { notify(state.isZoomed) }
    Box(modifier.clipToBounds().onSizeChanged { state.viewport=it; state.transform(1f,Offset.Zero,Offset.Zero) }
        .semantics {
            stateDescription=if(state.isZoomed) "Zoom ${(state.scale*100).toInt()}%" else "Fit"
            customActions=listOf(CustomAccessibilityAction("คืนขนาดเดิม") { state.reset(); true },
                CustomAccessibilityAction("ขยายภาพ") {
                    state.transform(1.5f,Offset.Zero,Offset(state.viewport.width/2f,state.viewport.height/2f)); true
                })
        }
        .pointerInput(imageKey,state) { detectTapGestures(onDoubleTap={ state.reset() }) }
        .pointerInput(imageKey,state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                var claimed=false
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val pointers=event.changes.count { it.pressed }
                    if(pointers>=2 || state.isZoomed) claimed=true
                    if(claimed) {
                        val centroid=event.calculateCentroid()
                        if(centroid.x.isFinite() && centroid.y.isFinite()) state.transform(event.calculateZoom(),event.calculatePan(),centroid)
                        event.changes.forEach { if(it.positionChanged()) it.consume() }
                    }
                } while(event.changes.any { it.pressed })
            }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX=state.scale; scaleY=state.scale
            translationX=state.offset.x; translationY=state.offset.y
        },content=content)
    }
}
