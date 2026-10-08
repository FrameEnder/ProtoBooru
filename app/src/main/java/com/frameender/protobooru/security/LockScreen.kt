package com.frameender.protobooru.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.frameender.protobooru.R
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Covers the whole app while it's locked. It's its own full-screen window, opened when the
 * lock engages, so it sits above everything else, including any dialog or fullscreen video
 * that was open. Back leaves the app instead of getting past it.
 */
@Composable
fun LockGate() {
    val lock = Graph.lock
    val locked by lock.locked.collectAsState()
    val method by lock.method.collectAsState()
    if (!locked || method == AppLock.Method.OFF) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val context = LocalContext.current
        // Fill the whole screen (dialog windows wrap their content by default).
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
        BackHandler { context.findActivity()?.moveTaskToBack(true) }
        Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.6f))
                Box(Modifier.size(84.dp).clip(CircleShape).background(Ink.Surface), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ProtoBooru is locked", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(24.dp))
                when (method) {
                    AppLock.Method.PASSCODE -> PinUnlock(onUnlocked = { lock.unlock() }, showForgot = true, modifier = Modifier.weight(3f))
                    else -> BiometricUnlock(onUnlocked = { lock.unlock() }, modifier = Modifier.weight(3f))
                }
            }
        }
    }
}

@Composable
private fun BiometricUnlock(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun ask() = Biometric.prompt(context, "Unlock ProtoBooru") { ok -> if (ok) onUnlocked() }
    // Ask straight away, and again each time the app comes back (Android closes the prompt
    // when the app leaves the screen). The button is there if the prompt was dismissed.
    val returns by Graph.lock.returns.collectAsState()
    LaunchedEffect(returns) {
        delay(250)
        ask()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Use your fingerprint, face or screen lock",
            style = MaterialTheme.typography.bodyMedium, color = Ink.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { ask() }) {
            Icon(Icons.Default.Fingerprint, null)
            Spacer(Modifier.width(8.dp))
            Text("Unlock", maxLines = 1)
        }
    }
}

/**
 * Number pad for entering the passcode. Digits are typed on this pad (never through a
 * keyboard app). Wrong tries count down; then a growing wait is enforced.
 */
@Composable
fun PinUnlock(onUnlocked: () -> Unit, showForgot: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lock = Graph.lock
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var waitUntil by remember { mutableLongStateOf(lock.lockoutUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by remember { mutableStateOf(false) }

    LaunchedEffect(waitUntil) {
        while (waitUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val waiting = waitUntil > now

    fun submit() {
        if (pin.length < AppLock.MIN_PIN || checking || waiting) return
        checking = true
        scope.launch {
            when (val r = lock.checkPasscode(pin)) {
                AppLock.Check.Ok -> {
                    message = null
                    onUnlocked()
                }
                is AppLock.Check.Wrong -> message = if (r.triesLeft > 0) "Wrong passcode · ${r.triesLeft} tries before a wait" else "Wrong passcode"
                is AppLock.Check.Wait -> {
                    waitUntil = r.untilMillis
                    message = null
                }
            }
            pin = ""
            checking = false
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(pin.length)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                waiting -> "Too many wrong tries · try again in ${((waitUntil - now) / 1000) + 1} s"
                checking -> "Checking…"
                else -> message ?: "Enter your passcode"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (message != null || waiting) Ink.Red else Ink.TextDim,
        )
        Spacer(Modifier.height(18.dp))
        NumberPad(
            enabled = !waiting && !checking,
            onDigit = { d -> if (pin.length < AppLock.MAX_PIN) pin += d },
            onBackspace = { pin = pin.dropLast(1) },
            onDone = { submit() },
            doneEnabled = pin.length >= AppLock.MIN_PIN,
        )
        if (showForgot && Biometric.deviceSecure(context)) {
            TextButton(onClick = { forgot = true }) { Text("Forgot passcode?", maxLines = 1) }
        }
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Forgot your passcode?") },
            text = {
                Text(
                    "Confirm with your phone's screen lock (or fingerprint) to remove the passcode. " +
                        "You can set a new one in Settings → Privacy & security.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    forgot = false
                    Biometric.prompt(context, "Remove ProtoBooru's passcode", "Confirm it's you") { ok ->
                        if (ok) {
                            lock.turnOff()
                            Graph.toast("Passcode removed. Set a new one in Settings → Privacy & security")
                        }
                    }
                }) { Text("Continue", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Cancel", maxLines = 1) } },
            containerColor = Ink.Surface2,
        )
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
        val shown = count.coerceAtLeast(AppLock.MIN_PIN)
        repeat(shown) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < count) Ink.Amber else Color.Transparent)
                    .border(1.5.dp, if (i < count) Ink.Amber else Ink.TextDim, CircleShape),
            )
        }
    }
}

@Composable
fun NumberPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    doneLabel: String = "OK",
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                r.forEach { d -> PadKey(enabled, { onDigit(d) }) { Text(d.toString(), fontSize = 26.sp) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PadKey(enabled, onBackspace, subtle = true) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") }
            PadKey(enabled, { onDigit('0') }) { Text("0", fontSize = 26.sp) }
            PadKey(enabled && doneEnabled, onDone, accent = true) { Text(doneLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun PadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    subtle: Boolean = false,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = when {
            accent && enabled -> Ink.Amber
            subtle -> Color.Transparent
            else -> Ink.Surface2
        },
        contentColor = if (accent && enabled) Ink.OnAmber else if (enabled) Ink.Text else Ink.TextDim,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Two-step "choose a passcode, then type it again" dialog. */
@Composable
fun ChoosePasscodeDialog(onDismiss: () -> Unit, onChosen: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    PadDialog(onDismiss) {
        Text(if (first == null) "Choose a passcode" else "Enter it again", style = MaterialTheme.typography.titleMedium)
        Text(
            error ?: "${AppLock.MIN_PIN}–${AppLock.MAX_PIN} digits",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) Ink.Red else Ink.TextDim,
        )
        Spacer(Modifier.height(16.dp))
        PinDots(pin.length)
        Spacer(Modifier.height(20.dp))
        NumberPad(
            enabled = true,
            onDigit = { d -> if (pin.length < AppLock.MAX_PIN) pin += d },
            onBackspace = { pin = pin.dropLast(1) },
            doneEnabled = pin.length >= AppLock.MIN_PIN,
            doneLabel = if (first == null) "Next" else "Set",
            onDone = {
                val f = first
                when {
                    f == null -> {
                        first = pin
                        error = null
                    }
                    f == pin -> onChosen(pin)
                    else -> {
                        first = null
                        error = "Those didn't match. Start again."
                    }
                }
                pin = ""
            },
        )
        TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
    }
}

/** Asks for the current passcode before a security setting changes. */
@Composable
fun ConfirmPasscodeDialog(onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    PadDialog(onDismiss) {
        Text("Enter your current passcode", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        PinUnlock(onUnlocked = onConfirmed, showForgot = false)
        TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
    }
}

@Composable
private fun PadDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Ink.Surface2) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
