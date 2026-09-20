package com.uci.pkbe

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeScreen(activity: Activity) {
    val context = LocalContext.current
    val api = remember { ApiClient(context) }
    val passkeys = remember { PasskeyService(activity) }
    var loggedIn by remember { mutableStateOf(SessionStore.token(context) != null) }

    if (!loggedIn) {
        LoginPane(api) { loggedIn = true }
    } else {
        HomePane(
            api = api,
            passkeys = passkeys,
            onLogout = { loggedIn = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPane(api: ApiClient, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("alice") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("PKBE") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text("Stub login", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !busy,
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val res = api.login(username.trim().lowercase())
                            SessionStore.save(context, res.token, res.username)
                            onLoggedIn()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && username.isNotBlank(),
            ) { Text("Continue") }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePane(
    api: ApiClient,
    passkeys: PasskeyService,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var localPasskey by remember { mutableStateOf("—") }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    suspend fun refresh(reconcileLocal: Boolean) {
        var latest = api.me()
        localPasskey = "—"
        if (!reconcileLocal) {
            me = latest
            return
        }
        if (latest.thisDeviceStatus == "NONE") {
            localPasskey = "none (not enrolled on server)"
            me = latest
            if (error?.contains("Cleared server enrollment") != true) error = null
            return
        }
        val credId = latest.thisDeviceCredentialId
        if (credId.isNullOrBlank()) {
            localPasskey = "unknown (server missing credential id)"
            me = latest
            error = "Server did not return thisDeviceCredentialId."
            return
        }
        if (passkeys.localPasskeyPresence(credId)) {
            localPasskey = "present on device"
            me = latest
            error = null
        } else {
            localPasskey = "missing on device"
            latest = api.unenroll()
            me = latest
            localPasskey = "none (cleared after local delete)"
            error = "Passkey not found on this device. Cleared server enrollment."
        }
    }

    LaunchedEffect(Unit) {
        run { refresh(true) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Activation") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Client config")
            Row("Package", context.packageName)
            Row("RP ID", Config.rpId)
            Row("API", Config.baseUrl)

            SectionTitle("This device")
            Row("Device ID", short(DeviceIdentity.deviceId(context)))
            Row("User", me?.username ?: SessionStore.username(context) ?: "—")
            Row("Server status", me?.thisDeviceStatus ?: "—")
            Row("Local passkey", localPasskey)
            Row("Active device", short(me?.activeDeviceId))
            Row("Pending device", short(me?.pendingDeviceId))

            me?.activeCredential?.let { cred ->
                SectionTitle("Server registry (not Google Password Manager)")
                Row("Credential", cred.credentialIdPrefix)
                Row("AAGUID", cred.aaguid)
                Row("Backup eligible (BE)", if (cred.backupEligible) "yes" else "no")
                Row("Backup state (BS)", if (cred.backupState) "yes" else "no")
                Text(
                    "This is the RP database row. Deleting the passkey on the phone does not remove it until Refresh + reconcile runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            SectionTitle("Actions")
            Action("Refresh + reconcile") {
                run { refresh(true) }
            }
            Action("Run association diagnostics") {
                run { diagnostics = PasskeyDiagnostics.runPreflight(context, api) }
            }
            Action("Enroll this device") {
                run {
                    val options = api.registerOptionsJson()
                    val credential = passkeys.createPasskey(options)
                    me = api.registerVerify(credential)
                    localPasskey = "present"
                    error = null
                }
            }
            Action("Activate via nearby device") {
                run {
                    if (me?.thisDeviceStatus != "PENDING") {
                        val options = api.registerOptionsJson()
                        val credential = passkeys.createPasskey(options)
                        me = api.registerVerify(credential)
                    }
                    val assertionOptions = api.handoverOptionsJson()
                    val assertion = passkeys.assertHandover(assertionOptions)
                    me = api.handoverVerify(assertion)
                    localPasskey = "present"
                    error = null
                }
            }
            TextButton(
                onClick = {
                    run {
                        me = api.unenroll()
                        localPasskey = "none"
                        error = "Server enrollment cleared for this device."
                    }
                },
                enabled = !busy && me?.thisDeviceStatus != "NONE",
            ) { Text("Clear server enrollment") }
            TextButton(
                onClick = {
                    run {
                        api.logout()
                        onLogout()
                    }
                },
                enabled = !busy,
            ) { Text("Log out") }

            error?.let {
                SectionTitle("Error")
                SelectionContainer {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            diagnostics?.let {
                SectionTitle("Diagnostics")
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
            Text(
                "Enroll prefers credentials already on this device. Nearby QR is used by Activate via nearby device. Asset Links (package + SHA-256) must be set on the RP or origin checks fail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun Row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(label) }
}

private fun short(value: String?): String {
    if (value.isNullOrEmpty()) return "—"
    return value.take(8)
}
