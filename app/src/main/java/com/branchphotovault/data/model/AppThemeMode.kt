package com.branchphotovault.data.model

/**
 * User choice takes precedence over Android night-mode.
 * SYSTEM preserves the application's existing default.
 */
enum class AppThemeMode(val label: String, val description: String) {
    SYSTEM("ตามระบบ", "ปรับตามธีมของโทรศัพท์"),
    LIGHT("สว่าง", "พื้นหลังสว่าง อ่านง่ายในที่มีแสง"),
    DARK("มืด", "พื้นหลังเข้ม ถนอมสายตาในที่แสงน้อย");

    companion object {
        fun fromStored(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
