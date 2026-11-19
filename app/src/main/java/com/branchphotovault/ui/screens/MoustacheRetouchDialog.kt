package com.branchphotovault.ui.screens

import android.graphics.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun MoustacheRetouchDialog(photo: PhotoEntity, dismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = LocalAppContainer.current.photoRepository
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var faceGuide by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var faceCount by remember { mutableStateOf(0) }
    var face by remember { mutableStateOf(0) }
    var region by remember { mutableStateOf(RetouchRegion.BOTH) }
    var busy by remember { mutableStateOf(true) }
    var before by remember { mutableStateOf(false) }
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
            if (faceCount > 1) {
                faceGuide = loaded.copy(Bitmap.Config.ARGB_8888,true).also { guide ->
                    val canvas = Canvas(guide)
                    faces.forEachIndexed { index, points ->
                        val x = points[1].x() * guide.width
                        val y = points[10].y() * guide.height
                        val size = (guide.width * 0.045f).coerceAtLeast(24f)
                        canvas.drawCircle(x,y,size,Paint().apply { color = Color.BLACK })
                        canvas.drawText("${index+1}",x,y+size*0.35f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE; textSize = size; textAlign = Paint.Align.CENTER
                        })
                    }
                }
            }
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
    AlertDialog(
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text("รีทัชหนวดและเครา") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                original?.let { source ->
                    Box(Modifier.fillMaxWidth().height(260.dp)) {
                        Image((if (result == null) faceGuide ?: source else source).asImageBitmap(),null,Modifier.fillMaxSize())
                        if (!before) result?.let { edited ->
                            Image(edited.asImageBitmap(),null,Modifier.fillMaxSize(),alpha = strength)
                        }
                    }
                }
                Text(message)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetouchRegion.entries.forEach { option ->
                        FilterChip(selected = region == option, enabled = !busy,
                            onClick = { region = option; result = null; before = false },
                            label = { Text(option.label) })
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (faceCount > 1) Row {
                    TextButton(enabled = !busy,onClick = { face = (face+1)%faceCount; result = null }) { Text("ใบหน้า ${face+1}/$faceCount (ลำดับตรวจพบ)") }
                }
                result?.let {
                    TextButton(onClick = { before = !before }) { Text(if (before) "ดูหลังแต่ง" else "ดูต้นฉบับ") }
                    Text("ความแรง ${(strength*100).toInt()}%")
                    Slider(strength,{ strength = it },enabled = !busy)
                }
                OutlinedButton(enabled = !busy && faceCount > 0,onClick = {
                    val source = original ?: return@OutlinedButton
                    busy = true; result = null; before = false; message = "กำลังวิเคราะห์ภาพ…"
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
        },
        confirmButton = { TextButton(enabled = !busy && result != null,onClick = {
            val source = original ?: return@TextButton
            val edited = result ?: return@TextButton
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO + NonCancellable) {
                        val output = source.copy(Bitmap.Config.ARGB_8888,true)
                        try {
                            Canvas(output).drawBitmap(edited,0f,0f,Paint().apply { alpha = (strength*255).toInt() })
                            repository.saveRetouchedCopy(photo,output)
                        } finally { output.recycle() }
                    }
                    dismiss()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "บันทึกไม่สำเร็จ ต้นฉบับยังอยู่ครบ" }
                finally { busy = false }
            }
        }) { Text("บันทึกฉบับแต่ง") } },
        dismissButton = { TextButton(enabled = !busy,onClick = dismiss) { Text("ยกเลิก") } }
    )
}
