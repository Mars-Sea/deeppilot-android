package dev.hailab.deeppilot.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.bridge.BridgeException
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.bridge.FailureReason
import dev.hailab.deeppilot.core.bridge.NotConnectedException
import dev.hailab.deeppilot.core.pairing.PairingError
import dev.hailab.deeppilot.core.pairing.PairingException
import dev.hailab.deeppilot.core.pairing.PairingFailure
import dev.hailab.deeppilot.core.protocol.WireErrorCode
import dev.hailab.deeppilot.core.trust.TrustRejection
import kotlinx.coroutines.TimeoutCancellationException

/** User-facing text for connection states and errors. */

@Composable
fun connectionText(state: ConnectionState): String = when (state) {
    is ConnectionState.Authenticated -> stringResource(R.string.connection_connected)
    is ConnectionState.Connecting -> stringResource(R.string.connection_connecting)
    ConnectionState.Disconnected -> stringResource(R.string.connection_disconnected)
    is ConnectionState.Failed -> failureText(state.reason)
}

@Composable
fun failureText(reason: FailureReason): String = when (reason) {
    FailureReason.DeviceRevoked -> stringResource(R.string.failure_revoked)
    is FailureReason.AuthRejected -> stringResource(R.string.failure_auth)
    FailureReason.AudienceMismatch -> stringResource(R.string.failure_audience)
    is FailureReason.Untrusted -> LocalContext.current.trustMessage(reason.rejection)
    FailureReason.InvalidHost -> stringResource(R.string.failure_host)
    is FailureReason.SigningFailed -> stringResource(R.string.failure_signing)
}

fun Context.trustMessage(rejection: TrustRejection): String = when (rejection) {
    is TrustRejection.Mismatch -> getString(R.string.failure_fingerprint)
    TrustRejection.UnsupportedKey -> getString(R.string.failure_cert_key)
    TrustRejection.EmptyChain -> getString(R.string.failure_fingerprint)
}

fun Context.errorMessage(error: Throwable): String = when (error) {
    is PairingException -> when (error.reason) {
        PairingError.EXPIRED -> getString(R.string.pair_error_expired)
        PairingError.UNSUPPORTED_VERSION -> getString(R.string.pair_error_version)
        PairingError.INVALID_HOST -> getString(R.string.failure_host)
        else -> getString(R.string.pair_error_invalid)
    }
    is PairingFailure.CodeRejected -> getString(R.string.pair_error_code)
    is PairingFailure.RateLimited -> getString(R.string.pair_error_rate)
    is PairingFailure.RegistryFull -> getString(R.string.pair_error_full)
    is PairingFailure.AudienceMismatch, is PairingFailure.FingerprintMismatch -> getString(R.string.failure_audience)
    is PairingFailure.Untrusted -> trustMessage(error.rejection)
    is PairingFailure.Network -> getString(R.string.pair_error_network)
    is PairingFailure -> getString(R.string.pair_error_generic)
    is NotConnectedException -> getString(R.string.error_not_connected)
    is TimeoutCancellationException -> getString(R.string.error_timeout)
    is BridgeException -> when (error.code) {
        WireErrorCode.E_BUSY -> getString(R.string.error_busy)
        WireErrorCode.E_FORBIDDEN -> getString(R.string.error_forbidden)
        WireErrorCode.E_NOT_FOUND -> getString(R.string.error_not_found)
        else -> error.message ?: getString(R.string.error_generic)
    }
    else -> error.message ?: getString(R.string.error_generic)
}

@Composable
fun errorText(error: Throwable): String = LocalContext.current.errorMessage(error)
