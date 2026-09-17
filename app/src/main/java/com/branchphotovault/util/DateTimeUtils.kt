package com.branchphotovault.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private val dateTimeFormatter = SimpleDateFormat(AppConfig.DATE_TIME_PATTERN, Locale.getDefault())
    private val monthKeyFormatter = SimpleDateFormat(AppConfig.MONTH_KEY_PATTERN, Locale.getDefault())

    fun nowFormatted(): String = dateTimeFormatter.format(Date())

    fun monthKey(epochMillis: Long): String = monthKeyFormatter.format(Date(epochMillis))
}

