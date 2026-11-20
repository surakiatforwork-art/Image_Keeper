package com.branchphotovault.util

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class GalleryNamingTest {
    @Test fun namesIncludeAccountBranchAndSaveTime() {
        assertEquals("LOTUS'S GO FRESH_001_19700101_070000_000_unique.jpg",
            GalleryNaming.fileName("LOTUS'S GO FRESH", "001", 0, "unique", ZoneId.of("Asia/Bangkok")))
        assertEquals("Pictures/Image Keeper/", GalleryNaming.RELATIVE_PATH)
    }
    @Test fun namesCannotCreateSubfoldersAndRemainBounded() {
        val name = GalleryNaming.fileName("a/b\\c\n", "../".repeat(100), 0, "id", ZoneId.of("UTC"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        assertTrue(name.toByteArray().size < 255)
    }
    @Test fun repeatedExportsAtSameMillisecondHaveDifferentNames() {
        assertNotEquals(GalleryNaming.fileName("CJ MORE", "1280", 0), GalleryNaming.fileName("CJ MORE", "1280", 0))
    }
    @Test fun longThaiMetadataStaysWithinFilesystemByteLimit() {
        assertTrue(GalleryNaming.fileName("สาขา".repeat(60),"ร้าน".repeat(60)).toByteArray().size < 255)
    }
}
