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
    /** A screen was just recreated (rotation etc.): its replacement starting isn't a return. */
    private var recreating = false

    /** True while any of the app's screens is on screen (used to pause background checks). */
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground
    private var leftAt = 0L
    /** Extra time allowed for the current trip outside, when it was one we started (a picker). */
    private var awayGrace = 0L
    /** When [allowLeave] was called (elapsed ms), or 0. Only counts for a short while. */
    private var passAt = 0L
    private val resumed = mutableListOf<Activity>()

    /** Counts returns to the app, so the lock screen can ask again (Android cancels the prompt on leaving). */
    private val _returns = MutableStateFlow(0)
    val returns: StateFlow<Int> = _returns

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Call right before opening something outside the app that the user will come straight
     * back from (photo or file picker, permission screen, the installer), so a quick return
     * from it doesn't ask to unlock again.
     *
     * The pass is narrow on purpose:
     *  - it only covers leaving within [PASS_VALID_MS] of being granted, and is used up by
     *    that one trip (or cancelled as soon as the app is back in front);
     *  - even then the trip only gets [ALLOWED_AWAY_MS] of grace. Staying away longer (e.g.
     *    going home from the picker and forgetting about it) still locks the app.
     */
    fun allowLeave() {
        passAt = SystemClock.elapsedRealtime()
    }

    /** Cancels a pass that wasn't used (e.g. the fingerprint prompt closed without leaving). */
    fun endAllowedLeave() {
        passAt = 0L
    }

    private fun passActive(now: Long) = passAt != 0L && now - passAt < PASS_VALID_MS

    override fun onActivityStarted(activity: Activity) {
        val wasAway = started.isEmpty() && !recreating
        recreating = false
        started += System.identityHashCode(activity)
        _foreground.value = true
        if (wasAway) {
            passAt = 0L
            _returns.value++
            if (leftAt != 0L) {
                val away = SystemClock.elapsedRealtime() - leftAt
                val allowed = maxOf(_lockAfter.value, awayGrace)
                leftAt = 0L
                awayGrace = 0L
                if (enabled && away >= allowed) _locked.value = true
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        started -= System.identityHashCode(activity)
        // Rotation and other configuration changes recreate the screen; that isn't leaving.
        // (The old screen is still taken off the list, or it would count as "open" forever
        // and the app would never notice being left again.)
        if (activity.isChangingConfigurations) {
            recreating = true
            return
        }
        if (started.isEmpty()) {
            _foreground.value = false
            // Every trip outside is timed, allowed or not.
            val now = SystemClock.elapsedRealtime()
            leftAt = now
            awayGrace = if (passActive(now)) ALLOWED_AWAY_MS else 0L
            passAt = 0L
            // "Immediately": engage the lock now, while the app is out of view, so it's
            // already up the moment the app comes back (no glimpse of what was open).
            if (enabled && _lockAfter.value == 0L && awayGrace == 0L) _locked.value = true
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = applyWindowFlags(activity)
    override fun onActivityResumed(activity: Activity) {
        resumed += activity
        applyWindowFlags(activity)
    }
    override fun onActivityPaused(activity: Activity) {
        resumed -= activity
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        resumed -= activity
        if (!activity.isChangingConfigurations) started -= System.identityHashCode(activity)
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
        /** How long a granted pass stays usable before the trip has to start. */
        private const val PASS_VALID_MS = 60_000L
        /** Longest an allowed trip (picker, installer) can last without locking. */
        private const val ALLOWED_AWAY_MS = 120_000L
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
