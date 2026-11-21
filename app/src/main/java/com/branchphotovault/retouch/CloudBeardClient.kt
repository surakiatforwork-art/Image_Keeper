package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AILabTools Beard Removal (experimental).
 *
 * BYO API key: it is used only for requests explicitly authorized by the user,
 * never logged, hard-coded, written to disk, or included in analytics.
 * IMPORTANT: This service is paid and image data goes to a third party.
 */
internal class CloudBeardClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .followRedirects(false)
        .build(),
    private val submitUrl: String = "https://www.ailabapi.com/api/portrait/editing/ai-beard-removal",
    private val queryUrl: String = "https://www.ailabapi.com/api/common/query-async-task-result"
) {
    companion object {
        const val MAX_UPLOAD_BYTES = 10_000_000
        const val MAX_RESULT_BYTES = 22_000_000
        const val MAX_POLL_ATTEMPTS = 30
        const val POLL_INTERVAL_MS = 5000L

        fun parseTaskId(json: String): String {
            val doc = JSONObject(json)
            checkError(doc)
            return doc.optString("task_id").trim().also {
                require(it.isNotEmpty() && it.length < 256) { "ไม่พบรหัสงานจาก Cloud" }
            }
        }

        fun parseResult(json: String): String? {
            val doc = JSONObject(json)
            checkError(doc)
            return when (doc.optInt("task_status", -1)) {
                0, 1 -> null
                2 -> {
                    val urls = doc.optJSONObject("data")?.optJSONArray("result_urls")
                    require(urls != null && urls.length() > 0) {
                        "Cloud ประมวลผลแล้วแต่ไม่ส่งภาพกลับ"
                    }
                    urls.optString(0).also { validateResultUrl(it) }
                }
                else -> error("Cloud ส่งสถานะงานไม่ถูกต้อง")
            }
        }

        private fun checkError(doc: JSONObject) {
            val detail = doc.optJSONObject("error_detail") ?: return
            val code = detail.optString("code")
            if (code.isNotEmpty() && code != "0" && code != "200") {
                // Do not echo server-generated messages containing URLs,
                // credentials or sensitive request details into UI.
                throw IOException("Cloud ตอบกลับข้อผิดพลาดรหัส ${code.take(30)}")
            }
        }

        fun validateResultUrl(value: String): HttpUrl {
            val url = value.toHttpUrlOrNull()
                ?: throw IOException("Cloud ส่ง URL ผลลัพธ์ไม่ถูกต้อง")
            require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) {
                "URL ผลลัพธ์ต้องใช้ HTTPS"
            }
            require(url.host.isNotBlank() && !url.host.equals("localhost",true)) {
                "URL ผลลัพธ์ไม่ปลอดภัย"
            }
            // Prevent remote jobs from providing loopback/private LAN URLs.
            val host = url.host.lowercase()
            require(!host.endsWith(".local") && !host.endsWith(".internal") &&
                !host.endsWith(".localhost") && !host.contains(':') &&
                !host.matches(Regex("""[0-9.]+""")) &&
                url.port == 443) { "URL ผลลัพธ์เป็นที่อยู่ภายในเครือข่ายหรือพอร์ตไม่ปลอดภัย" }
            return url
        }
    }

    suspend fun remove(
        input: Bitmap,
        apiKey: String,
        progress: (String) -> Unit
    ): Bitmap = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "ต้องใส่ API Key ของ AILabTools ก่อน" }
        require(!apiKey.contains('\n') && !apiKey.contains('\r') && apiKey.length < 512) {
            "API Key ไม่ถูกต้อง"
        }
        require(input.width in 1..4095 && input.height in 1..4095) {
            "ภาพที่ส่ง Cloud ต้องมีความละเอียดต่ำกว่า 4096 พิกเซล"
        }
        val bytes = ByteArrayOutputStream().use { out ->
            check(input.compress(Bitmap.CompressFormat.JPEG, 92, out)) { "แปลงภาพส่ง Cloud ไม่สำเร็จ" }
            out.toByteArray()
        }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "ไฟล์ส่ง Cloud เกิน 10 MB" }
        ensureActive()
        progress("กำลังส่งเฉพาะภาพครอปใบหน้าไป Cloud…")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "keeper-face.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder().url(submitUrl)
            .header("ailabapi-api-key", apiKey)
            .post(body).build()
        val taskId = parseTaskId(execute(request))
        var resultUrl: String? = null
        for (attempt in 0 until MAX_POLL_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            delay(POLL_INTERVAL_MS)
            progress("Cloud กำลังลบหนวด… (${attempt+1}/$MAX_POLL_ATTEMPTS)")
            val queryUrlSafe = queryUrl.toHttpUrlOrNull()
                ?: throw IOException("Cloud endpoint ไม่ถูกต้อง")
            val query = Request.Builder()
                .url(queryUrlSafe.newBuilder().addQueryParameter("task_id",taskId).build())
                .header("ailabapi-api-key", apiKey)
                .get().build()
            val response = execute(query)
            resultUrl = parseResult(response)
            if (resultUrl != null) break
        }
        val url = resultUrl ?: throw IOException("Cloud ใช้เวลานานเกินไป โปรดลองตรวจสอบสถานะงานกับผู้ให้บริการ")
        currentCoroutineContext().ensureActive()
        progress("กำลังรับภาพที่ลบหนวดแล้ว…")
        val imageRequest = Request.Builder().url(validateResultUrl(url)).get().build()
        val payload = client.newCall(imageRequest).execute().use { res ->
            if (!res.isSuccessful) throw IOException("ดาวน์โหลดภาพผลลัพธ์ไม่สำเร็จ (HTTP ${res.code})")
            val contentType = res.header("Content-Type").orEmpty()
            if (!contentType.startsWith("image/")) throw IOException("Cloud ไม่ได้ส่งไฟล์ภาพกลับ")
            if (res.body == null || res.body!!.contentLength() > MAX_RESULT_BYTES) {
                throw IOException("ไฟล์ภาพจาก Cloud ใหญ่เกินกำหนด")
            }
            val stream = res.body!!.byteStream()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            stream.use {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = it.read(buf)
                    if (n < 0) break
                    if (out.size()+n > MAX_RESULT_BYTES) throw IOException("ผลลัพธ์มีขนาดเกิน 22 MB")
                    out.write(buf,0,n)
                }
            }
            out.toByteArray()
        }
        val bitmap = BitmapFactory.decodeByteArray(payload,0,payload.size)
            ?: throw IOException("เปิดภาพผลลัพธ์จาก Cloud ไม่สำเร็จ")
        require(bitmap.width > 50 && bitmap.height > 50) { "Cloud ส่งภาพเล็กผิดปกติ" }
        bitmap
    }

    private fun execute(request: Request): String {
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("บริการ Cloud ตอบกลับ HTTP ${response.code}")
            val body = response.body ?: throw IOException("Cloud ไม่ส่งข้อมูลตอบกลับ")
            if (body.contentLength() > 1_000_000L) throw IOException("Cloud ส่งข้อมูลตอบกลับใหญ่ผิดปกติ")
            val text = body.string()
            if (text.length > 1_000_000) throw IOException("Cloud ส่งข้อมูลตอบกลับใหญ่ผิดปกติ")
            text
        }
    }
}
