package com.branchphotovault.data.model

import com.branchphotovault.util.AppConfig

enum class ImageAspectRatioOption(
    val key: String,
    val label: String,
    val width: Int?,
    val height: Int?
) {
    ORIGINAL(
        key = AppConfig.ASPECT_RATIO_ORIGINAL,
        label = "Original",
        width = null,
        height = null
    ),
    RATIO_1_1(
        key = "RATIO_1_1",
        label = "1:1 Square",
        width = 1,
        height = 1
    ),
    RATIO_3_4(
        key = "RATIO_3_4",
        label = "3:4 Portrait",
        width = 3,
        height = 4
    ),
    RATIO_4_3(
        key = "RATIO_4_3",
        label = "4:3 Landscape",
        width = 4,
        height = 3
    ),
    RATIO_9_16(
        key = "RATIO_9_16",
        label = "9:16 Portrait",
        width = 9,
        height = 16
    ),
    RATIO_16_9(
        key = "RATIO_16_9",
        label = "16:9 Landscape",
        width = 16,
        height = 9
    );

    companion object {
        fun fromKey(key: String?): ImageAspectRatioOption {
            return entries.firstOrNull { it.key == key } ?: ORIGINAL
        }
    }

    fun portraitVariant(): ImageAspectRatioOption {
        return when (this) {
            ORIGINAL -> ORIGINAL
            RATIO_1_1 -> RATIO_1_1
            RATIO_3_4 -> RATIO_3_4
            RATIO_4_3 -> RATIO_3_4
            RATIO_9_16 -> RATIO_9_16
            RATIO_16_9 -> RATIO_9_16
        }
    }
}
