package com.frameender.protobooru.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.security.AppLock
import com.frameender.protobooru.security.Biometric
import com.frameender.protobooru.security.ChoosePasscodeDialog
import com.frameender.protobooru.security.ConfirmPasscodeDialog
import kotlinx.coroutines.launch

/** Settings → Privacy & security: the app lock, the recent-apps preview, screenshots. */
@Composable
fun SecurityPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lock = Graph.lock
    val method by lock.method.collectAsState()
    val lockAfter by lock.lockAfter.collectAsState()
    val hideRecents by lock.hideRecents.collectAsState()
    val blockShots by lock.blockScreenshots.collectAsState()

    /** What to do once the current lock has been confirmed. */
    var confirmFor by remember { mutableStateOf<(() -> Unit)?>(null) }
    var choosePin by remember { mutableStateOf(false) }

    fun enableBiometric() {
        if (!Biometric.available(context)) {
            Graph.toast(Biometric.unavailableReason(context))
            return
        }
        Biometric.prompt(context, "Turn on biometric lock", "Confirm it's you") { ok ->
            if (ok) {
                lock.useBiometric()
                Graph.toast("Biometric lock on")
            }
        }
    }

    fun switchTo(target: AppLock.Method) {
        when (target) {
            AppLock.Method.OFF -> {
                lock.turnOff()
                Graph.toast("App lock off")
            }
            AppLock.Method.PASSCODE -> choosePin = true
            AppLock.Method.BIOMETRIC -> enableBiometric()
        }
    }

    /** Changing or removing a lock first asks for the current one. */
    fun withCurrentLock(then: () -> Unit) {
        when (method) {
            AppLock.Method.OFF -> then()
            AppLock.Method.PASSCODE -> confirmFor = then
            AppLock.Method.BIOMETRIC -> Biometric.prompt(context, "Confirm it's you") { ok -> if (ok) then() }
        }
    }

    SettingsPage("Privacy & security", onBack) {
        GroupLabel("App lock")
        SegmentedSetting(
            listOf(
                Triple(AppLock.Method.OFF, "Off", Icons.Default.LockOpen),
                Triple(AppLock.Method.PASSCODE, "Passcode", Icons.Default.Dialpad),
                Triple(AppLock.Method.BIOMETRIC, "Biometrics", Icons.Default.Fingerprint),
            ),
            selected = method,
        ) { target -> if (target != method) withCurrentLock { switchTo(target) } }
        Hint(
            when (method) {
                AppLock.Method.OFF -> "Ask for a passcode or your fingerprint every time ProtoBooru opens or comes back."
                AppLock.Method.PASSCODE -> "A ${AppLock.MIN_PIN}–${AppLock.MAX_PIN} digit passcode, typed on the app's own number pad. " +
                    "Only a salted hash is kept. After 5 wrong tries there's a wait that grows each time."
                AppLock.Method.BIOMETRIC -> "Android's own prompt: fingerprint or face, with your phone's PIN, pattern or password as a fallback."
            },
        )
        if (method != AppLock.Method.OFF) {
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                ChipsSetting(
                    "Lock after leaving",
                    listOf(0L to "Immediately", 30_000L to "30 s", 60_000L to "1 min", 300_000L to "5 min"),
                    lockAfter,
                    summary = "How long ProtoBooru can be in the background before it asks again",
                ) { lock.setLockAfter(it) }
                if (method == AppLock.Method.PASSCODE) {
                    CardDivider()
                    ActionRow("Change passcode") { withCurrentLock { choosePin = true } }
                }
            }
            Hint(
                "It locks when you go home, switch apps, open another app or turn the screen off. " +
                    "Pulling down notifications, rotating, and the photo or file pickers don't lock it. " +
                    "Back on the lock screen leaves the app.",
            )
        }

        GroupLabel("Screen privacy")
        SettingsCard {
            SwitchSetting(
                "Hide preview in recent apps",
                if (Build.VERSION.SDK_INT >= 33) "The app switcher shows a blank card instead of what was on screen"
                else "Blank card in the app switcher. On this Android version this also blocks screenshots",
                hideRecents,
            ) { lock.setHideRecents(it) }
            CardDivider()
            SwitchSetting(
                "Block screenshots",
                "Screenshots and screen recordings of ProtoBooru come out black",
                blockShots,
            ) { lock.setBlockScreenshots(it) }
        }
        Hint("These apply right away, to every screen in the app.")
    }

    confirmFor?.let { next ->
        ConfirmPasscodeDialog(
            onDismiss = { confirmFor = null },
            onConfirmed = {
                confirmFor = null
                next()
            },
        )
    }
    if (choosePin) {
        ChoosePasscodeDialog(
            onDismiss = { choosePin = false },
            onChosen = { pin ->
                choosePin = false
                scope.launch {
                    lock.setPasscode(pin)
                    Graph.toast("Passcode set")
                }
            },
        )
    }
}
