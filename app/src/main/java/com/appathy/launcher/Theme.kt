package com.appathy.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LauncherColors {
    val text = Color.White
    val textMuted = Color.White.copy(alpha = 0.6f)
    val textDim = Color.White.copy(alpha = 0.45f)

    val accent = Color(0xFF7FA6D8)
    val positive = Color(0xFF5BD6A8)
    val danger = Color(0xFFE2687A)
    val action = Color(0xFF2C4A6E)

    val glass = Color.White.copy(alpha = 0.10f)
    val glassRaised = Color.White.copy(alpha = 0.12f)
    val glassStrong = Color.White.copy(alpha = 0.16f)
    val glassSoft = Color.White.copy(alpha = 0.08f)
    val edge = Color.White.copy(alpha = 0.14f)
    val edgeStrong = Color.White.copy(alpha = 0.22f)

    val scrim = Color(0xE60B0D10)
    val scrimOpaque = Color(0xF20B0D10)
    val scrimDim = Color(0xB3000000)
    val scrimControl = Color(0xCC05070A)

    val settingsTile = Color(0xFF6E6E73)
    val badge = Color(0xFFE8E8E8)
    val badgeInk = Color(0xFF333333)
}

object LauncherType {
    val screenTitle = 26.sp
    val panelTitle = 22.sp
    val sectionValue = 17.sp
    val body = 15.sp
    val bodySmall = 14.sp
    val label = 13.sp
    val caption = 12.sp
    val iconLabel = 11.sp
    val micro = 10.sp
}

object LauncherShape {
    val card = RoundedCornerShape(14.dp)
    val panel = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(20.dp)
    val dock = RoundedCornerShape(30.dp)
    val overlay = RoundedCornerShape(28.dp)
    val chip = RoundedCornerShape(12.dp)
}

const val IconCorner = 0.235f

object LauncherSize {
    val icon = 60.dp
    val iconDrag = 68.dp
    val iconFolder = 52.dp
    val iconList = 44.dp
    val iconSearch = 40.dp
    val iconRow = 38.dp
    val iconMini = 23.dp
}
