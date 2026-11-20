package com.branchphotovault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.branchphotovault.ui.components.GlassCard as Card
import com.branchphotovault.ui.components.GlassButton as Button
import com.branchphotovault.ui.components.GlassSecondaryButton as OutlinedButton
import com.branchphotovault.ui.components.GlassTextField as OutlinedTextField
import com.branchphotovault.ui.components.GlassDialog as AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.data.repository.PhotoTransfer
import com.branchphotovault.data.repository.TransferFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun PhotoTransferCard() {
    val context = LocalContext.current
    val transfer = remember(context) { PhotoTransfer(context) }
    val scope = rememberCoroutineScope()
    var shops by remember { mutableStateOf(emptyList<PhotoEntity>()) }
    var shop by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("") }
    var through by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingFilter by remember { mutableStateOf(TransferFilter()) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importShops by remember { mutableStateOf(emptyList<PhotoEntity>()) }
    var importShop by remember { mutableStateOf<Pair<String, String>?>(null) }
    var importMenu by remember { mutableStateOf(false) }

    fun launchTransfer(task: suspend () -> String) {
        busy = true
        status = "กำลังถ่ายโอนรูป โปรดอยู่ในหน้านี้จนเสร็จ"
        scope.launch {
            try {
                status = task()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = "ถ่ายโอนไม่สำเร็จ: ${error.message.orEmpty()}"
            } finally {
                busy = false
            }
        }
    }

    fun filter(): TransferFilter? = runCatching {
        val start = from.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        val end = through.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        require(start == null || end == null || end >= start)
        TransferFilter(start, end, shop)
    }.getOrElse {
        status = "ตรวจช่วงวันที่ ใช้รูปแบบ YYYY-MM-DD และวันสิ้นสุดต้องไม่ก่อนวันเริ่ม"
        null
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) launchTransfer { "ส่งออก ${transfer.export(uri, pendingFilter)} รูปแล้ว" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchTransfer {
            importShops = transfer.archiveShops(uri)
            importShop = pendingFilter.shop?.takeIf { key -> importShops.any { (it.account to it.branchCode) == key } }
            importUri = uri
            ""
        }
    }
    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("เลือกร้านจากไฟล์สำรอง") },
            text = {
                Column {
                    Text("ช่วงวันที่: ${pendingFilter.from ?: "ทุกวัน"} ถึง ${pendingFilter.through ?: "ไม่จำกัด"}")
                    Box {
                        OutlinedButton(onClick = { importMenu = true }) {
                            Text(importShops.firstOrNull { (it.account to it.branchCode) == importShop }
                                ?.let { "${it.branchCode} · ${it.shopName}" } ?: "ทุกร้าน")
                        }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(text = { Text("ทุกร้าน") }, onClick = { importShop = null; importMenu = false })
                            importShops.forEach { photo ->
                                DropdownMenuItem(text = { Text("${photo.account} · ${photo.branchCode} · ${photo.shopName}") },
                                    onClick = { importShop = photo.account to photo.branchCode; importMenu = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val selectedFilter = pendingFilter.copy(shop = importShop)
                importUri = null
                launchTransfer {
                    val result = transfer.import(uri, selectedFilter)
                    "นำเข้า ${result.imported} รูป · ข้ามรูปที่มีอยู่แล้ว ${result.skipped} รูป"
                }
            }) { Text("นำเข้า") } },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("ยกเลิก") } }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("นำเข้า / ส่งออกรูป", style = MaterialTheme.typography.titleMedium)
            Text("ไฟล์ Keeper ZIP เก็บภาพพร้อมข้อมูลสาขาและวันที่ เว้นวันที่ว่างและเลือกร้านทั้งหมดเพื่อถ่ายโอนทุกรูป")
            OutlinedTextField(from, { from = it }, label = { Text("วันเริ่ม YYYY-MM-DD") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(through, { through = it }, label = { Text("วันสิ้นสุด YYYY-MM-DD") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        try { shops = transfer.shops(); menu = true }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { status = "อ่านรายชื่อร้านไม่สำเร็จ" }
                    }
                }) { Text(shops.firstOrNull { (it.account to it.branchCode) == shop }?.let { "${it.branchCode} · ${it.shopName}" } ?: "ทุกร้าน") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ทุกร้าน") }, onClick = { shop = null; menu = false })
                    shops.forEach { photo ->
                        DropdownMenuItem(text = { Text("${photo.account} · ${photo.branchCode} · ${photo.shopName}") },
                            onClick = { shop = photo.account to photo.branchCode; menu = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    filter()?.let { pendingFilter = it; export.launch("Keeper-${LocalDate.now()}.zip") }
                }) { Text("ส่งออก ZIP") }
                OutlinedButton(enabled = !busy, onClick = {
                    filter()?.let { pendingFilter = it; import.launch(arrayOf("application/zip", "application/octet-stream")) }
                }) { Text("นำเข้า ZIP") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotEmpty()) Text(status)
        }
    }
}
