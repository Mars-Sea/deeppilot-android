package dev.hailab.deeppilot.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.LocaleList
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.data.AppPreferences
import androidx.core.content.ContextCompat
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.graph
import dev.hailab.deeppilot.service.ConnectionService

class MainActivity : ComponentActivity() {
    /** A pairing link from a deeppilot://pair intent, waiting for the user to confirm. */
    private val pairLink = mutableStateOf<String?>(null)

    /** A session to open, from a notification tap. */
    private val sessionToOpen = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** The in-app language choice overrides the system locale for this activity's resources. */
    override fun attachBaseContext(newBase: Context) {
        val locale = AppPreferences.storedLanguage(newBase).locale
        if (locale == null) {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration).apply { setLocales(LocaleList(locale)) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val preferences = graph.preferences
        setContent {
            val appearance by preferences.appearance.collectAsStateWithLifecycle()
            val fontSize by preferences.fontSize.collectAsStateWithLifecycle()
            val language by preferences.language.collectAsStateWithLifecycle()
            val dark = appearance.isDark()
            // Status and navigation bar icons follow the app's appearance, not only the system's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Strings come from the activity's resources, so a new language needs a fresh activity.
            val applied = remember { language }
            LaunchedEffect(language) { if (language != applied) recreate() }
            DeepPilotTheme(appearance = appearance, fontSize = fontSize) {
                AppRoot(
                    repository = graph.repository,
                    pairLink = pairLink,
                    sessionToOpen = sessionToOpen,
                    onPaired = ::onPaired,
                )
            }
        }
        if (graph.store.loadHost() != null) onPaired()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data: Uri? = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null && PairingLink.isPairingUri(data.toString())) {
            pairLink.value = data.toString()
        }
        intent.getStringExtra(EXTRA_SESSION_ID)?.let { sessionToOpen.value = it }
    }

    /** Once a host exists: ask for notifications and start "keep connected". */
    private fun onPaired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (graph.store.keepConnected) ConnectionService.start(this)
    }

    companion object {
        const val EXTRA_SESSION_ID = "dev.hailab.deeppilot.SESSION_ID"
    }
}
