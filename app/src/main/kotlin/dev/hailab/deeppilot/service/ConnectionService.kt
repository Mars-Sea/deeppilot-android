package dev.hailab.deeppilot.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.graph
import dev.hailab.deeppilot.notify.Notifier
import kotlinx.coroutines.launch

/**
 * "Keep connected": a foreground service that holds the process, and with it
 * the bridge WebSocket, while the app is in the background. This is how
 * notifications reach phones without a vendor push channel (phase 1).
 *
 * Type remoteMessaging rather than dataSync: dataSync is capped at 6 h a day
 * from Android 15.
 */
class ConnectionService : LifecycleService() {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground(describe(graph.client.state.value))) return
        lifecycleScope.launch {
            graph.client.state.collect { state ->
                if (graph.store.loadHost() == null) {
                    stopSelf()
                } else {
                    runCatching {
                        getSystemService(android.app.NotificationManager::class.java)
                            .notify(Notifier.CONNECTION_NOTIFICATION_ID, graph.notifier.connectionNotification(describe(state)))
                    }
                }
            }
        }
        // A new network usually means the old socket is dead; reconnect without waiting for backoff.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                graph.client.poke()
            }
        }
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!graph.store.keepConnected || graph.store.loadHost() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        graph.client.poke()
        return START_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        super.onDestroy()
    }

    /** False when the system refused (e.g. a sticky restart from the background on Android 12+). */
    private fun enterForeground(text: String): Boolean {
        val notification = graph.notifier.connectionNotification(text)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Notifier.CONNECTION_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(Notifier.CONNECTION_NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            stopSelf()
            false
        }
    }

    private fun describe(state: ConnectionState): String = getString(
        when (state) {
            is ConnectionState.Authenticated -> R.string.connection_connected
            is ConnectionState.Connecting -> R.string.connection_connecting
            is ConnectionState.Failed -> R.string.connection_failed
            ConnectionState.Disconnected -> R.string.connection_disconnected
        },
    )

    companion object {
        /** Call from a visible Activity; background starts are refused on Android 12+. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
