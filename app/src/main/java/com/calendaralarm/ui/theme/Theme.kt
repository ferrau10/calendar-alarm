package com.calendaralarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandBlue = Color(0xFF1A73E8)
private val BrandBlueLight = Color(0xFF8AB4F8)

@Composable
fun CalendarAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Use the wallpaper-based colors on Android 12+; false gives the brand colors (screenshots). */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(primary = BrandBlueLight, primaryContainer = Color(0xFF0B3D91))
        else -> lightColorScheme(
            primary = BrandBlue,
            primaryContainer = Color(0xFFD3E3FD),
            onPrimaryContainer = Color(0xFF041E49),
            secondaryContainer = Color(0xFFE8F0FE),
            onSecondaryContainer = Color(0xFF041E49),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
