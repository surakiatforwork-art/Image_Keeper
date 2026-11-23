package com.branchphotovault.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.branchphotovault.retouch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.max

private enum class RetouchTool(val label: String) {
    PAN("เลื่อน/ซูม"), HEAL("ซ่อมหนวด"), RESTORE("คืนภาพ"),
    FRAME("กรอบ"), PAINT("ทา AI")
}
private data class EditSnapshot(val strokes: List<RetouchStroke> = emptyList(), val bounds: RetouchBounds? = null)

@Composable
fun HoldOriginalButton(enabled: Boolean, onHolding: (Boolean) -> Unit) {
    val holding by rememberUpdatedState(onHolding)
    DisposableEffect(enabled) { onDispose { holding(false) } }
    Box(Modifier.size(48.dp).testTag("hold-original").semantics {
        contentDescription="กดค้างดูต้นฉบับ ปล่อยเพื่อดูภาพแต่ง"
        role=Role.Button
        if(!enabled) disabled()
        customActions=if(enabled) listOf(
            CustomAccessibilityAction("แสดงต้นฉบับ") { holding(true); true },
            CustomAccessibilityAction("กลับภาพแต่ง") { holding(false); true }) else emptyList()
    }.pointerInput(enabled) {
        if(enabled) detectTapGestures(onPress={
            holding(true)
            try { tryAwaitRelease() } finally { holding(false) }
        })
    },contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Compare,null,tint=if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.35f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualRetouchEditor(source: Bitmap, generated: Bitmap?, strength: Float, busy: Boolean,
    detectedBounds: RetouchBounds?, onZoomChanged: (Boolean) -> Unit, onEditingChanged: (Boolean) -> Unit,
    onRendered: (Bitmap?) -> Unit, previewHeight: Dp = 340.dp,
    controlsHeight: Dp = 156.dp) {
    var tool by remember(source,generated) { mutableStateOf(RetouchTool.PAN) }
    var before by remember { mutableStateOf(false) }
    var radius by remember { mutableFloatStateOf(.035f) }
    var donor by remember(source,generated) { mutableStateOf<RetouchPoint?>(null) }
    var pickingSkin by remember(source,generated) { mutableStateOf(false) }
    val history=remember(source,generated) { mutableStateListOf(EditSnapshot()) }
    val redo=remember(source,generated) { mutableStateListOf<EditSnapshot>() }
    val edit=history.last()
    var rendered by remember(source,generated) { mutableStateOf<Bitmap?>(null) }
    var previewLayer by remember(source,generated) { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<List<RetouchPoint>>(emptyList()) }
    val report by rememberUpdatedState(onRendered)
    val edited by rememberUpdatedState(onEditingChanged)
    val zoom=remember(source) { ImageZoomState() }
    val renderLock=remember(source) { Mutex() }
    fun commit(next: EditSnapshot) {
        report(null)
        history.add(next)
        if(history.size>31) history.removeAt(0)
        redo.clear()
    }
    LaunchedEffect(tool) { edited(tool!=RetouchTool.PAN) }
    LaunchedEffect(tool) { if(tool!=RetouchTool.HEAL) pickingSkin=false }
    DisposableEffect(Unit) { onDispose { edited(false) } }
    // Stage 1: prepare transparent edit pixels only when brush/frame changes.
    // This prevents full-resolution bitmap copies for every slider tick.
    LaunchedEffect(source,generated,edit) {
        report(null)
        rendered=null
        previewLayer=null
        renderError=null
        if(generated==null) return@LaunchedEffect
        rendering=true
        try {
            val prepared=withContext(Dispatchers.Default) {
                ManualRetouch.prepareLayer(generated,edit.strokes,edit.bounds)
            }
            previewLayer=prepared
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { renderError="เตรียมภาพรีทัชไม่สำเร็จ" }
        finally { rendering=false }
    }
    // Stage 2: the preview is blended on the GPU, without any CPU work.
    // Only materialize full pixels after the slider settles, for saving.
    LaunchedEffect(source,previewLayer,strength) {
        report(null)
        rendered=null
        val overlay=previewLayer ?: return@LaunchedEffect
        delay(180)
        var candidate: Bitmap?=null
        try {
            val output=withContext(Dispatchers.Default) {
                renderLock.withLock {
                    coroutineContext.ensureActive()
                    ManualRetouch.composeLayer(source,overlay,strength).also { candidate=it }
                }
            }
            coroutineContext.ensureActive()
            rendered=output
            report(output)
            candidate=null
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { renderError="เตรียมภาพบันทึกไม่สำเร็จ" }
        finally { candidate?.recycle() }
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        ZoomableImageViewport(source,Modifier.fillMaxWidth().height(previewHeight).testTag("retouch-preview"),
            IntSize(source.width,source.height),onZoomChanged,state=zoom,
            gesturesEnabled=true,singleFingerPan=tool==RetouchTool.PAN) {
            Image(source.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            if(!before) previewLayer?.let { layer ->
                Image(layer.asImageBitmap(),null,Modifier.fillMaxSize(),
                    alpha=strength.coerceIn(0f,1f),contentScale=ContentScale.Fit)
            }
            Canvas(Modifier.fillMaxSize().semantics { stateDescription=if(before) "Original" else "Edited" }
                .pointerInput(tool,busy,rendering,radius,edit,pickingSkin,donor) {
                    if(tool==RetouchTool.PAN || busy || rendering) return@pointerInput
                    val fit=min(size.width.toFloat()/source.width,size.height.toFloat()/source.height)
                    val width=source.width*fit; val height=source.height*fit
                    val left=(size.width-width)/2; val top=(size.height-height)/2
                    fun point(p: Offset)=RetouchPoint(((p.x-left)/width).coerceIn(0f,1f),((p.y-top)/height).coerceIn(0f,1f))
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                        val valid=down.position.x in left..left+width && down.position.y in top..top+height
                        if(!valid) return@awaitEachGesture
                        down.consume()
                        report(null)
                        drag=listOf(point(down.position))
                        var cancelled=false
                        var committed=false
                        try {
                            do {
                                val event=awaitPointerEvent(PointerEventPass.Initial)
                                if(event.changes.count { it.pressed }>1) {
                                    cancelled=true
                                    drag=emptyList()
                                }
                                if(!cancelled) event.changes.forEach { change ->
                                    if(change.id==down.id && drag.size<1500)
                                        drag=drag+point(change.position)
                                    change.consume()
                                }
                            } while(event.changes.any { it.pressed })
                        if(!cancelled && drag.isNotEmpty()) {
                            if(tool==RetouchTool.HEAL && pickingSkin) {
                                donor=drag.last()
                                pickingSkin=false
                                committed=true
                            } else if(tool==RetouchTool.FRAME) {
                                val a=drag.first(); val b=drag.last()
                                if(kotlin.math.abs(a.x-b.x)>.02f && kotlin.math.abs(a.y-b.y)>.02f) {
                                    commit(edit.copy(bounds=RetouchBounds(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y))))
                                    committed=true
                                }
                            } else {
                                commit(edit.copy(strokes=edit.strokes+RetouchStroke(
                                    drag,radius,tool==RetouchTool.RESTORE,
                                    heal=tool==RetouchTool.HEAL,
                                    donor=if(tool==RetouchTool.HEAL) donor else null
                                )))
                                committed=true
                            }
                        }
                        } finally {
                            drag=emptyList()
                            if(!committed) report(rendered)
                        }
                    }
                }) {
                if(before) return@Canvas
                val fit=min(size.width/source.width,size.height/source.height)
                val width=source.width*fit; val height=source.height*fit
                val origin=Offset((size.width-width)/2,(size.height-height)/2)
                fun pos(p: RetouchPoint)=origin+Offset(p.x*width,p.y*height)
                val bounds=edit.bounds ?: if(tool==RetouchTool.FRAME || generated==null) detectedBounds else null
                if(tool==RetouchTool.HEAL) donor?.let { sample ->
                    drawCircle(Color(0xFFFFD16B),7.dp.toPx(),pos(sample),style=Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFFFFD16B),2.dp.toPx(),pos(sample))
                }
                bounds?.let {
                    drawRect(Color(0xFF39C8AE),origin+Offset(it.left*width,it.top*height),Size((it.right-it.left)*width,(it.bottom-it.top)*height),style=Stroke(2.dp.toPx()))
                }
                if(drag.isNotEmpty()) {
                    if(tool==RetouchTool.FRAME) {
                        val a=pos(drag.first()); val b=pos(drag.last())
                        drawRect(Color.Yellow,Offset(min(a.x,b.x),min(a.y,b.y)),Size(kotlin.math.abs(b.x-a.x),kotlin.math.abs(b.y-a.y)),style=Stroke(2.dp.toPx()))
                    } else drag.forEach {
                        drawCircle(
                            if(tool==RetouchTool.RESTORE) Color(0x55FF7272)
                            else if(tool==RetouchTool.HEAL && pickingSkin) Color(0xAAFFD16B)
                            else Color(0x5539C8AE),
                            if(tool==RetouchTool.HEAL && pickingSkin) 7.dp.toPx()
                                else radius*min(width,height),pos(it)
                        )
                    }
                }
            }
        }
        // Keep the photo fixed. Advanced hand-retouch tools scroll in their
        // own small area, so switching to Healing Brush cannot hide preview
        // or push the one-tap AI / Save actions off the screen.
        if(generated!=null) {
        Column(Modifier.fillMaxWidth().heightIn(max=controlsHeight)
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            RetouchTool.entries.forEach { option ->
                FilterChip(modifier=Modifier.testTag("tool-"+option.name.lowercase()),
                    selected=tool==option,enabled=!busy && (!rendering) && (option==RetouchTool.PAN || generated!=null),
                    onClick={ tool=option },label={ Text(option.label) })
            }
        }
        if(tool==RetouchTool.FRAME) {
            Text("ลากกรอบเพื่อจำกัดพื้นที่แต่ง • ไม่เปลี่ยนจุดตา/ปากที่ AI ตรวจพบ",style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled=!busy && !rendering && detectedBounds!=null,onClick={ commit(edit.copy(bounds=detectedBounds)) }) { Text("ใช้กรอบที่ตรวจพบ") }
                TextButton(enabled=!busy && !rendering && edit.bounds!=null,onClick={ commit(edit.copy(bounds=null)) }) { Text("ไม่จำกัดกรอบ") }
            }
        }
        if(tool==RetouchTool.HEAL) {
            Text("ซ่อมบริเวณที่ AI ลบไม่ถึงด้วยเนื้อผิวจริง ไม่เรียก AI ซ้ำ",
                style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=pickingSkin, enabled=!busy && !rendering,
                    onClick={ pickingSkin=!pickingSkin },
                    label={ Text(if(pickingSkin) "แตะผิวสะอาดบนภาพ…" else "เลือกผิวต้นแบบ") })
                if(donor!=null) TextButton(enabled=!busy && !rendering,
                    onClick={donor=null;pickingSkin=false}) { Text("เลือกอัตโนมัติ") }
            }
            Text(if(donor==null) "ไม่เลือกต้นแบบ: ระบบจะหาผิวใกล้เคียงให้อัตโนมัติ"
                else "เลือกผิวต้นแบบแล้ว · ทาบริเวณหนวดที่ตกหล่นได้เลย",
                style=MaterialTheme.typography.bodySmall)
        }
        if(tool==RetouchTool.PAINT)
            Text("ทาเพิ่มเฉพาะส่วนที่ AI เคยแก้ไข ถ้า AI พลาด ให้เลือก «ซ่อมหนวด»",
                style=MaterialTheme.typography.bodySmall)
        if(tool==RetouchTool.HEAL || tool==RetouchTool.PAINT || tool==RetouchTool.RESTORE) {
            Text("ขนาดแปรง " + (radius*200).toInt() + "%",style=MaterialTheme.typography.bodySmall)
            Slider(radius,{ radius=it },valueRange=.008f.. .10f,enabled=!busy && !rendering)
        }
        Text("ใช้ 1 นิ้วทา/วาด • ใช้ 2 นิ้วเลื่อนและซูมขณะใช้ทุกเครื่องมือได้",
            style=MaterialTheme.typography.bodySmall)
        } // scrollable manual tools
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(enabled=!busy && !rendering && history.size>1,onClick={ report(null); redo.add(history.removeAt(history.lastIndex)) }) { Icon(Icons.Outlined.Undo,"ย้อนกลับ") }
            IconButton(enabled=!busy && !rendering && redo.isNotEmpty(),onClick={ report(null); history.add(redo.removeAt(redo.lastIndex)) }) { Icon(Icons.Outlined.Redo,"ทำซ้ำ") }
            Spacer(Modifier.weight(1f))
            HoldOriginalButton(generated!=null && !busy) { before=it }
        }
        } // generated result tools; initial screen stays uncluttered
        if(rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
        renderError?.let { Text(it,color=MaterialTheme.colorScheme.error) }

    }
}
