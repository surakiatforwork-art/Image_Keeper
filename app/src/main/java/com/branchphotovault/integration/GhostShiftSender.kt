package com.branchphotovault.integration

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.branchphotovault.data.local.PhotoEntity
import java.io.File

object GhostShiftSender {
    private const val GHOST_SHIFT_PACKAGE = "com.phantom.ghostshift"
    private const val AUTHORITY = "com.branchphotovault.ghostshiftshare"

    const val EXTRA_REMARKS = "com.phantom.ghostshift.EXTRA_REMARKS"

    fun send(context: Context, photos: List<PhotoEntity>): Boolean {
        if (photos.isEmpty()) return false
        val uris = ArrayList(photos.mapNotNull { photo ->
            File(photo.localMainPath).takeIf(File::exists)?.let { file ->
                FileProvider.getUriForFile(context, AUTHORITY, file)
            }
        })
        if (uris.size != photos.size) return false

        val remarks = ArrayList(photos.map { photo ->
            listOf(photo.branchCode, photo.shopName).filter(String::isNotBlank).joinToString(" | ")
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            setPackage(GHOST_SHIFT_PACKAGE)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putStringArrayListExtra(EXTRA_REMARKS, remarks)
            clipData = ClipData.newUri(context.contentResolver, "GhostShift photo", uris.first())
            uris.drop(1).forEach { clipData?.addItem(ClipData.Item(it)) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}
