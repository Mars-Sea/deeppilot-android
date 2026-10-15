package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.ui.AppIconChoice
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.AppIcon
import dev.hailab.deeppilot.ui.components.BackButton
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.NavTitle
import dev.hailab.deeppilot.ui.components.SettingsCard
import dev.hailab.deeppilot.ui.components.SettingsDivider
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette

/** iOS `IconSettingsView`: pick the home-screen icon. */
@Composable
fun AppIconScreen(onBack: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    var active by remember { mutableStateOf(AppIconChoice.current(context)) }

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(leading = { BackButton(onBack) }, principal = { NavTitle(stringResource(R.string.settings_icon_title)) })
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = AppLayout.pageInset)
                .padding(top = AppSpacing.small),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsCard {
                AppIconChoice.entries.forEachIndexed { index, option ->
                    if (index > 0) SettingsDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .plainClickable {
                                if (option != active) {
                                    AppIconChoice.apply(context, option)
                                    active = option
                                }
                            }
                            .padding(horizontal = AppSpacing.regular, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(size = 56.dp, choice = option)
                        Text(stringResource(option.title), style = AppType.body, color = p.foreground, modifier = Modifier.weight(1f))
                        if (option == active) {
                            Icon(Icons.Filled.Check, null, tint = p.foreground, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.settings_icon_explainer),
                style = AppType.footnote,
                color = p.secondary,
                modifier = Modifier.padding(horizontal = AppSpacing.regular),
            )
        }
    }
}
