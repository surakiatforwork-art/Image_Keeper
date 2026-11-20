package com.branchphotovault

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.data.local.*
import com.branchphotovault.data.model.BranchSortOption
import com.branchphotovault.util.BranchQueryBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchFiltersTest {
    @Test fun timeSortingAndPhotoFilterCombineWithAccountAndSearch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            for (code in listOf("old", "new", "empty", "tie"))
                db.branchDao().upsert(BranchEntity("CJ MORE", code, "Shop $code", 9, null, null))
            db.branchDao().upsert(BranchEntity("OTHER", "other", "Other shop", 1, null, null))
            for ((id, code, time) in listOf(Triple("1", "old", 10L), Triple("2", "new", 5L),
                    Triple("3", "new", 30L), Triple("4", "tie", 30L))) {
                db.photoDao().insert(PhotoEntity(id,"CJ MORE",code,"Shop $code",9,"unused","unused",time,"1970-01"))
            }
            suspend fun codes(sort: BranchSortOption, only: Boolean = false, search: String = "") =
                db.branchDao().observeBranchListItems(BranchQueryBuilder.build(search,"CJ MORE",sort,only)).first().map { it.branchCode }
            assertEquals(listOf("old","new","tie","empty"),codes(BranchSortOption.OLDEST_FIRST))
            assertEquals(listOf("new","tie","old","empty"),codes(BranchSortOption.NEWEST_FIRST))
            assertEquals(listOf("new","tie","old"),codes(BranchSortOption.NEWEST_FIRST,true))
            assertEquals(listOf("new"),codes(BranchSortOption.ACCOUNT,true,"new"))
            for (sort in BranchSortOption.entries) assertEquals(3,codes(sort,true).size)
            db.photoDao().deleteByIds(listOf("1"))
            assertEquals(listOf("new","tie"),codes(BranchSortOption.NEWEST_FIRST,true))
        } finally { db.close() }
    }
}
