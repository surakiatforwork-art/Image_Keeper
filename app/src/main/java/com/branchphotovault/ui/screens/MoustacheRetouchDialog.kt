package com.branchphotovault.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.branchphotovault.LocalAppContainer
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.retouch.MoustacheRetoucher
import com.branchphotovault.retouch.RetouchBounds
import com.branchphotovault.retouch.SmartShaveAuto
import com.branchphotovault.ui.components.GlassButton
import com.branchphotovault.ui.components.GlassSecondaryButton
import com.branchphotovault.ui.components.ManualRetouchEditor
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-tap Smart Shave. The user no longer needs to select region, model,
 * 1/2 pass or experimental toggles. The offline pipeline chooses a safe
 * quality recipe automatically. The only required user action is one tap
 * on "ลบหนวดและเคราด้วย AI" and then optional inspection/save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoustacheRetouchDialog(photo:PhotoEntity,dismiss:()->Unit) {
    val context=LocalContext.current
    val repository=LocalAppContainer.current.photoRepository
    val scope=rememberCoroutineScope()
    var original by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var faceBounds by remember(photo.id) { mutableStateOf<List<RetouchBounds>>(emptyList()) }
    var detectedFaces by remember(photo.id) {
        mutableStateOf<List<List<NormalizedLandmark>>>(emptyList())
    }
    var result by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var composite by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var face by remember(photo.id) { mutableIntStateOf(0) }
    var busy by remember(photo.id) { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var strength by remember { mutableFloatStateOf(1f) }
    var message by remember { mutableStateOf("กำลังตรวจใบหน้า…") }

    LaunchedEffect(photo.id) {
        try {
            val loaded=withContext(Dispatchers.Default) {
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeFile(photo.localMainPath,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0){"อ่านไฟล์ภาพไม่ได้"}
                val options=BitmapFactory.Options().apply {
                    inSampleSize=1
                    inPreferredConfig=Bitmap.Config.ARGB_8888
                }
                while(maxOf(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2560)
                    options.inSampleSize*=2
                requireNotNull(BitmapFactory.decodeFile(photo.localMainPath,options))
            }
            original=loaded
            val faces=withContext(Dispatchers.Default){
                MoustacheRetoucher(context).faces(loaded)
            }
            detectedFaces=faces
            faceBounds=faces.map{points->
                RetouchBounds(
                    (points.minOf { it.x() }-.03f).coerceIn(0f,1f),
                    (points.minOf { it.y() }-.03f).coerceIn(0f,1f),
                    (points.maxOf { it.x() }+.03f).coerceIn(0f,1f),
                    (points.maxOf { it.y() }+.03f).coerceIn(0f,1f)
                )
            }
            message=if(faces.isNotEmpty())
                "พร้อมลบหนวดและเคราด้วย AI บนเครื่อง · เก็บต้นฉบับไว้"
                else "ไม่พบใบหน้าในภาพ"
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){
            android.util.Log.e("KeeperOneTap","Photo/face detection failed",error)
            message=if(original==null) "เปิดภาพไม่สำเร็จ: "+error.message.orEmpty()
                else "ตรวจใบหน้าไม่สำเร็จ: "+error.message.orEmpty()
        }finally{busy=false}
    }

    val startRetouch:()->Unit={
        val source=original
        val selected=detectedFaces.getOrNull(face)
        if(source!=null && selected!=null && !busy) {
            busy=true
            result=null
            composite=null
            strength=1f
            message="กำลังลบหนวดและเคราด้วย AI ออฟไลน์…"
            scope.launch {
                try {
                    val outcome=withContext(Dispatchers.Default) {
                        SmartShaveAuto.run(context,source,face,selected,
                            onStatus={status->scope.launch { message=status }})
                    }
                    result=outcome.image
                    message=outcome.summary
                }catch(cancelled:CancellationException){
                    message="ยกเลิกการรีทัช"
                }catch(error:Exception){
                    android.util.Log.e("KeeperOneTap","One-tap AI failed",error)
                    message="ประมวลผลไม่สำเร็จ: "+error.message.orEmpty()
                }finally{busy=false}
            }
        }
    }

    BackHandler(enabled=busy){}
    val sheetState=rememberModalBottomSheetState(
        skipPartiallyExpanded=true,
        confirmValueChange={(!busy&&!zoomed&&!editing)||it==SheetValue.Expanded}
    )
    val screenHeightDp=LocalConfiguration.current.screenHeightDp
    ModalBottomSheet(
        sheetState=sheetState,
        onDismissRequest={if(!busy)dismiss()},
        containerColor=MaterialTheme.colorScheme.surface.copy(alpha=.98f),
        shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp)
    ) {
        // Photo never scrolls out when manual tools are expanded. All advanced
        // model settings were removed; processing begins on the primary tap.
        Column(Modifier.fillMaxWidth()
            .height((screenHeightDp*.86f).dp)
            .navigationBarsPadding().padding(horizontal=16.dp).padding(bottom=8.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome,null,
                    tint=MaterialTheme.colorScheme.primary)
                Text("ลบหนวดและเคราด้วย AI",
                    style=MaterialTheme.typography.titleLarge)
            }
            original?.let{source->
                ManualRetouchEditor(
                    source,result,strength,busy,faceBounds.getOrNull(face),
                    onZoomChanged={zoomed=it},
                    onEditingChanged={editing=it},
                    onRendered={composite=it},
                    previewHeight=(screenHeightDp*.235f).coerceIn(150f,215f).dp,
                    controlsHeight=(screenHeightDp*.14f).coerceIn(86f,120f).dp
                )
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message,
                modifier=Modifier.testTag("smart-shave-status"),
                style=MaterialTheme.typography.bodySmall,
                maxLines=2)
            result?.let {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("ผลลัพธ์ "+(strength*100).toInt()+"%",
                        style=MaterialTheme.typography.bodySmall)
                    Slider(
                        value=strength,
                        onValueChange={composite=null;strength=it},
                        enabled=!busy,
                        modifier=Modifier.weight(1f).testTag("smart-shave-strength")
                    )
                }
            }
            if(detectedFaces.size>1) {
                TextButton(enabled=!busy,onClick={
                    face=(face+1)%detectedFaces.size
                    result=null;composite=null;strength=1f
                }) {Text("ใบหน้า "+(face+1)+"/"+detectedFaces.size+" · เปลี่ยนใบหน้า")}
            }
            Spacer(Modifier.weight(1f))
            GlassButton(
                modifier=Modifier.fillMaxWidth().testTag("smart-shave-one-tap"),
                enabled=!busy && original!=null && detectedFaces.isNotEmpty(),
                onClick=startRetouch
            ) {
                Text(if(busy) "AI กำลังทำงาน…" else if(result==null)
                    "ลบหนวดและเคราด้วย AI"
                    else "ลองประมวลผลด้วย AI อีกครั้ง")
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(
                    modifier=Modifier.weight(1f),enabled=!busy,
                    onClick=dismiss
                ){Text("ยกเลิก")}
                GlassButton(
                    modifier=Modifier.weight(1.5f).testTag("smart-shave-save"),
                    enabled=!busy && composite!=null,
                    onClick={
                        val source=original ?: return@GlassButton
                        val edited=composite ?: return@GlassButton
                        busy=true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO+NonCancellable) {
                                    val output=source.copy(Bitmap.Config.ARGB_8888,true)
                                    try {
                                        Canvas(output).drawBitmap(edited,0f,0f,null)
                                        repository.saveRetouchedCopy(photo,output)
                                    }finally{output.recycle()}
                                }
                                dismiss()
                            }catch(cancelled:CancellationException){throw cancelled}
                            catch(error:Exception){
                                android.util.Log.e("KeeperOneTap","Copy save failed",error)
                                message="บันทึกไม่สำเร็จ · ภาพต้นฉบับยังอยู่ครบ"
                            }finally{busy=false}
                        }
                    }
                ){Text("บันทึกฉบับแต่ง")}
            }
        }
    }
}
