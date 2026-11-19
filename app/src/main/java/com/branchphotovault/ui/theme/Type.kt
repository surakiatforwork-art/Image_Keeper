package com.branchphotovault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.branchphotovault.R

@OptIn(ExperimentalTextApi::class)
private val KeeperFont = FontFamily(
    Font(R.font.noto_sans_thai,weight=FontWeight.Normal,variationSettings=FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_thai,weight=FontWeight.Medium,variationSettings=FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_thai,weight=FontWeight.SemiBold,variationSettings=FontVariation.Settings(FontVariation.weight(600))))

private fun type(size: Int,line: Int,weight: FontWeight=FontWeight.Normal) = TextStyle(
    fontFamily=KeeperFont,fontWeight=weight,fontSize=size.sp,lineHeight=line.sp)

val AppTypography = Typography(
    headlineLarge=type(30,40,FontWeight.SemiBold),headlineMedium=type(26,36,FontWeight.SemiBold),
    headlineSmall=type(24,32,FontWeight.SemiBold),titleLarge=type(21,30,FontWeight.SemiBold),
    titleMedium=type(16,24,FontWeight.SemiBold),titleSmall=type(14,22,FontWeight.Medium),
    bodyLarge=type(16,25),bodyMedium=type(14,23),bodySmall=type(12,19),
    labelLarge=type(14,22,FontWeight.SemiBold),labelMedium=type(12,18,FontWeight.Medium),labelSmall=type(11,17,FontWeight.Medium))

