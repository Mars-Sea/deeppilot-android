package dev.hailab.deeppilot.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.hailab.deeppilot.R

/**
 * The home-screen icons (iOS `AppIconChoice`). Each is an `<activity-alias>` in the
 * manifest; switching enables one alias and disables the other.
 */
enum class AppIconChoice(private val alias: String, @StringRes val title: Int, @DrawableRes val glyph: Int) {
    Default(".IconDefault", R.string.settings_icon_default, R.drawable.app_icon_glyph),
    Flow(".IconFlow", R.string.settings_icon_flow, R.drawable.app_icon_glyph_flow),
    ;

    private fun component(context: Context) = ComponentName(context.packageName, context.packageName + alias)

    companion object {
        /** The icon on the home screen now; the default unless Flow was explicitly enabled. */
        fun current(context: Context): AppIconChoice {
            val state = context.packageManager.getComponentEnabledSetting(Flow.component(context))
            return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) Flow else Default
        }

        fun apply(context: Context, choice: AppIconChoice) {
            val pm = context.packageManager
            // Enable the new entry first so the app never has no launcher entry at all.
            entries.sortedBy { it != choice }.forEach { option ->
                pm.setComponentEnabledSetting(
                    option.component(context),
                    if (option == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
