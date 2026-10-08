package com.frameender.protobooru.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.frameender.protobooru.data.Graph

/** The phone's own biometric prompt (fingerprint or face, with its PIN/pattern as a fallback). */
object Biometric {
    private val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Whether the phone can do it: a fingerprint/face enrolled, or at least a screen lock. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** A short reason why it isn't available, for the settings page. */
    fun unavailableReason(context: Context): String =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Set up a screen lock or fingerprint in Android's settings first"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "This phone has no usable biometric sensor"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Android needs a security update before biometrics can be used"
            else -> "Not available on this phone"
        }

    /** Whether the phone has a screen lock (needed for "Forgot passcode?"). */
    fun deviceSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    /**
     * Shows the prompt. [onResult] gets true when the user is confirmed; false when they
     * cancelled or it failed (the prompt itself handles retries).
     */
    fun prompt(context: Context, title: String, subtitle: String? = null, onResult: (Boolean) -> Unit) {
        val activity = context.findFragmentActivity() ?: run {
            onResult(false)
            return
        }
        // Android 11+ shows the prompt (PIN fallback included) as an overlay: the app never
        // leaves the screen, so no pass is needed, and granting one would let the next real
        // trip to the home screen skip the lock. Android 10 opens a separate screen for the
        // PIN/pattern fallback, so there the trip is allowed, and the pass is cancelled as
        // soon as the prompt reports back.
        if (Build.VERSION.SDK_INT < 30) Graph.lock.allowLeave()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Graph.lock.endAllowedLeave()
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Graph.lock.endAllowedLeave()
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
