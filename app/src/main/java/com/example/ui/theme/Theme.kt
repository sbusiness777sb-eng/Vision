package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class VisionTab(val route: String, val label: String) {
    DASHBOARD("dashboard", "Dashboard"),
    ALARM("alarm", "Alarm"),
    DISTRACTION("distraction", "Distraction"),
    REPORT("report", "Report")
}

@Immutable
data class TabColorPalette(
    val isDark: Boolean,
    val primary: Color,
    val secondary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val onSurface: Color,
    val muted: Color,
    val outline: Color
)

fun paletteForTab(tab: VisionTab): TabColorPalette = when (tab) {
    VisionTab.DASHBOARD -> TabColorPalette(
        isDark = false,
        primary = VisionColors.DashboardPrimary,
        secondary = VisionColors.DashboardSecondary,
        onPrimary = Color.White,
        background = VisionColors.DashboardBackground,
        surface = VisionColors.DashboardSurface,
        surfaceElevated = VisionColors.DashboardSurfaceElevated,
        onSurface = VisionColors.DashboardOnSurface,
        muted = VisionColors.DashboardMuted,
        outline = VisionColors.DashboardOutline
    )
    VisionTab.ALARM -> TabColorPalette(
        isDark = true,
        primary = VisionColors.AlarmPrimary,
        secondary = VisionColors.AlarmSecondary,
        onPrimary = Color(0xFF141815),
        background = VisionColors.AlarmBackground,
        surface = VisionColors.AlarmSurface,
        surfaceElevated = VisionColors.AlarmSurfaceElevated,
        onSurface = VisionColors.AlarmOnSurface,
        muted = VisionColors.AlarmMuted,
        outline = VisionColors.AlarmOutline
    )
    VisionTab.DISTRACTION -> TabColorPalette(
        isDark = false,
        primary = VisionColors.DistractionPrimary,
        secondary = VisionColors.DistractionSecondary,
        onPrimary = Color.White,
        background = VisionColors.DistractionBackground,
        surface = VisionColors.DistractionSurface,
        surfaceElevated = VisionColors.DistractionSurfaceElevated,
        onSurface = VisionColors.DistractionOnSurface,
        muted = VisionColors.DistractionMuted,
        outline = VisionColors.DistractionOutline
    )
    VisionTab.REPORT -> TabColorPalette(
        isDark = false,
        primary = VisionColors.ReportPrimary,
        secondary = VisionColors.ReportSecondary,
        onPrimary = Color.White,
        background = VisionColors.ReportBackground,
        surface = VisionColors.ReportSurface,
        surfaceElevated = VisionColors.ReportSurfaceElevated,
        onSurface = VisionColors.ReportOnSurface,
        muted = VisionColors.ReportMuted,
        outline = VisionColors.ReportOutline
    )
}

@Composable
fun VisionTheme(
    activeTab: VisionTab = VisionTab.DASHBOARD,
    content: @Composable () -> Unit
) {
    val p = paletteForTab(activeTab)
    val colorScheme = if (p.isDark) {
        darkColorScheme(
            primary = p.primary,
            onPrimary = p.onPrimary,
            secondary = p.secondary,
            background = p.background,
            onBackground = p.onSurface,
            surface = p.surface,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceElevated,
            onSurfaceVariant = p.muted,
            outline = p.outline
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = p.onPrimary,
            secondary = p.secondary,
            background = p.background,
            onBackground = p.onSurface,
            surface = p.surface,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceElevated,
            onSurfaceVariant = p.muted,
            outline = p.outline
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
