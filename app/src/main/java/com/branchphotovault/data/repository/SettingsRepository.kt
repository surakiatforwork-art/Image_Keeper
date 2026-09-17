package com.branchphotovault.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.branchphotovault.data.model.ImageAspectRatioOption
import com.branchphotovault.data.model.ImageSizeOption
import com.branchphotovault.util.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "branch_photo_vault_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val retentionMonths = intPreferencesKey("retention_months")
        val exportFolderPattern = stringPreferencesKey("export_folder_pattern")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val imageLongEdge = intPreferencesKey("image_long_edge")
        val imageAspectRatioKey = stringPreferencesKey("image_aspect_ratio_key")
    }

    val baseUrlFlow: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: AppConfig.DEFAULT_APPS_SCRIPT_URL
    }

    val retentionMonthsFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.retentionMonths] ?: AppConfig.DEFAULT_RETENTION_MONTHS
    }

    val exportFolderPatternFlow: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.exportFolderPattern] ?: AppConfig.DEFAULT_EXPORT_FOLDER_PATTERN
    }

    val lastSyncAtFlow: Flow<Long?> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.lastSyncAt]
    }

    val imageLongEdgeFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.imageLongEdge] ?: ImageSizeOption.LARGE.longEdge
    }

    val imageAspectRatioKeyFlow: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.imageAspectRatioKey] ?: ImageAspectRatioOption.ORIGINAL.key
    }

    suspend fun getBaseUrl(): String = baseUrlFlow.first()

    suspend fun getRetentionMonths(): Int = retentionMonthsFlow.first()

    suspend fun getExportFolderPattern(): String = exportFolderPatternFlow.first()

    suspend fun getImageLongEdge(): Int = imageLongEdgeFlow.first()

    suspend fun getImageAspectRatioKey(): String = imageAspectRatioKeyFlow.first()

    suspend fun setBaseUrl(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.baseUrl] = value.trim()
        }
    }

    suspend fun setRetentionMonths(months: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.retentionMonths] = months.coerceAtLeast(1)
        }
    }

    suspend fun setExportFolderPattern(pattern: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.exportFolderPattern] = pattern.trim().ifBlank {
                AppConfig.DEFAULT_EXPORT_FOLDER_PATTERN
            }
        }
    }

    suspend fun setLastSyncAt(epochMillis: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.lastSyncAt] = epochMillis
        }
    }

    suspend fun setImageLongEdge(longEdge: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.imageLongEdge] = ImageSizeOption.fromLongEdge(longEdge).longEdge
        }
    }

    suspend fun setImageAspectRatioKey(key: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.imageAspectRatioKey] = ImageAspectRatioOption.fromKey(key).key
        }
    }
}
