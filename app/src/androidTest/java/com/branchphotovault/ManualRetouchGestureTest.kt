package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.branchphotovault.ui.components.HoldOriginalButton
import com.branchphotovault.ui.components.ManualRetouchEditor
import com.branchphotovault.ui.theme.BranchPhotoVaultTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualRetouchGestureTest {
    @get:Rule val compose=createComposeRule()
    @Test fun holdReleasesAndCancellationResetsOriginalView() {
        var held=false
        compose.setContent { BranchPhotoVaultTheme { HoldOriginalButton(true) { held=it } } }
        compose.onNodeWithTag("hold-original").performTouchInput { down(center); advanceEventTime(300) }
        compose.runOnIdle { assertTrue(held) }
        compose.onNodeWithTag("hold-original").performTouchInput { up() }
        compose.runOnIdle { assertFalse(held) }
        compose.onNodeWithTag("hold-original").performTouchInput { down(center); advanceEventTime(300) }
        compose.runOnIdle { assertTrue(held) }
        compose.onNodeWithTag("hold-original").performTouchInput { cancel() }
        compose.runOnIdle { assertFalse(held) }
    }
    @Test fun brushUndoRedoAndZoomOperateOnTheRenderedCopy() {
        val source=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val ai=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        var output: Bitmap?=null
        var show by mutableStateOf(true)
        try {
            compose.setContent { BranchPhotoVaultTheme { Column {
                if(show) ManualRetouchEditor(source,ai,1f,false,null,{},{},{ output=it })
            } } }
            compose.waitUntil(5000) { output!=null }
            compose.onNodeWithText("คืนต้นฉบับ").performClick()
            compose.onNodeWithTag("retouch-preview").performTouchInput { click(center) }
            compose.waitUntil(5000) { output?.getPixel(100,100)==Color.BLUE }
            compose.onNodeWithContentDescription("ย้อนกลับ").performClick()
            compose.waitUntil(5000) { output?.getPixel(100,100)==Color.RED }
            compose.onNodeWithContentDescription("ทำซ้ำ").performClick()
            compose.waitUntil(5000) { output?.getPixel(100,100)==Color.BLUE }
            compose.onNodeWithText("ทารีทัช").performClick()
            compose.onNodeWithTag("retouch-preview").performTouchInput { click(center) }
            compose.waitUntil(5000) { output?.getPixel(100,100)==Color.RED }
            compose.onNodeWithText("กรอบใบหน้า").performClick()
            compose.onNodeWithTag("retouch-preview").performTouchInput {
                swipe(center-Offset(width*.15f,height*.15f),center+Offset(width*.15f,height*.15f),300)
            }
            compose.waitUntil(5000) { output?.getPixel(10,10)==Color.BLUE }
            assertEquals(Color.RED,output!!.getPixel(100,100))
            compose.onNodeWithContentDescription("ย้อนกลับ").performClick()
            compose.waitUntil(5000) { output?.getPixel(10,10)==Color.RED }
            compose.onNodeWithText("ขยาย/เลื่อน").performClick()
            compose.onNodeWithTag("retouch-preview").performTouchInput {
                pinch(center-Offset(35f,0f),center+Offset(35f,0f),center-Offset(100f,0f),center+Offset(100f,0f),300)
            }
            compose.onNodeWithTag("retouch-preview").assert(SemanticsMatcher("zoomed") {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }.startsWith("Zoom")
            })
            assertEquals(Color.BLUE,source.getPixel(100,100))
        } finally {
            compose.runOnIdle { show=false }
            compose.waitForIdle()
            source.recycle(); ai.recycle(); output?.recycle()
        }
    }
}
