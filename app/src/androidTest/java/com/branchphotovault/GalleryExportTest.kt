package com.branchphotovault

import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.util.GalleryNaming
import com.branchphotovault.util.MediaStoreExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GalleryExportTest {
    @Test fun exportsDifferentBranchesIntoOneAlbumWithoutChangingSources() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File(context.cacheDir,"gallery-test-${UUID.randomUUID()}.jpg")
        val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
        bitmap.recycle()
        val original=source.readBytes()
        val resolver=context.contentResolver
        val names=listOf(GalleryNaming.fileName("CJ MORE","001"),GalleryNaming.fileName("7-ELEVEN","002"))
        try {
            for(name in names) {
                assertTrue(MediaStoreExporter(context).exportImage(source,name))
                resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.SIZE),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ?",arrayOf(name),null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertEquals(GalleryNaming.RELATIVE_PATH,it.getString(0))
                    assertTrue(it.getLong(1)>0)
                    assertFalse(it.moveToNext())
                }
            }
            assertArrayEquals(original,source.readBytes())
        } finally {
            for(name in names) resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",arrayOf(name),null)?.use {
                while(it.moveToNext()) resolver.delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,it.getLong(0)),null,null)
            }
            source.delete()
        }
    }
}
