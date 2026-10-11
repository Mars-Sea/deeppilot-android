package dev.hailab.deeppilot.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            DeepPilotTheme {
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
