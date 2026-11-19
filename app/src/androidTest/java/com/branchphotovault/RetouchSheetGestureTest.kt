package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.ui.screens.MoustacheRetouchDialog
import com.branchphotovault.ui.theme.BranchPhotoVaultTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RetouchSheetGestureTest {
    @get:Rule val compose=createComposeRule()
    @Test fun retouchSheetZoomResetsWithoutSavingOrDismissing() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"zoom-sheet-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.LTGRAY)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        val photo=PhotoEntity("zoom-fixture","TEST","TEST","Zoom fixture",null,file.path,file.path,0,"1970-01")
        var dismissed=false
        try {
            compose.setContent {
                BranchPhotoVaultTheme(darkTheme=false) {
                    CompositionLocalProvider(LocalAppContainer provides (context.applicationContext as BranchPhotoVaultApplication).container) {
                        MoustacheRetouchDialog(photo) { dismissed=true }
                    }
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("ไม่พบใบหน้าในภาพ").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("บันทึกฉบับแต่ง").assertIsNotEnabled()
            compose.onNodeWithTag("retouch-preview").performTouchInput {
                pinch(center-Offset(40f,0f),center+Offset(40f,0f),center-Offset(140f,0f),center+Offset(140f,0f),300)
            }
            assertTrue(compose.onNodeWithTag("retouch-preview").fetchSemanticsNode().config[SemanticsProperties.StateDescription].startsWith("Zoom"))
            compose.runOnIdle { assertFalse(dismissed) }
            compose.onNodeWithTag("retouch-preview").performTouchInput { advanceEventTime(500); doubleClick(center) }
            assertEquals("Fit",compose.onNodeWithTag("retouch-preview").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            compose.onNodeWithText("บันทึกฉบับแต่ง").assertIsNotEnabled()
            compose.onNodeWithText("ยกเลิก").performClick()
            compose.runOnIdle { assertTrue(dismissed) }
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
