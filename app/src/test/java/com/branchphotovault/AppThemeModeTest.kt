package com.branchphotovault

import com.branchphotovault.data.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test fun defaultsToSystemForMissingOrUnknownChoice() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored(""))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored("UNKNOWN_FUTURE_VALUE"))
    }
    @Test fun restoresAllThreeStableStoredValues() {
        for (mode in AppThemeMode.entries) {
            assertEquals(mode, AppThemeMode.fromStored(mode.name))
        }
    }
}
