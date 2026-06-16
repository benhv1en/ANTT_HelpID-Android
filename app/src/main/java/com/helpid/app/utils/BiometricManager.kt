package com.helpid.app.utils

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.helpid.app.R
import java.util.concurrent.Executor

object BiometricUtils {

    fun getAvailability(
        context: Context,
        allowDeviceCredential: Boolean = true
    ): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return availabilityFromStatus(
            biometricManager.canAuthenticate(allowedAuthenticators(allowDeviceCredential))
        )
    }

    fun isBiometricAvailable(context: Context): Boolean =
        getAvailability(context) == BiometricAvailability.Available

    fun showBiometricPrompt(
        activity: FragmentActivity,
        executor: Executor,
        onSuccess: () -> Unit,
        onError: (BiometricPromptFailure) -> Unit,
        allowDeviceCredential: Boolean = true
    ) {
        val authenticators = allowedAuthenticators(allowDeviceCredential)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(BiometricPromptFailure(errorFromCode(errorCode)))
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(BiometricPromptFailure(BiometricPromptError.AuthenticationFailed))
                }
            }
        )

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_access_title))
            .setSubtitle(activity.getString(R.string.biometric_access_subtitle))
            .setAllowedAuthenticators(authenticators)

        if (!usesDeviceCredential(authenticators)) {
            promptBuilder.setNegativeButtonText(activity.getString(R.string.biometric_cancel))
        }

        biometricPrompt.authenticate(promptBuilder.build())
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        executor: Executor,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        showBiometricPrompt(
            activity = activity,
            executor = executor,
            onSuccess = onSuccess,
            onError = { failure -> onError(activity.getString(failure.messageResId)) },
            allowDeviceCredential = true
        )
    }

    internal fun allowedAuthenticators(allowDeviceCredential: Boolean): Int {
        val biometricStrong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return if (allowDeviceCredential && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricStrong or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            biometricStrong
        }
    }

    internal fun availabilityFromStatus(status: Int): BiometricAvailability =
        when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SecurityUpdateRequired
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.Unsupported
            else -> BiometricAvailability.Unknown
        }

    internal fun errorFromCode(errorCode: Int): BiometricPromptError =
        when (errorCode) {
            BiometricPrompt.ERROR_LOCKOUT -> BiometricPromptError.Lockout
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricPromptError.LockoutPermanent
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricPromptError.Canceled
            BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricPromptError.NoneEnrolled
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> BiometricPromptError.NoHardware
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricPromptError.HardwareUnavailable
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricPromptError.NoDeviceCredential
            else -> BiometricPromptError.Unknown
        }

    private fun usesDeviceCredential(authenticators: Int): Boolean =
        authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0
}

enum class BiometricAvailability {
    Available,
    NoneEnrolled,
    NoHardware,
    HardwareUnavailable,
    SecurityUpdateRequired,
    Unsupported,
    Unknown
}

data class BiometricPromptFailure(val error: BiometricPromptError) {
    val messageResId: Int
        get() = when (error) {
            BiometricPromptError.AuthenticationFailed -> R.string.biometric_auth_failed
            BiometricPromptError.Lockout,
            BiometricPromptError.LockoutPermanent -> R.string.biometric_locked_out
            BiometricPromptError.NoneEnrolled -> R.string.biometric_not_enrolled
            BiometricPromptError.NoHardware,
            BiometricPromptError.HardwareUnavailable,
            BiometricPromptError.NoDeviceCredential -> R.string.biometric_not_available
            BiometricPromptError.Canceled -> R.string.biometric_canceled
            BiometricPromptError.Unknown -> R.string.biometric_system_error
        }
}

enum class BiometricPromptError {
    AuthenticationFailed,
    Canceled,
    Lockout,
    LockoutPermanent,
    NoneEnrolled,
    NoHardware,
    HardwareUnavailable,
    NoDeviceCredential,
    Unknown
}
