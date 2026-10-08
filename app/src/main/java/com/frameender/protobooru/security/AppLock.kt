package com.frameender.protobooru.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Privacy & security:
 *  - App lock: a passcode or the phone's biometrics, asked whenever the app is opened or
 *    comes back after leaving it (home, app switcher, another app, screen off).
 *  - Hide the preview in recent apps.
 *  - Block screenshots and screen recordings.
 *
 * Settings live in their own SharedPreferences (not the main settings) so they're read
 * synchronously at startup, before anything is drawn.
 */
class AppLock(private val app: Application) : Application.ActivityLifecycleCallbacks {

    enum class Method(val key: String) {
        OFF("off"), PASSCODE("pin"), BIOMETRIC("bio");

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key } ?: OFF
        }
    }

    private val prefs = app.getSharedPreferences("security", Context.MODE_PRIVATE)

    private val _method = MutableStateFlow(Method.of(prefs.getString(K_METHOD, null)))
    val method: StateFlow<Method> = _method

    /** How long the app may be away before it locks again (ms). 0 = immediately. */
    private val _lockAfter = MutableStateFlow(prefs.getLong(K_AFTER, 0L))
    val lockAfter: StateFlow<Long> = _lockAfter

    private val _hideRecents = MutableStateFlow(prefs.getBoolean(K_HIDE_RECENTS, true))
    /** Blank card in the recent-apps screen instead of a snapshot of what was open. */
    val hideRecents: StateFlow<Boolean> = _hideRecents

    private val _blockScreenshots = MutableStateFlow(prefs.getBoolean(K_SECURE, false))
    val blockScreenshots: StateFlow<Boolean> = _blockScreenshots

    /** Locked at launch whenever a lock is set up. */
    private val _locked = MutableStateFlow(_method.value != Method.OFF)
    val locked: StateFlow<Boolean> = _locked

    val enabled: Boolean get() = _method.value != Method.OFF

    // ---------------------------------------------------------------- leaving / returning

    private val started = mutableSetOf<Int>()
    private var leftAt = 0L
    private var allowLeaveOnce = false
    private val resumed = mutableListOf<Activity>()

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Call right before opening something outside the app that the user will come straight
     * back from (photo picker, file picker, permission prompt, the fingerprint prompt), so
     * returning from it doesn't ask to unlock again.
     */
    fun allowLeave() {
        allowLeaveOnce = true
    }

    override fun onActivityStarted(activity: Activity) {
        val wasAway = started.isEmpty()
        started += System.identityHashCode(activity)
        if (wasAway && leftAt != 0L) {
            val away = SystemClock.elapsedRealtime() - leftAt
            leftAt = 0L
            if (enabled && away >= _lockAfter.value) _locked.value = true
        }
    }

    override fun onActivityStopped(activity: Activity) {
        // Rotation and other configuration changes recreate the screen; that isn't leaving.
        if (activity.isChangingConfigurations) return
        started -= System.identityHashCode(activity)
        if (started.isEmpty()) {
            if (allowLeaveOnce) {
                allowLeaveOnce = false
            } else {
                leftAt = SystemClock.elapsedRealtime()
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = applyWindowFlags(activity)
    override fun onActivityResumed(activity: Activity) {
        resumed += activity
        applyWindowFlags(activity)
        // Back from an allowed trip outside (picker etc.): the pass is used up.
        allowLeaveOnce = false
    }
    override fun onActivityPaused(activity: Activity) {
        resumed -= activity
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        resumed -= activity
    }

    fun unlock() {
        _locked.value = false
    }

    // ---------------------------------------------------------------- settings

    fun setLockAfter(ms: Long) {
        prefs.edit().putLong(K_AFTER, ms).apply()
        _lockAfter.value = ms
    }

    fun setHideRecents(on: Boolean) {
        prefs.edit().putBoolean(K_HIDE_RECENTS, on).apply()
        _hideRecents.value = on
        resumed.toList().forEach { applyWindowFlags(it) }
    }

    fun setBlockScreenshots(on: Boolean) {
        prefs.edit().putBoolean(K_SECURE, on).apply()
        _blockScreenshots.value = on
        resumed.toList().forEach { applyWindowFlags(it) }
    }

    /** Biometric lock (the phone's fingerprint or face, with its screen lock as a fallback). */
    fun useBiometric() {
        prefs.edit().putString(K_METHOD, Method.BIOMETRIC.key).remove(K_HASH).remove(K_SALT).apply()
        _method.value = Method.BIOMETRIC
        resetFailures()
    }

    fun turnOff() {
        prefs.edit().putString(K_METHOD, Method.OFF.key).remove(K_HASH).remove(K_SALT).apply()
        _method.value = Method.OFF
        _locked.value = false
        resetFailures()
    }

    /**
     * Android 13+ can blank just the recent-apps preview. Older versions can only do that
     * with FLAG_SECURE, which also blocks screenshots, so there it's used for both.
     */
    private fun applyWindowFlags(a: Activity) {
        val secure = _blockScreenshots.value || (_hideRecents.value && Build.VERSION.SDK_INT < 33)
        if (secure) {
            a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            a.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= 33) a.setRecentsScreenshotEnabled(!_hideRecents.value && !_blockScreenshots.value)
    }

    // ---------------------------------------------------------------- passcode

    /** Stores a new passcode (only a salted, slow hash of it) and switches the lock to it. */
    suspend fun setPasscode(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(K_METHOD, Method.PASSCODE.key)
            .putString(K_SALT, b64(salt))
            .putString(K_HASH, b64(hash))
            .apply()
        _method.value = Method.PASSCODE
        resetFailures()
    }

    sealed interface Check {
        data object Ok : Check
        data class Wrong(val triesLeft: Int) : Check
        data class Wait(val untilMillis: Long) : Check
    }

    /** When wrong tries are blocked until (epoch ms), or 0. */
    fun lockoutUntil(): Long = prefs.getLong(K_LOCKOUT, 0L).takeIf { it > System.currentTimeMillis() } ?: 0L

    /**
     * Checks a passcode. Five wrong tries in a row start a wait: 30 seconds, doubling with
     * each further wrong try, up to 15 minutes. The check is deliberately slow (PBKDF2).
     */
    suspend fun checkPasscode(pin: String): Check {
        lockoutUntil().takeIf { it > 0 }?.let { return Check.Wait(it) }
        val salt = prefs.getString(K_SALT, null)?.let { unb64(it) } ?: return Check.Wrong(0)
        val want = prefs.getString(K_HASH, null)?.let { unb64(it) } ?: return Check.Wrong(0)
        val ok = MessageDigest.isEqual(hash(pin, salt), want)
        if (ok) {
            resetFailures()
            return Check.Ok
        }
        val fails = prefs.getInt(K_FAILS, 0) + 1
        prefs.edit().putInt(K_FAILS, fails).apply()
        if (fails >= FREE_TRIES) {
            val step = fails - FREE_TRIES
            val wait = (30_000L shl step.coerceAtMost(5)).coerceAtMost(15 * 60_000L)
            val until = System.currentTimeMillis() + wait
            prefs.edit().putLong(K_LOCKOUT, until).apply()
            return Check.Wait(until)
        }
        return Check.Wrong(FREE_TRIES - fails)
    }

    private fun resetFailures() {
        prefs.edit().remove(K_FAILS).remove(K_LOCKOUT).apply()
    }

    private suspend fun hash(pin: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val MIN_PIN = 4
        const val MAX_PIN = 12
        private const val FREE_TRIES = 5
        private const val ITERATIONS = 120_000

        private const val K_METHOD = "method"
        private const val K_SALT = "pin_salt"
        private const val K_HASH = "pin_hash"
        private const val K_FAILS = "pin_fails"
        private const val K_LOCKOUT = "pin_lockout"
        private const val K_AFTER = "lock_after"
        private const val K_HIDE_RECENTS = "hide_recents"
        private const val K_SECURE = "block_screenshots"
    }
}
