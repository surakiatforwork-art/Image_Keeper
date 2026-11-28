package com.branchphotovault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.branchphotovault.data.model.BranchListItem
import com.branchphotovault.ui.viewmodel.BranchListUiState
import com.branchphotovault.ui.screens.BranchListContent
import org.junit.Assert.assertTrue
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
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("ค้นหาร้าน").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("1600")
        compose.runOnIdle { assertEquals("1600",query) }
        compose.onNodeWithText("ค้นหา").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("1600",query) }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("ปิดการค้นหา").performClick()
        compose.onNodeWithContentDescription("ค้นหาร้าน").assertIsDisplayed()
        compose.runOnIdle { assertEquals("",query) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun compactResultsStayNearBottomAndAreClickable() {
        var opened=""
        val row=BranchListItem("7-ELEVEN","1600","Test branch",null,null,null,3)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(250.dp).testTag("resultArea")) {
                    BranchListContent(BranchListUiState(searchText="1600",branches=listOf(row)),
                        compactSearch=true,onToggleAccountFilter={},onClearAccountFilters={},
                        onSortChange={},onOnlyWithPhotosChange={},onBranchClick={_,code->opened=code})
                }
            }
        }
        compose.onNodeWithText("รายการสาขา").assertDoesNotExist()
        val area=compose.onNodeWithTag("resultArea").fetchSemanticsNode().boundsInRoot
        val result=compose.onNodeWithText("Test branch").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("One result should stay in lower half of the available list",result.center.y>area.center.y)
        compose.onNodeWithText("Test branch").performClick()
        compose.runOnIdle { assertEquals("1600",opened) }
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
