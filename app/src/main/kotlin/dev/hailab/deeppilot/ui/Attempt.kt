package dev.hailab.deeppilot.ui

import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

/** Runs [block]; returns the failure to show, or null on success. Real cancellation still propagates. */
suspend fun attempt(block: suspend () -> Unit): Throwable? = try {
    block()
    null
} catch (e: TimeoutCancellationException) {
    e
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e
}
