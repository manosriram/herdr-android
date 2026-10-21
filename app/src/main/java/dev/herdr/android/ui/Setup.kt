package dev.herdr.android.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.herdr.android.AuthFailed
import dev.herdr.android.Herdr
import dev.herdr.android.HerdrError
import dev.herdr.android.Machine
import dev.herdr.android.PhoneKey
import dev.herdr.android.R
import dev.herdr.android.shellQuote
import kotlinx.coroutines.launch

// ---- connection check shared by onboarding and the machine form ----

class Probe {
    var stage by mutableIntStateOf(-1)
    var error by mutableStateOf<String?>(null)
    var authFailed by mutableStateOf(false)
    var herdrMissing by mutableStateOf(false)
    var sessions by mutableStateOf<List<String>?>(null)
    val running get() = stage >= 0 && error == null && sessions == null

    suspend fun run(m: Machine) {
        stage = 0; error = null; authFailed = false; herdrMissing = false; sessions = null
        try {
            sessions = Herdr.probe(m) { stage = it }
        } catch (e: AuthFailed) {
            authFailed = true
            error = e.message
        } catch (e: HerdrError) {
            herdrMissing = e.code == 127
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
}

@Composable
fun ProbeView(pr: Probe) {
    if (pr.stage < 0) return
    val p = LocalPalette.current
    Panel(bg = p.term) {
        listOf("Machine reachable", "Signed in", "herdr installed").forEachIndexed { i, label ->
            val ok = pr.stage > i
            val failed = !ok && pr.error != null && pr.stage == i
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (ok) "✓" else if (failed) "✗" else "…", 13, if (ok) p.done else if (failed) p.blocked else p.working, modifier = Modifier.width(24.dp))
                T(label, 13)
            }
        }
    }
    pr.error?.let { T(it, 12, p.blocked) }
    if (pr.herdrMissing) CodeBox("install herdr on the machine", "curl -fsSL https://herdr.dev/install.sh | sh")
}

@Composable
private fun SessionPicker(sessions: List<String>, selected: String, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    T("herdr session", 12, p.muted)
    (sessions.ifEmpty { listOf("default") }).forEach { s ->
        val on = s == selected
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, if (on) p.accent else p.line)
                .clickable(role = Role.RadioButton) { onPick(s) }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(on, null, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
            T(s, 13)
        }
    }
}

// ---- onboarding ----

@Composable
fun Onboarding(nav: Nav) {
    var step by rememberSaveable { mutableIntStateOf(0) } // 0 welcome, 1 network, 2 machine, 3 key, 4 notify
    val back = { step = if (step >= 3) 2 else step - 1 }
    BackHandler(step > 0) { back() }
    when (step) {
        0 -> Welcome { step = 1 }
        1 -> Step("step 1 of 3", 1, back) { NetworkStep { step = 2 } }
        2 -> Step("step 2 of 3", 2, back) { MachineStep(onKey = { step = 3 }, onDone = { step = 4 }) }
        3 -> Step("extra step · no Tailscale SSH", 2, back) { KeyContent(); Spacer(Modifier.weight(1f)); Primary("I've added it") { step = 2 } }
        else -> Step("step 3 of 3", 3, back) { NotifyStep(nav) }
    }
}

@Composable
private fun Step(label: String, filled: Int, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = back)
            T(label, 12, p.muted)
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(3.dp).background(if (it < filled) p.accent else p.line)) }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Welcome(next: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        T("herdr", 14, p.muted)
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.logo), null, Modifier.size(72.dp), colorFilter = ColorFilter.tint(p.text))
        T("Your agents,\nin your pocket.", 30, weight = FontWeight.Bold)
        T("See every herdr machine in one list, get pinged when an agent is blocked, and answer it from your phone.", 14)
        T("You'll need SSH access to a machine running herdr.", 13, p.muted)
        Spacer(Modifier.weight(1f))
        Primary("Get started", onClick = next)
    }
}

@Composable
private fun ColumnScope.NetworkStep(next: () -> Unit) {
    val p = LocalPalette.current
    T("How will your phone reach your machines?", 22, weight = FontWeight.Bold)
    T("herdr talks over SSH. Your phone needs a route to each machine's SSH port.", 13, p.muted)
    listOf(
        "Tailscale (recommended)" to "Works anywhere, no open ports. With Tailscale SSH on (`sudo tailscale set --ssh`) no key setup is needed.",
        "Same Wi-Fi" to "Use the machine's local IP. Only works at home.",
        "Public host" to "VPS or port-forward. Use key-only login.",
    ).forEach { (title, body) ->
        Panel {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(title, 15, weight = FontWeight.SemiBold)
                T(body, 12, p.muted)
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Primary("Continue", onClick = next)
}

@Composable
private fun ColumnScope.MachineStep(onKey: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    val id = rememberSaveable { Herdr.newId() }
    val pr = remember { Probe() }
    var session by remember { mutableStateOf("default") }
    fun machine(): Machine {
        val h = host.trim().substringBefore(':')
        val port = host.substringAfter(':', "22").toIntOrNull() ?: 22
        return Machine(id, h.substringBefore('.'), h, port, user.trim(), session)
    }

    T("Connect your first machine", 22, weight = FontWeight.Bold)
    Field("Host (name, IP or host:port)", host) { host = it }
    Field("User", user) { user = it }
    ProbeView(pr)
    Ghost("No Tailscale SSH on this machine? Set up a key", color = LocalPalette.current.accent, onClick = onKey)
    pr.sessions?.let { SessionPicker(it, session) { s -> session = s } }
    Spacer(Modifier.weight(1f))
    if (pr.sessions == null) {
        Primary(if (pr.running) "Connecting…" else "Connect", enabled = host.isNotBlank() && user.isNotBlank() && !pr.running) {
            scope.launch {
                pr.run(machine())
                pr.sessions?.let { s -> if (s.isNotEmpty() && session !in s) session = s.first() }
            }
        }
    } else {
        Primary("Continue") {
            Herdr.saveMachine(machine())
            onDone()
        }
    }
}

@Composable
private fun ColumnScope.NotifyStep(nav: Nav) {
    val p = LocalPalette.current
    var blocked by remember { mutableStateOf(Herdr.store.notifyBlocked) }
    var done by remember { mutableStateOf(Herdr.store.notifyDone) }
    fun finish(enabled: Boolean) {
        Herdr.store.notifyBlocked = enabled && blocked
        Herdr.store.notifyDone = enabled && done
        Herdr.store.onboarded = true
        Herdr.syncService()
        nav.reset(Screen.Machines)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish(it) }
    T("Know when an agent needs you", 22, weight = FontWeight.Bold)
    T("Get a notification the moment an agent is blocked on a question or approval.", 13, p.muted)
    Panel {
        CheckRow("Blocked agents", blocked) { blocked = it }
        CheckRow("Agent finished", done) { done = it }
    }
    Spacer(Modifier.weight(1f))
    Primary("Allow notifications") {
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else finish(true)
    }
    Ghost("Not now", Modifier.fillMaxWidth()) { finish(false) }
}

// ---- phone key ----

@Composable
fun ColumnScope.KeyContent() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val line = remember { PhoneKey.authorizedKeysLine(Herdr.key) }
    val cmd = "mkdir -p ~/.ssh && echo ${shellQuote(line)} >> ~/.ssh/authorized_keys"
    T("Let your machine trust this phone", 22, weight = FontWeight.Bold)
    T("This phone has its own SSH key; the private half never leaves it. Run this once on each machine:", 13, p.muted)
    CodeBox("on your machine", cmd)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Secondary("Copy", Modifier.weight(1f)) { copy(ctx, cmd) }
        Secondary("Share", Modifier.weight(1f)) { share(ctx, cmd) }
    }
    T("fingerprint  ${PhoneKey.fingerprint(Herdr.key.public)}", 11, p.muted)
}

@Composable
fun KeyScreen(nav: Nav) = Column(Modifier.fillMaxSize()) {
    Header(null, "Phone SSH key", back = { nav.pop() })
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { KeyContent() }
}

// ---- add / edit machine ----

@Composable
fun EditMachineScreen(nav: Nav, id: String?) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val existing = remember { Herdr.machines.value.find { it.id == id } }
    val mid = remember { existing?.id ?: Herdr.newId() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var session by remember { mutableStateOf(existing?.session ?: "default") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    val pr = remember { Probe() }
    val valid = host.isNotBlank() && user.isNotBlank() && port.toIntOrNull() != null
    fun machine() = Machine(
        mid, name.ifBlank { host.substringBefore('.') }.trim(), host.trim(), port.toInt(), user.trim(),
        session.ifBlank { "default" }.trim(), password.ifEmpty { null },
    )

    Column(Modifier.fillMaxSize()) {
        Header(null, if (existing == null) "Add machine" else "Edit machine", back = { nav.pop() })
        Column(
            Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("Name", name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Host", host, Modifier.weight(2f)) { host = it }
                Field("Port", port, Modifier.weight(1f), KeyboardType.Number) { port = it }
            }
            Field("User", user) { user = it }
            Field("Password (optional)", password, secret = true, keyboard = KeyboardType.Password) { password = it }
            T("Leave empty to use Tailscale SSH or this phone's key.", 12, p.muted)
            Field("herdr session", session) { session = it }
            Ghost("Phone SSH key ›", color = p.accent) { nav.push(Screen.Key) }
            ProbeView(pr)
            if (existing != null) Ghost("Remove machine", color = p.blocked) {
                Herdr.removeMachine(existing.id)
                nav.pop()
            }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Secondary("Test", Modifier.weight(1f), enabled = valid && !pr.running) { scope.launch { pr.run(machine()) } }
            Primary("Save", Modifier.weight(2f), enabled = valid) {
                Herdr.saveMachine(machine())
                nav.pop()
            }
        }
    }
}

// ---- settings ----

@Composable
fun SettingsScreen(nav: Nav) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val theme by Herdr.theme.collectAsState()
    var blocked by remember { mutableStateOf(Herdr.store.notifyBlocked) }
    var done by remember { mutableStateOf(Herdr.store.notifyDone) }
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(null, "Settings", back = { nav.pop() })
        Label("Appearance")
        Panel(Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Theme", 14)
                Row(Modifier.fillMaxWidth().border(1.dp, p.line)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
                        val on = theme == id
                        Box(
                            Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) p.accent else p.surface)
                                .clickable(role = Role.Tab) { Herdr.setTheme(id) },
                            contentAlignment = Alignment.Center,
                        ) { T(label, 13, if (on) p.onAccent else p.text, FontWeight.SemiBold) }
                    }
                }
                T(
                    mapOf("system" to "Follows your phone: Paper in light mode, Midnight in dark.", "light" to "Paper", "dark" to "Midnight")[theme] ?: "",
                    12, p.muted,
                )
            }
        }
        Label("Notifications")
        Panel(Modifier.padding(horizontal = 12.dp)) {
            fun changed() {
                Herdr.store.notifyBlocked = blocked
                Herdr.store.notifyDone = done
                if ((blocked || done) && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                Herdr.syncService()
            }
            CheckRow("Agent blocked", blocked) { blocked = it; changed() }
            CheckRow("Agent finished", done) { done = it; changed() }
        }
        Label("Connection")
        Panel(Modifier.padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { nav.push(Screen.Key) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("Phone SSH key", 14, modifier = Modifier.weight(1f))
                T("ed25519 ›", 12, p.muted)
            }
        }
        T("herdr for Android · $version", 11, p.muted, modifier = Modifier.padding(20.dp))
    }
}
