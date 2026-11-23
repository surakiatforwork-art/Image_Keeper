package com.branchphotovault.ui.screens

import android.graphics.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import com.branchphotovault.ui.components.GlassButton
import com.branchphotovault.ui.components.GlassSecondaryButton
import com.branchphotovault.ui.components.ManualRetouchEditor
import com.branchphotovault.retouch.RetouchBounds
import androidx.compose.ui.unit.dp
import com.branchphotovault.LocalAppContainer
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.retouch.MoustacheRetoucher
import com.branchphotovault.retouch.RetouchRegion
import com.branchphotovault.retouch.RetouchEngine
import com.branchphotovault.retouch.BeardReconstructionV66
import com.branchphotovault.retouch.V6PassOutcome
import com.branchphotovault.retouch.processV6Passes
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MoustacheRetouchDialog(photo: PhotoEntity, dismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = LocalAppContainer.current.photoRepository
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var faceBounds by remember { mutableStateOf<List<RetouchBounds>>(emptyList()) }
    var detectedFaces by remember {
        mutableStateOf<List<List<NormalizedLandmark>>>(emptyList())
    }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var composite by remember { mutableStateOf<Bitmap?>(null) }
    var faceCount by remember { mutableStateOf(0) }
    var face by remember { mutableStateOf(0) }
    var region by remember { mutableStateOf(RetouchRegion.BOTH) }
    // V6 is an opt-in on-device research pipeline in the private trial APK.
    // The classic one-pass AttGAN is available for instant visual comparison.
    val privateResearchAvailable=remember { context.assets.list("retouch")?.contains("beard-mobile-224.onnx")==true }
    var retouchEngine by remember { mutableStateOf(if(privateResearchAvailable) RetouchEngine.V6_OFFLINE else RetouchEngine.BASELINE) }
    var v67Refinement by remember { mutableStateOf(false) }
    // AttGAN remains the verified offline quality baseline. Extra detail is
    // optional, preserving the original single-pass result for comparison.
    var extraDetail by remember { mutableStateOf(false) }
    var extraDetailStrength by remember { mutableFloatStateOf(.28f) }
    var v6CoverageStrength by remember { mutableFloatStateOf(.84f) }
    // Faster default: V6 once, specialized beard model once.
    var v6Passes by remember { mutableIntStateOf(1) }
    // Private RESEARCH-ONLY beard checkpoint, switchable for comparisons.
    var v66BeardReconstruction by remember { mutableStateOf(true) }
    var v66BeardStrength by remember { mutableFloatStateOf(.94f) }
    var busy by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var strength by remember { mutableFloatStateOf(1f) }
    var message by remember { mutableStateOf("กำลังตรวจใบหน้า…") }
    LaunchedEffect(photo.id) {
        try {
            val loaded = withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo.localMainPath,bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "อ่านไฟล์ภาพไม่ได้" }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                while (maxOf(bounds.outWidth,bounds.outHeight) / options.inSampleSize > 2560) options.inSampleSize *= 2
                requireNotNull(BitmapFactory.decodeFile(photo.localMainPath,options))
            }
            original = loaded
            val faces = withContext(Dispatchers.Default) { MoustacheRetoucher(context).faces(loaded) }
            faceCount = faces.size
            detectedFaces=faces
            faceBounds=faces.map { points -> RetouchBounds(
                (points.minOf { it.x() }-.03f).coerceIn(0f,1f),
                (points.minOf { it.y() }-.03f).coerceIn(0f,1f),
                (points.maxOf { it.x() }+.03f).coerceIn(0f,1f),
                (points.maxOf { it.y() }+.03f).coerceIn(0f,1f)) }
            message = if (faceCount > 0) "เลือกใบหน้า แล้วกดรีทัช · ต้นฉบับจะถูกเก็บไว้" else "ไม่พบใบหน้าในภาพ"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.e("KeeperRetouch", "Image decode or face detection failed", error)
            message = if (original == null) "เปิดภาพไม่สำเร็จ: ${error.message.orEmpty()}"
                else "ตรวจใบหน้าไม่สำเร็จ: ${error.message.orEmpty()}"
        }
        finally { busy = false }
    }
    val startRetouch: () -> Unit = {
        val source = original
        if (source != null && !busy && faceCount > 0) {
            busy=true
            result=null
            composite=null
            strength=1f
            message=if(retouchEngine == RetouchEngine.V6_OFFLINE) "Smart Shave V6 กำลังตรวจหนวดและป้องกันริมฝีปาก…" else if(extraDetail) "AttGAN กำลังตรวจหนวดเพิ่มเติม…" else "AttGAN กำลังรีทัชออฟไลน์…"
            scope.launch {
                try {
                    val (outcome,beardChanged)=withContext(Dispatchers.Default) {
                        val tool=MoustacheRetoucher(context)
                        val base=if(retouchEngine == RetouchEngine.V6_OFFLINE) {
                            tool.processV6Passes(
                                source,face,region,v6CoverageStrength,
                                passes=v6Passes,
                                v67Refinement=v67Refinement,
                                originalFace=detectedFaces.getOrNull(face),
                                onRound={round -> scope.launch {
                                    message="Smart Shave · กำลังประมวลผลรอบที่ $round/$v6Passes"
                                }},
                                onProgress={stage -> scope.launch {
                                    message="Smart Shave · "+stage.message
                                }}
                            )
                        } else V6PassOutcome(
                            tool.process(
                                source,face,region,retouchEngine,
                                onProgress={stage -> scope.launch { message=stage.message }},
                                extraDetail=extraDetail,
                                extraDetailStrength=extraDetailStrength,
                                v6CoverageStrength=v6CoverageStrength
                            ),1
                        )
                        if(retouchEngine == RetouchEngine.V6_OFFLINE &&
                            region != RetouchRegion.MOUSTACHE && v66BeardReconstruction) {
                            try {
                                val selected=detectedFaces.getOrNull(face)
                                    ?: error("ไม่พบโครงใบหน้าต้นฉบับ")
                                scope.launch { message="กำลังสร้างผิวคางด้วย AI เครา V6.6…" }
                                val (polished,stats)=BeardReconstructionV66(context)
                                    .reconstruct(source,base.image,selected,v66BeardStrength,refineV67=v67Refinement)
                                if(polished !== base.image) {
                                    base.image.recycle()
                                    V6PassOutcome(polished,base.completedPasses) to
                                        (stats.editedPixels>0)
                                } else base to false
                            } catch(error:Exception) {
                                android.util.Log.w("KeeperV66Beard",
                                    "Research model failed: retaining base V6",error)
                                base to false
                            }
                        } else base to false
                    }
                    result=outcome.image
                    message=if(retouchEngine == RetouchEngine.V6_OFFLINE)
                        if(beardChanged)
                            if(v67Refinement) "V6.7 เก็บหนวดและเคราแล้ว · ตรวจขอบปากและรูปคางก่อนบันทึก"
                            else "V6.6 เก็บหนวดและเคราด้วย AI แล้ว · ตรวจปาก คาง และขอบผิว"
                        else if(v66BeardReconstruction && region != RetouchRegion.MOUSTACHE)
                            "V6 เสร็จ แต่ AI เคราไม่สำเร็จ · ตรวจภาพก่อนบันทึก"
                        else "V6 เสร็จ ${outcome.completedPasses}/$v6Passes รอบ · ตรวจผิวก่อนบันทึก"
                    else if(extraDetail)
                        "รีทัช 2 มุมมองแล้ว · ตรวจริมฝีปากและคางก่อนบันทึก"
                    else "AttGAN ประมวลผลแล้ว · ตรวจริมฝีปากและคางก่อนบันทึก"
                } catch(cancelled:CancellationException) {
                    message="ยกเลิกการรีทัช"
                } catch(error:Exception) {
                    android.util.Log.e("KeeperRetouch","Offline retouch failed",error)
                    message="รีทัชไม่สำเร็จ: ${error.message.orEmpty()}"
                } finally { busy=false }
            }
        }
    }
    BackHandler(enabled = busy) {}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,
        confirmValueChange={ (!busy && !zoomed && !editing) || it==SheetValue.Expanded })
    val sheetShape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp)
    ModalBottomSheet(
        sheetState=sheetState,
        onDismissRequest = { if (!busy) dismiss() },
        containerColor=MaterialTheme.colorScheme.surface.copy(alpha=.98f),
        shape=sheetShape
    ) {
        // Only advanced controls scroll. Image, result strength and actions
        // stay in view even on a small Android handset.
        Column(Modifier.fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp*.84f).dp)
            .navigationBarsPadding().padding(horizontal=16.dp).padding(bottom=8.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary)
                Text("รีทัชหนวดและเครา",style=MaterialTheme.typography.titleLarge)
            }
            original?.let { source ->
                ManualRetouchEditor(source,result,strength,busy,faceBounds.getOrNull(face),
                    onZoomChanged={ zoomed=it },onEditingChanged={ editing=it },
                    onRendered={ composite=it },
                    previewHeight=(LocalConfiguration.current.screenHeightDp*.215f)
                        .coerceIn(156f,216f).dp)
            }
            Text(message,style=MaterialTheme.typography.bodySmall,maxLines=2)
            result?.let {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("ผลลัพธ์ ${(strength*100).toInt()}%",
                        style=MaterialTheme.typography.bodySmall)
                    Slider(value=strength,onValueChange={
                        composite=null;strength=it
                    },enabled=!busy,modifier=Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("พื้นที่รีทัช",style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RetouchRegion.entries.forEach { option ->
                        FilterChip(selected = region == option, enabled = !busy,
                            onClick = { region = option; result = null; composite = null },
                            label = { Text(option.label) })
                    }
                }
                HorizontalDivider()
                Text("โหมด AI บนเครื่อง",style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (if(privateResearchAvailable) listOf(RetouchEngine.V6_OFFLINE,RetouchEngine.BASELINE)
                        else listOf(RetouchEngine.BASELINE)).forEach { option ->
                        FilterChip(selected=retouchEngine==option, enabled=!busy,
                            onClick={
                                retouchEngine=option
                                result=null
                                composite=null
                                strength=1f
                                message=if(option==RetouchEngine.V6_OFFLINE)
                                    "Smart Shave V6 · ตรวจหนวดด้วย AI + LipSafe · ทดลองส่วนตัว"
                                else "AttGAN รอบเดียว · ผลอ้างอิงเดิม"
                            },
                            label={ Text(if(option==RetouchEngine.V6_OFFLINE)
                                "V6 · AI + LipSafe" else "AttGAN · ดั้งเดิม") })
                    }
                }
                Text(if(retouchEngine==RetouchEngine.V6_OFFLINE)
                    "ออฟไลน์ · LipSafe · ไม่ทับภาพต้นฉบับ"
                    else "AttGAN ออฟไลน์ · เปรียบเทียบผล",
                    style=MaterialTheme.typography.bodySmall)
                if(retouchEngine==RetouchEngine.V6_OFFLINE) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("ทดลอง V6.7 · เก็บขอบปากและคุมขอบผิว",style=MaterialTheme.typography.titleSmall)
                            Text("ปิดเพื่อเทียบกับ V6.6 · ไม่เพิ่มรอบสร้างภาพ",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=v67Refinement,enabled=!busy,onCheckedChange={
                            v67Refinement=it;result=null;composite=null;strength=1f
                        })
                    }
                    if(region != RetouchRegion.MOUSTACHE) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("V6.6 · AI ลบเคราโดยเฉพาะ",
                                    style=MaterialTheme.typography.titleSmall)
                                Text("ออฟไลน์ · รุ่นทดลองวิจัย · คงภาพต้นฉบับ",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked=v66BeardReconstruction,enabled=!busy,
                                onCheckedChange={
                                    v66BeardReconstruction=it
                                    result=null;composite=null;strength=1f
                                })
                        }
                        if(v66BeardReconstruction) {
                            Text("ความแรงลบเครา "+(v66BeardStrength*100).toInt()+"%",
                                style=MaterialTheme.typography.bodySmall)
                            Slider(value=v66BeardStrength,onValueChange={
                                v66BeardStrength=it
                                result=null;composite=null;strength=1f
                            },enabled=!busy,valueRange=.40f..1f)

                        }
                    }
                    Text("จำนวนรอบ AI",style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(1,2).forEach { choice ->
                            FilterChip(
                                selected=v6Passes==choice,
                                enabled=!busy,
                                onClick={
                                    v6Passes=choice
                                    result=null
                                    composite=null
                                    strength=1f
                                },
                                label={ Text("$choice รอบ") }
                            )
                        }
                    }
                    Text("1 รอบเร็วกว่า · 2 รอบเนียนกว่าแต่ใช้เวลามากขึ้น",
                        style=MaterialTheme.typography.bodySmall)
                    Text(
                        "ความครอบคลุมการลบหนวด " + (v6CoverageStrength*100).toInt() + "%",
                        style=MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value=v6CoverageStrength,
                        onValueChange={
                            v6CoverageStrength=it
                            result=null
                            composite=null
                            strength=1f
                        },
                        valueRange=.0f..1f,
                        enabled=!busy
                    )
                    Text("ถ้าผิวเริ่มดูแบน ให้ลดค่านี้",
                        style=MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("เก็บหนวดตกหล่นเพิ่มเติม",style=MaterialTheme.typography.bodyMedium)
                        Text("ทดลองเก็บเฉพาะเส้นขนสีเข้ม · ใช้เวลานานขึ้น " +
                            "และอาจเหมาะกับภาพบางประเภทเท่านั้น",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=extraDetail,enabled=!busy && retouchEngine==RetouchEngine.BASELINE,onCheckedChange={
                        extraDetail=it;result=null;composite=null;strength=1f
                        message=if(it) "โหมดเก็บรายละเอียด 2 มุมมอง (ทดลอง)" else
                            "AttGAN รอบเดียว (ผลอ้างอิง)"
                    })
                }
                if(extraDetail && retouchEngine==RetouchEngine.BASELINE) {
                    Text("ความแรงเก็บรายละเอียด " + (extraDetailStrength*100).toInt() + "%" +
                        " · เน้นคงสีผิว AttGAN เดิม",style=MaterialTheme.typography.bodySmall)
                    Slider(value=extraDetailStrength,
                        onValueChange={
                            extraDetailStrength=it
                            result=null
                            composite=null
                        },
                        valueRange=.10f.. .60f,
                        enabled=!busy)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (faceCount > 1) Row {
                    TextButton(enabled = !busy,onClick = {
                        face=(face+1)%faceCount;result=null;composite=null

                    }) { Text("ใบหน้า ${face+1}/$faceCount (ลำดับตรวจพบ)") }
                }
            }
            GlassButton(modifier=Modifier.fillMaxWidth(),
                enabled=!busy && faceCount>0,onClick=startRetouch
            ) {Text(if(retouchEngine==RetouchEngine.V6_OFFLINE)
                "เริ่ม Smart Shave V6 · $v6Passes รอบ" else if(extraDetail)
                "รีทัชออฟไลน์ · เก็บรายละเอียด" else "รีทัชออฟไลน์ด้วย AttGAN")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(enabled=!busy,onClick=dismiss,modifier=Modifier.weight(1f)) { Text("ยกเลิก") }
                GlassButton(modifier=Modifier.weight(1.5f),enabled = !busy && composite != null,onClick = {
            val source = original ?: return@GlassButton
            val edited = composite ?: return@GlassButton
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO + NonCancellable) {
                        val output = source.copy(Bitmap.Config.ARGB_8888,true)
                        try {
                            Canvas(output).drawBitmap(edited,0f,0f,null)
                            repository.saveRetouchedCopy(photo,output)
                        } finally { output.recycle() }
                    }
                    dismiss()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "บันทึกไม่สำเร็จ ต้นฉบับยังอยู่ครบ" }
                finally { busy = false }
            }
        }) { Text("บันทึกฉบับแต่ง") }
            }
        }
    }
}
