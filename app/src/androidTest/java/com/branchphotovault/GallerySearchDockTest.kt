package com.branchphotovault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.branchphotovault.ui.screens.GallerySearchDock
import com.branchphotovault.ui.screens.GalleryRibbonHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySearchDockTest {
    @get:Rule val compose=createComposeRule()

    @Test fun searchExpandsAcceptsQueryAndCloses() {
        var query by mutableStateOf("")
        compose.setContent {
            MaterialTheme { GallerySearchDock(query,{query=it},false) }
        }
        compose.onNodeWithText("ค้นหาชื่อร้าน รหัสสาขา หรือ Account").assertDoesNotExist()
        compose.onNodeWithContentDescription("ค้นหาร้าน").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("1600")
        compose.runOnIdle { assertEquals("1600",query) }
        compose.onNodeWithContentDescription("ปิดการค้นหา").performClick()
        compose.onNodeWithContentDescription("ค้นหาร้าน").assertIsDisplayed()
        compose.runOnIdle { assertEquals("",query) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun addBranchMovedToOverflow() {
        var added=0
        compose.setContent {
            MaterialTheme { GalleryRibbonHeader(false,false,{}, {}, {added++}) }
        }
        compose.onNodeWithText("เพิ่มรายการร้าน").assertDoesNotExist()
        compose.onNodeWithContentDescription("เมนูเพิ่มเติม").performClick()
        compose.onNodeWithText("รีเฟรชรายการสาขา").assertIsDisplayed()
        compose.onNodeWithText("ตั้งค่า").assertIsDisplayed()
        compose.onNodeWithText("เพิ่มรายการร้าน").performClick()
        compose.runOnIdle { assertEquals(1,added) }
    }
}
