package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.branchphotovault.retouch.ManualRetouch
import com.branchphotovault.retouch.RetouchPoint
import com.branchphotovault.retouch.RetouchStroke
import com.branchphotovault.ui.components.ManualRetouchEditor
import com.branchphotovault.ui.theme.BranchPhotoVaultTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualHealAndMultitouchTest {
    @get:Rule val compose=createComposeRule()

    @Test fun healingRepairsHairInPixelsUntouchedByAIAndNeverModifiesOriginal() {
        val src=Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(170,135,115))
        val cx=90;val cy=93
        for(y in 84..102) for(x in 81..99) {
            if((x-cx)*(x-cx)+(y-cy)*(y-cy)<95)
                src.setPixel(x,y,Color.rgb(41,34,32))
        }
        // AI missed the hair entirely. This must still work.
        val untouchedAI=src.copy(Bitmap.Config.ARGB_8888,false)
        val strokes=listOf(RetouchStroke(
            points=listOf(RetouchPoint(cx/180f,cy/180f)),
            radius=.09f,restore=false,heal=true,
            donor=RetouchPoint(.20f,.20f)
        ))
        val result=ManualRetouch.compose(src,untouchedAI,strokes,1f)
        try {
            val restored=result.getPixel(cx,cy)
            assertTrue("Dark hair must become skin-like",Color.red(restored)>145)
            assertEquals("Pixels far from brush unchanged",src.getPixel(10,10),result.getPixel(10,10))
            assertEquals("Unpainted pixels inside brush rectangle unchanged",
                src.getPixel(62,62),result.getPixel(62,62))
            assertEquals("Source never mutated",Color.rgb(41,34,32),src.getPixel(cx,cy))
            assertEquals("AI never mutated",src.getPixel(cx,cy),untouchedAI.getPixel(cx,cy))
            val zero=ManualRetouch.compose(src,untouchedAI,strokes,0f)
            try {assertEquals(src.getPixel(cx,cy),zero.getPixel(cx,cy))}
            finally {zero.recycle()}
        } finally {
            result.recycle();src.recycle();untouchedAI.recycle()
        }
    }

    @Test fun singleFingerHealAfterTwoFingerZoomRepairsPreciseCenter() {
        val src=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
            .apply {eraseColor(Color.rgb(173,141,122))}
        for(y in 94..106) for(x in 94..106)
            src.setPixel(x,y,Color.rgb(55,38,32))
        val ai=src.copy(Bitmap.Config.ARGB_8888,false)
        var output:Bitmap?=null
        var mounted by mutableStateOf(true)
        try {
            compose.setContent {BranchPhotoVaultTheme {
                if(mounted) ManualRetouchEditor(src,ai,1f,false,null,{},{},{output=it})
            }}
            compose.waitUntil(8000){output!=null}
            compose.onNodeWithTag("tool-heal").performClick()
            compose.onNodeWithTag("retouch-preview").performTouchInput {
                pinch(center-Offset(35f,0f),center+Offset(35f,0f),
                    center-Offset(95f,0f),center+Offset(95f,0f),300)
            }
            compose.onNodeWithTag("retouch-preview").assert(
                SemanticsMatcher("zoomed") {
                    it.config.getOrElse(SemanticsProperties.StateDescription){""}.startsWith("Zoom")
                }
            )
            // Pinching alone must NOT accidentally commit any paint.
            compose.runOnIdle { assertEquals(Color.rgb(55,38,32),output?.getPixel(100,100)) }
            compose.onNodeWithTag("retouch-preview").performTouchInput { click(center) }
            compose.waitUntil(8000) {output?.getPixel(100,100)?.let{Color.red(it)>135} == true}
            assertEquals(Color.rgb(55,38,32),src.getPixel(100,100))
        } finally {
            compose.runOnIdle {mounted=false}
            compose.waitForIdle()
            src.recycle();ai.recycle()
        }
    }

    @Test fun twoFingerPinchStillWorksWhileHealingRestoringAndFraming() {
        val src=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
            .apply {eraseColor(Color.rgb(170,135,115))}
        val ai=src.copy(Bitmap.Config.ARGB_8888,false)
        var output:Bitmap?=null
        var mounted by mutableStateOf(true)
        try {
            compose.setContent {
                BranchPhotoVaultTheme {
                    if(mounted) ManualRetouchEditor(
                        src,ai,1f,false,null,{},{},{output=it}
                    )
                }
            }
            compose.waitUntil(8000) {output!=null}
            for(tool in listOf("ซ่อมหนวด","คืนภาพ","กรอบ")) {
                compose.onNodeWithText(tool).performClick()
                compose.onNodeWithTag("retouch-preview").performTouchInput {
                    pinch(
                        center-Offset(30f,0f),center+Offset(30f,0f),
                        center-Offset(80f,0f),center+Offset(80f,0f),300
                    )
                }
                compose.onNodeWithTag("retouch-preview").assert(
                    SemanticsMatcher("zoom active while $tool") {
                        it.config.getOrElse(SemanticsProperties.StateDescription){""}
                            .startsWith("Zoom")
                    }
                )
                // Reset zoom in navigation mode before testing next tool.
                compose.onNodeWithTag("tool-pan").performClick()
                compose.onNodeWithTag("retouch-preview").performTouchInput { doubleClick(center) }
                compose.onNodeWithTag("retouch-preview").assert(
                    SemanticsMatcher("back to fit") {
                        it.config.getOrElse(SemanticsProperties.StateDescription){""}=="Fit"
                    }
                )
            }
        } finally {
            compose.runOnIdle {mounted=false}
            compose.waitForIdle()
            src.recycle();ai.recycle()
        }
    }
}
