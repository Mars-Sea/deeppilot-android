package dev.hailab.deeppilot.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppearanceOption { System, Light, Dark }

/**
 * Text size, as the iOS Dynamic Type choices: body text is 15, 16, 17 or 19pt.
 * [scale] is relative to the default 17pt; System follows the phone's setting.
 */
enum class FontSizeOption(val scale: Float?) {
    System(null),
    Small(15f / 17f),
    Medium(16f / 17f),
    Large(1f),
    XLarge(19f / 17f),
}

enum class AppLanguage(val tag: String?) {
    System(null),
    Chinese("zh-CN"),
    English("en");

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)
}

/** Appearance, text size and language chosen in Settings (iOS `@AppStorage` keys). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(read(KEY_APPEARANCE, AppearanceOption.System))
    val appearance: StateFlow<AppearanceOption> = _appearance.asStateFlow()

    private val _fontSize = MutableStateFlow(read(KEY_FONT_SIZE, FontSizeOption.System))
    val fontSize: StateFlow<FontSizeOption> = _fontSize.asStateFlow()

    private val _language = MutableStateFlow(read(KEY_LANGUAGE, AppLanguage.System))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setAppearance(value: AppearanceOption) {
        prefs.edit { putString(KEY_APPEARANCE, value.name) }
        _appearance.value = value
    }

    fun setFontSize(value: FontSizeOption) {
        prefs.edit { putString(KEY_FONT_SIZE, value.name) }
        _fontSize.value = value
    }

    fun setLanguage(value: AppLanguage) {
        // Committed synchronously: the activity is recreated right after and reads it back.
        prefs.edit(commit = true) { putString(KEY_LANGUAGE, value.name) }
        _language.value = value
    }

    private inline fun <reified T : Enum<T>> read(key: String, default: T): T =
        prefs.getString(key, null)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    companion object {
        private const val FILE = "deeppilot.preferences"
        private const val KEY_APPEARANCE = "appearance.mode"
        private const val KEY_FONT_SIZE = "font.size"
        private const val KEY_LANGUAGE = "app.language"

        /** The stored language, readable before the app graph exists (Activity.attachBaseContext). */
        fun storedLanguage(context: Context): AppLanguage {
            val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
            return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.System
        }
    }
}
