package com.branchphotovault.data.repository

import com.branchphotovault.data.local.PhotoEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFilterTest {
    @Test fun dateRangeIncludesEndDateAndShopUsesAccountAndCode() {
        val day = LocalDate.of(2026, 11, 14)
        val midnightAfter = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val photo = PhotoEntity("existing-id", "A", "001", "Shop", 3, "", "", midnightAfter - 1, "2026-11")
        val filter = TransferFilter(day, day, "A" to "001")
        assertTrue(filter.matches(photo))
        assertFalse(filter.matches(photo.copy(createdAt = midnightAfter)))
        assertFalse(filter.matches(photo.copy(account = "B")))
        assertTrue(TransferFilter().matches(photo))
    }
}
