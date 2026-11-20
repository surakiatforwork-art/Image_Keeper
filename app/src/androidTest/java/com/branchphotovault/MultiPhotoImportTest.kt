package com.branchphotovault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.data.local.*
import com.branchphotovault.data.remote.AppsScriptService
import com.branchphotovault.data.repository.*
import com.branchphotovault.ui.viewmodel.BranchPreviewViewModel
import com.branchphotovault.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MultiPhotoImportTest {
    @Test fun pickerAllowsMultipleAndBatchContinuesAfterInvalidPhoto() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(ActivityResultContracts.GetMultipleContents().createIntent(context,"image/*")
            .getBooleanExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,false))
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val settings=SettingsRepository(context)
        val photos=PhotoRepository(db.photoDao(),db.branchDao(),settings,ImageProcessor(context),MediaStoreExporter(context),context)
        val service=Retrofit.Builder().baseUrl("https://example.invalid/").build().create(AppsScriptService::class.java)
        val branches=BranchRepository(db.branchDao(),db.photoDao(),service,settings)
        val branch=BranchEntity("Keeper test ${UUID.randomUUID()}","multi","Test",null,null,null)
        db.branchDao().upsert(branch)
        val source=File(context.cacheDir,"import-test-${UUID.randomUUID()}.jpg")
        val bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
        bitmap.recycle()
        val original=source.readBytes()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val store=ViewModelStore()
        try {
            val vm=withContext(Dispatchers.Main) { BranchPreviewViewModel(branches,photos,context,branch.account,branch.branchCode).also { store.put("test",it) } }
            scope.launch { vm.uiState.collect() }
            withTimeout(5_000) { while(vm.uiState.value.branch==null) delay(20) }
            val second=File(context.cacheDir,"import-test-second-${UUID.randomUUID()}.jpg")
            try {
                source.copyTo(second)
                withContext(Dispatchers.Main) {
                    vm.importPhotos(listOf(Uri.fromFile(source),Uri.fromFile(File(context.cacheDir,"missing-${UUID.randomUUID()}.jpg")),Uri.fromFile(second)))
                }
                withTimeout(15_000) { while(vm.uiState.value.message==null || vm.uiState.value.isProcessing) delay(30) }
                val imported=photos.getPhotosForBranch(branch.account,branch.branchCode)
                assertEquals(2,imported.size)
                assertTrue(vm.uiState.value.message!!.contains("ไม่สำเร็จ 1"))
                assertTrue(imported.all { File(it.localMainPath).isFile && File(it.localThumbPath).isFile })
                assertArrayEquals(original,source.readBytes())
            } finally { second.delete() }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            scope.cancel()
            photos.deletePhotos(photos.getPhotosForBranch(branch.account,branch.branchCode).map { it.id })
            source.delete()
            db.close()
        }
    }
}
