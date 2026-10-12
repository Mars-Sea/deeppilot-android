package dev.hailab.deeppilot.bridge

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether any screen is visible, and which session the user is reading.
 * Notifications for the session on screen are dropped (the user already sees it).
 */
class AppVisibility {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _viewingSession = MutableStateFlow<String?>(null)
    val viewingSession: StateFlow<String?> = _viewingSession.asStateFlow()

    /** Must run on the main thread (Application.onCreate). */
    fun install(onForeground: () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _foreground.value = true
                onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                _foreground.value = false
            }
        })
    }

    fun setViewingSession(sessionId: String?) {
        _viewingSession.value = sessionId
    }

    fun isViewing(sessionId: String): Boolean = _foreground.value && _viewingSession.value == sessionId
}
