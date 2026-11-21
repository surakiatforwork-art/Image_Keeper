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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.branchphotovault.retouch.CloudBeardRetoucher
import com.branchphotovault.retouch.RetouchRegion
import com.branchphotovault.retouch.RetouchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var composite by remember { mutableStateOf<Bitmap?>(null) }
    var faceCount by remember { mutableStateOf(0) }
    var face by remember { mutableStateOf(0) }
    var region by remember { mutableStateOf(RetouchRegion.BOTH) }
    var engine by remember { mutableStateOf(RetouchEngine.BASELINE) }
    var cloudMode by remember { mutableStateOf(false) }
    var cloudKey by remember { mutableStateOf("") }
    var cloudConsent by remember { mutableStateOf(false) }
    var cloudJob by remember { mutableStateOf<Job?>(null) }
    var maskPreview by remember { mutableStateOf<Bitmap?>(null) }
    var maskStats by remember { mutableStateOf<com.branchphotovault.retouch.HairMaskDiagnostics.Stats?>(null) }
    var maskVisible by remember { mutableStateOf(false) }
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
    // One retouch entrypoint: opt-in Cloud calls are separate from the
    // tested offline AttGAN path. The user's API Key is session-only.
    val startRetouch: () -> Unit = {
        val source = original
        if(source != null && !busy && faceCount > 0) {
            val useCloud=cloudMode
            val activeKey=if(useCloud) cloudKey.trim() else ""
            busy = true
            result=null;composite=null
            maskPreview=null;maskStats=null;maskVisible=false
            message = if(useCloud) "กำลังเตรียมภาพสำหรับ Cloud…" else "กำลังวิเคราะห์ภาพ…"
            cloudJob = scope.launch {
                try {
                    var neuralAvailable=false
                    val processed = if(useCloud) {
                        CloudBeardRetoucher(context).process(source,face,region,activeKey,
                            progress={ status -> scope.launch {message=status} })
                    } else {
                        withContext(Dispatchers.Default) {
                            MoustacheRetoucher(context).process(source,face,region,RetouchEngine.BASELINE,
                                onProgress={ stage -> scope.launch {message=stage.message} },
                                onDiagnostics={ map,stats ->
                                    neuralAvailable=true
                                    scope.launch {maskPreview=map;maskStats=stats}
                                })
                        }
                    }
                    result=processed
                    message= if(useCloud) {
                        "Cloud AI ประมวลผลแล้ว · ตรวจสีผิว ริมฝีปาก และแนวคางก่อนบันทึก"
                    } else {
                        "AttGAN ประมวลผลแล้ว · ขยายดูหนวดและคางก่อนบันทึก"
                    }
                } catch(cancelled:CancellationException) {
                    message="หยุดติดตามงานแล้ว · งาน Cloud อาจยังดำเนินการและมีค่าใช้จ่าย"
                } catch(error:Exception) {
                    android.util.Log.e("KeeperRetouch","Retouch operation failed: ${error.javaClass.simpleName}")
                    message=if(useCloud) "Cloud ไม่สำเร็จ: ${error.message.orEmpty()}" else
                        "รีทัชไม่สำเร็จ: ${error.message.orEmpty()}"
                } finally {
                    busy=false
                    cloudJob=null
                }
            }
        }
    }
    if(cloudConsent) {
        AlertDialog(
            onDismissRequest={cloudConsent=false},
            title={Text("ยืนยันส่งภาพไป Cloud AI")},
            text={
                Text("Keeper จะครอปเฉพาะบริเวณใบหน้าที่เลือกและส่งไป AILabTools เพื่อประมวลผลลบหนวดเครา " +
                    "ภาพจะออกจากโทรศัพท์และอาจมีค่าบริการตามบัญชี API Key ของคุณ " +
                    "ผลลัพธ์จะเก็บไว้ชั่วคราวบนเซิร์ฟเวอร์ผู้ให้บริการตามนโยบายของเขา " +
                    "ระบบจะไม่เก็บ API Key ลงในไฟล์หรือฝังไว้ใน APK และจะไม่แก้ไขภาพต้นฉบับ")
            },
            confirmButton={
                TextButton(onClick={cloudConsent=false;startRetouch()}) {Text("ยินยอมและประมวลผล")}
            },
            dismissButton={TextButton(onClick={cloudConsent=false}){Text("ยกเลิก")}}
        )
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
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.86f).dp)
            .navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=16.dp)
            .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary)
                Text("รีทัชหนวดและเครา",style=MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                original?.let { source ->
                    ManualRetouchEditor(source,result,strength,busy,faceBounds.getOrNull(face),
                        onZoomChanged={ zoomed=it },onEditingChanged={ editing=it },onRendered={ composite=it })
                }
                Text(message,style=MaterialTheme.typography.bodySmall)
                Text("พื้นที่รีทัช",style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RetouchRegion.entries.forEach { option ->
                        FilterChip(selected = region == option, enabled = !busy,
                            onClick = { region = option; result = null; composite = null; maskPreview=null; maskStats=null; maskVisible=false },
                            label = { Text(option.label) })
                    }
                }
                HorizontalDivider()
                Text("ระบบรีทัช",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        modifier=Modifier.weight(1f),
                        selected=!cloudMode,enabled=!busy,
                        onClick={
                            cloudMode=false;engine=RetouchEngine.BASELINE
                            result=null;composite=null;maskPreview=null
                            strength=1f
                            message="AttGAN ออฟไลน์ · พร้อมลบหนวดเครา"
                        },
                        label={Text("AttGAN ออฟไลน์",maxLines=1)}
                    )
                    FilterChip(
                        modifier=Modifier.weight(1f),
                        selected=cloudMode,enabled=!busy,
                        onClick={
                            cloudMode=true
                            result=null;composite=null;maskPreview=null
                            strength=1f
                            message="Cloud Beta · ใส่ API Key แล้วเลือกแต่งอัตโนมัติ"
                        },
                        label={Text("Cloud AI · Beta",maxLines=1)}
                    )
                }
                if(cloudMode) {
                    OutlinedTextField(
                        modifier=Modifier.fillMaxWidth(),
                        value=cloudKey,
                        onValueChange={cloudKey=it.take(512)},
                        singleLine=true,
                        visualTransformation=PasswordVisualTransformation(),
                        label={Text("AILabTools API Key ของคุณ")},
                        supportingText={Text("ใช้เฉพาะรอบนี้ · ไม่บันทึกคีย์ในเครื่องหรือใน APK")}
                    )
                    Text("Cloud จะส่งภาพครอปใบหน้าไปผู้ให้บริการภายนอก มีค่าใช้จ่ายต่อภาพ " +
                        "และต้องใช้อินเทอร์เน็ต · ยังไม่ยืนยันว่าผลดีเท่า Meitu",
                        style=MaterialTheme.typography.bodySmall)
                } else {
                    Text("AttGAN ทำงานบนมือถือโดยไม่ต้องใช้อินเทอร์เน็ต " +
                        "และเป็นตัวอ้างอิงที่ผ่านการประเมินด้วยสายตาของคุณในภาพตัวอย่าง",
                        style=MaterialTheme.typography.bodySmall)
                }
                if(engine==RetouchEngine.SMART_V4 && maskPreview!=null) {
                    TextButton(onClick={maskVisible=!maskVisible},enabled=!busy) {
                        Text(if(maskVisible) "ซ่อนแผนที่ AI" else "ดูบริเวณหนวดที่ AI ตรวจพบ")
                    }
                    if(maskVisible) {
                        maskPreview?.let {preview ->
                            Image(preview.asImageBitmap(),"แผนที่หนวดจากโมเดล AI",
                                Modifier.fillMaxWidth().height(290.dp),contentScale=ContentScale.Fit)
                        }
                        maskStats?.let {info ->
                            Text("แดง: หนวดที่ตรวจพบและแก้ไขได้ • ส้ม: ตรวจพบแต่ถูกป้องกัน • เขียวอ่อน: พื้นที่แก้ไขเดิม",
                                style=MaterialTheme.typography.bodySmall)
                            Text("พิกเซลตัวอย่างในพื้นที่แก้ไข ${info.accepted} • นอกพื้นที่ ${info.excluded} · แผนที่เป็นผลประมาณจาก AI",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (faceCount > 1) Row {
                    TextButton(enabled = !busy,onClick = {
                        face=(face+1)%faceCount;result=null;composite=null
                        maskPreview=null;maskStats=null;maskVisible=false
                    }) { Text("ใบหน้า ${face+1}/$faceCount (ลำดับตรวจพบ)") }
                }
                result?.let {
                    Text("ความแรง ${(strength*100).toInt()}%")
                    Slider(strength,{ composite=null; strength = it },enabled = !busy)
                }
                if(busy && cloudMode) {
                    TextButton(enabled=cloudJob!=null,onClick={cloudJob?.cancel()}) {
                        Text("ยกเลิกการรอผล Cloud (คำขอที่ส่งแล้วอาจยังมีค่าบริการ)")
                    }
                }
                GlassButton(modifier=Modifier.fillMaxWidth(),
                    enabled = !busy && faceCount > 0,
                    onClick = {
                        if(cloudMode) {
                            if(cloudKey.isBlank()) {
                                message="กรุณาใส่ AILabTools API Key เพื่อทดลอง Cloud Beta"
                            } else cloudConsent=true
                        } else startRetouch()
                    }
                ) {Text(if(cloudMode) "ลบหนวดด้วย Cloud AI (Beta)" else "แต่งด้วย AttGAN ออฟไลน์")}
            }
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
