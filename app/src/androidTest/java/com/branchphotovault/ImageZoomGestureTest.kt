package com.branchphotovault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.branchphotovault.ui.components.ImageZoomState
import com.branchphotovault.ui.components.ZoomableImageViewport
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImageZoomGestureTest {
    @get:Rule val compose=createComposeRule()
    @Test fun twoFingerPinchAndDoubleTapReset() {
        val state=ImageZoomState()
        compose.setContent {
            ZoomableImageViewport("fixture",Modifier.size(300.dp).testTag("preview"),IntSize(400,400),state=state) {
                Box(Modifier.fillMaxSize().background(Color.Cyan))
            }
        }
        compose.onNodeWithTag("preview").performTouchInput {
            pinch(center-Offset(40f,0f),center+Offset(40f,0f),center-Offset(140f,0f),center+Offset(140f,0f),300)
        }
        compose.runOnIdle { assertTrue(state.scale>1.2f) }
        compose.onNodeWithTag("preview").performTouchInput { advanceEventTime(500); doubleClick(center) }
        compose.runOnIdle { assertEquals(1f,state.scale,0f); assertEquals(Offset.Zero,state.offset) }
    }

    @Test fun normalSingleFingerSwipeStillChangesPhoto() {
        var page=0
        compose.setContent {
            val pager=rememberPagerState(pageCount={2})
            LaunchedEffect(pager.currentPage) { page=pager.currentPage }
            HorizontalPager(state=pager,modifier=Modifier.size(300.dp).testTag("pager")) { index ->
                ZoomableImageViewport(index,Modifier.fillMaxSize(),IntSize(400,400)) {
                    Box(Modifier.fillMaxSize().background(if(index==0) Color.Cyan else Color.Magenta))
                }
            }
        }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1,page) }
    }
}
