package com.branchphotovault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.data.repository.GalleryLayout
import com.branchphotovault.data.repository.GalleryPhotoSort
import com.branchphotovault.data.repository.GalleryViewConfig
import com.branchphotovault.data.repository.GalleryViewPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryViewPreferencesTest {

    @Test
    fun settingsSurviveNewPreferencesInstanceAndStayPerBranch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "UI_LAYOUT_TEST_" + System.nanoTime()
        val first = GalleryViewPreferences(context)
        assertEquals(GalleryViewConfig(), first.load("SME", id))
        assertEquals(GalleryViewConfig(), first.load("CVS", id))

        val changed = GalleryViewConfig(
            layout = GalleryLayout.LIST,
            columns = 6,
            sort = GalleryPhotoSort.OLDEST_FIRST
        )
        first.save("SME", id, changed)
        val reloaded = GalleryViewPreferences(context)
        assertEquals(changed, reloaded.load("SME", id))
        assertEquals(GalleryViewConfig(), reloaded.load("CVS", id))

        reloaded.save(
            "SME", id,
            changed.copy(layout = GalleryLayout.GRID, columns = 4)
        )
        assertEquals(
            changed.copy(layout = GalleryLayout.GRID, columns = 4),
            GalleryViewPreferences(context).load("SME", id)
        )
    }
}
