package dev.hailab.deeppilot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.palette

/** Click without the Material ripple, like an iOS `.plain` button. */
@Composable
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )

/** iOS `SettingsCard`: grouped rows on the surface color, radius 12. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(palette.surface),
        content = content,
    )
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .padding(start = 18.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(palette.hairline),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.plainClickable(onClick = onClick) else Modifier)
            .heightIn(min = AppLayout.settingsRowHeight)
            .padding(horizontal = AppSpacing.regular, vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = AppType.body, color = palette.foreground)
            if (subtitle != null) Text(subtitle, style = AppType.footnote, color = palette.secondary)
        }
        trailing()
    }
}

/** The app icon: the launcher glyph on the brand color with iOS corner rounding (22.5%). */
@Composable
fun AppIcon(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.225f))
            .background(colorResource(R.color.launcher_background)),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size))
    }
}

/** 8pt connection dot: green, orange pulsing, hollow ring, or red with a halo, as on iOS. */
@Composable
fun ConnectionStatusLight(state: ConnectionState, backdrop: Color = palette.background) {
    val p = palette
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.size(14.dp).background(backdrop, CircleShape), contentAlignment = Alignment.Center) {
        when (state) {
            is ConnectionState.Authenticated -> Box(Modifier.size(8.dp).background(p.success, CircleShape))
            is ConnectionState.Connecting -> Box(Modifier.size(8.dp).alpha(pulse).background(p.warning, CircleShape))
            ConnectionState.Disconnected -> Box(Modifier.size(8.dp).border(1.5.dp, p.secondary, CircleShape))
            is ConnectionState.Failed -> {
                Box(Modifier.size(12.dp).border(1.5.dp, p.danger.copy(alpha = 0.42f), CircleShape))
                Box(Modifier.size(8.dp).background(p.danger, CircleShape))
            }
        }
    }
}

/** Short connection words used in the drawer header and settings ("已连接", "连接中", …). */
@Composable
fun phaseText(state: ConnectionState): String = when (state) {
    is ConnectionState.Authenticated -> stringResource(R.string.phase_connected)
    is ConnectionState.Connecting -> stringResource(R.string.phase_connecting)
    ConnectionState.Disconnected -> stringResource(R.string.phase_disconnected)
    is ConnectionState.Failed -> stringResource(R.string.phase_failed)
}

@Composable
fun SmallSpinner(color: Color = palette.secondary, size: Dp = 14.dp) {
    CircularProgressIndicator(modifier = Modifier.size(size), color = color, strokeWidth = 1.8.dp)
}

/** iOS `.borderedProminent` + `brandProminent()`: brand blue fill, white label. */
@Composable
fun ProminentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    radius: Dp = AppRadius.large,
    height: Dp = 50.dp,
    icon: ImageVector? = null,
) {
    val p = palette
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) p.accent else p.foreground.copy(alpha = 0.08f))
            .plainClickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = AppSpacing.regular),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            SmallSpinner(color = Color.White, size = 18.dp)
        } else {
            val color = if (enabled) Color.White else p.tertiary
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = AppType.body.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
    }
}

/** iOS `.bordered` with a secondary tint: a soft gray fill. */
@Composable
fun BorderedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(AppRadius.large))
            .background(p.secondaryFill)
            .plainClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) p.secondary else p.tertiary,
        )
    }
}

/** A 44×44 plain icon button (iOS toolbar item). */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = palette.foreground,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else palette.tertiary, modifier = Modifier.size(iconSize))
    }
}

/** iOS inline navigation bar: leading items, a centered principal view, trailing items. */
@Composable
fun NavBar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    principal: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 6.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, content = leading)
        Box(Modifier.align(Alignment.Center).padding(horizontal = 96.dp), contentAlignment = Alignment.Center, content = principal)
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    BarIconButton(Icons.AutoMirrored.Filled.ArrowBackIos, stringResource(R.string.action_back), onClick, iconSize = 20.dp)
}

@Composable
fun NavTitle(text: String) {
    Text(text, style = AppType.headline, color = palette.foreground, maxLines = 1)
}

/** Text that inherits a secondary color, for compact labels with an icon. */
@Composable
fun IconLabel(
    icon: ImageVector,
    text: String,
    style: TextStyle,
    color: Color,
    iconColor: Color = color,
    iconSize: Dp = 12.dp,
    spacing: Dp = 4.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSize))
        CompositionLocalProvider(LocalContentColor provides color) { Text(text, style = style, color = color, maxLines = 1) }
    }
}

/** Content padding helper matching iOS page insets. */
val PageInsets = PaddingValues(horizontal = AppLayout.pageInset)
