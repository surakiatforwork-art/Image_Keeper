package com.branchphotovault.data.model

import com.branchphotovault.util.AppConfig

enum class ImageSizeOption(
    val longEdge: Int,
    val label: String
) {
    SMALL(longEdge = 1440, label = "1440 px"),
    MEDIUM(longEdge = 1920, label = "1920 px"),
    LARGE(longEdge = AppConfig.MAIN_LONG_EDGE, label = "2560 px"),
    XL(longEdge = 3200, label = "3200 px");

    companion object {
        fun fromLongEdge(longEdge: Int?): ImageSizeOption {
            return entries.firstOrNull { it.longEdge == longEdge } ?: LARGE
        }
    }
}

