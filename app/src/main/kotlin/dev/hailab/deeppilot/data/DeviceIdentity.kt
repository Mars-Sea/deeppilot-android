package dev.hailab.deeppilot.data

import android.content.Context
import android.os.Build
import android.provider.Settings

/** The device name the host shows for this phone. */
class DeviceIdentity(private val context: Context) {
    val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
}
