package com.helpid.app.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.helpid.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricUtilsTest {

    @Test
    fun `availabilityFromStatus maps known biometric statuses`() {
        assertEquals(
            BiometricAvailability.Available,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_SUCCESS)
        )
        assertEquals(
            BiometricAvailability.NoneEnrolled,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        )
        assertEquals(
            BiometricAvailability.NoHardware,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
        )
        assertEquals(
            BiometricAvailability.HardwareUnavailable,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
        )
    }

    @Test
    fun `availabilityFromStatus maps SecurityUpdateRequired and Unsupported`() {
        assertEquals(
            BiometricAvailability.SecurityUpdateRequired,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED)
        )
        assertEquals(
            BiometricAvailability.Unsupported,
            BiometricUtils.availabilityFromStatus(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)
        )
    }

    @Test
    fun `availabilityFromStatus maps unknown status safely`() {
        assertEquals(
            BiometricAvailability.Unknown,
            BiometricUtils.availabilityFromStatus(Int.MIN_VALUE)
        )
    }

    @Test
    fun `errorFromCode maps lockout cancel and hardware failures`() {
        assertEquals(
            BiometricPromptError.Lockout,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_LOCKOUT)
        )
        assertEquals(
            BiometricPromptError.LockoutPermanent,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
        )
        assertEquals(
            BiometricPromptError.Canceled,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_USER_CANCELED)
        )
        assertEquals(
            BiometricPromptError.NoneEnrolled,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_NO_BIOMETRICS)
        )
        assertEquals(
            BiometricPromptError.NoHardware,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_HW_NOT_PRESENT)
        )
    }

    @Test
    fun `errorFromCode maps all Canceled aliases`() {
        assertEquals(
            BiometricPromptError.Canceled,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_CANCELED)
        )
        assertEquals(
            BiometricPromptError.Canceled,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_NEGATIVE_BUTTON)
        )
    }

    @Test
    fun `errorFromCode maps hardware unavailable and no device credential`() {
        assertEquals(
            BiometricPromptError.HardwareUnavailable,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_HW_UNAVAILABLE)
        )
        assertEquals(
            BiometricPromptError.NoDeviceCredential,
            BiometricUtils.errorFromCode(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL)
        )
    }

    @Test
    fun `errorFromCode maps unknown code safely`() {
        assertEquals(
            BiometricPromptError.Unknown,
            BiometricUtils.errorFromCode(Int.MIN_VALUE)
        )
    }

    @Test
    fun `prompt failure maps to local string resources`() {
        assertEquals(
            R.string.biometric_auth_failed,
            BiometricPromptFailure(BiometricPromptError.AuthenticationFailed).messageResId
        )
        assertEquals(
            R.string.biometric_locked_out,
            BiometricPromptFailure(BiometricPromptError.Lockout).messageResId
        )
        assertEquals(
            R.string.biometric_locked_out,
            BiometricPromptFailure(BiometricPromptError.LockoutPermanent).messageResId
        )
        assertEquals(
            R.string.biometric_not_enrolled,
            BiometricPromptFailure(BiometricPromptError.NoneEnrolled).messageResId
        )
        assertEquals(
            R.string.biometric_not_available,
            BiometricPromptFailure(BiometricPromptError.NoHardware).messageResId
        )
        assertEquals(
            R.string.biometric_not_available,
            BiometricPromptFailure(BiometricPromptError.HardwareUnavailable).messageResId
        )
        assertEquals(
            R.string.biometric_not_available,
            BiometricPromptFailure(BiometricPromptError.NoDeviceCredential).messageResId
        )
        assertEquals(
            R.string.biometric_canceled,
            BiometricPromptFailure(BiometricPromptError.Canceled).messageResId
        )
        assertEquals(
            R.string.biometric_system_error,
            BiometricPromptFailure(BiometricPromptError.Unknown).messageResId
        )
    }
}
