package com.branchphotovault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.data.model.AppThemeMode
import com.branchphotovault.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferenceTest {
    @Test
    fun persistsAllChoicesAfterRecreatingRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(context)
        val original = repository.appThemeModeFlow.first()
        try {
            for (mode in AppThemeMode.entries) {
                repository.setAppThemeMode(mode)
                assertEquals(mode, SettingsRepository(context).appThemeModeFlow.first())
            }
        } finally {
            repository.setAppThemeMode(original)
        }
        assertEquals(original, SettingsRepository(context).appThemeModeFlow.first())
    }
}
