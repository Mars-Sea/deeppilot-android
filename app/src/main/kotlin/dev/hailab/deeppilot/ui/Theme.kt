package dev.hailab.deeppilot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.hailab.deeppilot.data.AppearanceOption
import dev.hailab.deeppilot.data.FontSizeOption
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens copied from the iOS app (`DesignSystem.swift`), which is the
 * reference for every screen. System colors use the iOS values.
 */
@Immutable
data class AppPalette(
    val background: Color,
    val surface: Color,
    val foreground: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    /** iOS `.secondary` label. */
    val secondary: Color,
    /** iOS `.tertiary` label. */
    val tertiary: Color,
    /** iOS `secondarySystemFill`. */
    val secondaryFill: Color,
    /** iOS `tertiarySystemFill`. */
    val tertiaryFill: Color,
    val dark: Boolean,
) {
    /** Fixed near-black in both appearances, as on iOS. */
    val prominentFill: Color get() = Color(0xFF111111)
    val softAccent: Color get() = accent.copy(alpha = 0.10f)
    val mutedSurface: Color get() = foreground.copy(alpha = 0.05f)
    val hairline: Color get() = border.copy(alpha = 0.72f)

    /** iOS `systemGroupedBackground`. */
    val groupedBackground: Color get() = if (dark) Color.Black else Color(0xFFF2F2F7)

    companion object {
        val Light = AppPalette(
            background = Color.White,
            surface = Color(0xFFF2F2F7),
            foreground = Color(0xFF111111),
            muted = Color(0xFF6B7280),
            border = Color(0xFFD9DEE7),
            accent = Color(0xFF4D6BFE),
            danger = Color(0xFFFF3B30),
            warning = Color(0xFFFF9500),
            success = Color(0xFF34C759),
            secondary = Color(0x993C3C43),
            tertiary = Color(0x4D3C3C43),
            secondaryFill = Color(0x29787880),
            tertiaryFill = Color(0x1F767680),
            dark = false,
        )
        val Dark = AppPalette(
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            foreground = Color(0xFFF5F5F5),
            muted = Color(0xFFA7ADB8),
            border = Color(0xFF3A3A3C),
            accent = Color(0xFF4D6BFE),
            danger = Color(0xFFFF453A),
            warning = Color(0xFFFF9F0A),
            success = Color(0xFF30D158),
            secondary = Color(0x99EBEBF5),
            tertiary = Color(0x4DEBEBF5),
            secondaryFill = Color(0x52787880),
            tertiaryFill = Color(0x3D767680),
            dark = true,
        )
    }
}

/** iOS Dynamic Type sizes at the default content size. */
object AppType {
    private val base = TextStyle(fontFamily = FontFamily.Default, letterSpacing = 0.sp)
    val title = base.copy(fontSize = 28.sp, lineHeight = 34.sp)
    val title2 = base.copy(fontSize = 22.sp, lineHeight = 28.sp)
    val title3 = base.copy(fontSize = 20.sp, lineHeight = 25.sp)
    val headline = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = base.copy(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = base.copy(fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = base.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = base.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = base.copy(fontSize = 11.sp, lineHeight = 13.sp)
}

object AppSpacing {
    val micro = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val regular = 16.dp
    val comfortable = 20.dp
    val section = 24.dp
    val large = 32.dp
    val hero = 48.dp
}

object AppRadius {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val panel = 24.dp
}

object AppLayout {
    val pageInset = 16.dp
    val settingsRowHeight = 52.dp
    val largeControlHeight = 52.dp
}

val LocalPalette = staticCompositionLocalOf { AppPalette.Light }

/** Shorthand for the current palette inside composables. */
val palette: AppPalette
    @Composable get() = LocalPalette.current

/** Whether the app draws dark, after the user's appearance choice. */
@Composable
fun AppearanceOption.isDark(): Boolean = when (this) {
    AppearanceOption.System -> isSystemInDarkTheme()
    AppearanceOption.Light -> false
    AppearanceOption.Dark -> true
}

@Composable
fun DeepPilotTheme(
    appearance: AppearanceOption = AppearanceOption.System,
    fontSize: FontSizeOption = FontSizeOption.System,
    content: @Composable () -> Unit,
) {
    val colors = if (appearance.isDark()) AppPalette.Dark else AppPalette.Light
    // A fixed text size pins the font scale like iOS pins a Dynamic Type size;
    // every sp (and the WebView text zoom) follows it.
    val density = LocalDensity.current
    val scaled = fontSize.scale?.let { Density(density.density, it) } ?: density
    val scheme = if (colors.dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.background,
            onSurface = colors.foreground,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerLow = colors.background,
            outline = colors.border,
            outlineVariant = colors.hairline,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.foreground,
            surface = colors.background,
            onSurface = colors.foreground,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerLow = colors.background,
            outline = colors.border,
            outlineVariant = colors.hairline,
            error = colors.danger,
        )
    }
    val typography = Typography(
        bodyLarge = AppType.body,
        bodyMedium = AppType.subheadline,
        bodySmall = AppType.footnote,
        titleLarge = AppType.title3,
        titleMedium = AppType.headline,
        titleSmall = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = AppType.body.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = AppType.caption,
        labelSmall = AppType.caption2,
    )
    CompositionLocalProvider(LocalPalette provides colors, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
