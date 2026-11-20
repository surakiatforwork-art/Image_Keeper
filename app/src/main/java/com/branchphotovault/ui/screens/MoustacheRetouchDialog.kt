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
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var composite by remember { mutableStateOf<Bitmap?>(null) }
    var faceCount by remember { mutableStateOf(0) }
    var face by remember { mutableStateOf(0) }
    var region by remember { mutableStateOf(RetouchRegion.BOTH) }
    var busy by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var strength by remember { mutableFloatStateOf(.85f) }
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
                Text(message)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetouchRegion.entries.forEach { option ->
                        FilterChip(selected = region == option, enabled = !busy,
                            onClick = { region = option; result = null; composite = null },
                            label = { Text(option.label) })
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (faceCount > 1) Row {
                    TextButton(enabled = !busy,onClick = { face = (face+1)%faceCount; result = null; composite=null }) { Text("ใบหน้า ${face+1}/$faceCount (ลำดับตรวจพบ)") }
                }
                result?.let {
                    Text("ความแรง ${(strength*100).toInt()}%")
                    Slider(strength,{ composite=null; strength = it },enabled = !busy)
                }
                GlassButton(modifier=Modifier.fillMaxWidth(),enabled = !busy && faceCount > 0,onClick = {
                    val source = original ?: return@GlassButton
                    busy = true; result = null; composite=null; message = "กำลังวิเคราะห์ภาพ…"
                    scope.launch {
                        try {
                            result = withContext(Dispatchers.Default) {
                                MoustacheRetoucher(context).process(source,face,region) { stage ->
                                    scope.launch { message = stage.message }
                                }
                            }
                            message = "วิเคราะห์พื้นที่และปรับผิวแล้ว · ตรวจผลก่อนบันทึก ต้นฉบับยังอยู่ครบ"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            android.util.Log.e("KeeperRetouch", "Retouch failed", error)
                            message = "รีทัชไม่สำเร็จ: ${error.message.orEmpty()}"
                        }
                        finally { busy = false }
                    }
                }) { Text("แต่งอัตโนมัติด้วย AI") }
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
