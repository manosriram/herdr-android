package dev.herdr.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.herdr.android.Herdr
import dev.herdr.android.Machine
import dev.herdr.android.Pane
import dev.herdr.android.Snapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AgentRow(val m: Machine, val pane: Pane, val ws: String)

@Composable
private fun allAgents(): List<AgentRow> {
    val machines by Herdr.machines.collectAsState()
    val states by Herdr.states.collectAsState()
    return machines.flatMap { m ->
        val snap = states[m.id]?.snap ?: return@flatMap emptyList()
        snap.agents.map { AgentRow(m, it, snap.workspace(it.workspaceId)?.label ?: it.workspaceId) }
    }
}

@Composable
fun MachinesScreen(nav: Nav) {
    val p = LocalPalette.current
    val machines by Herdr.machines.collectAsState()
    val states by Herdr.states.collectAsState()
    val agents = allAgents()
    Column(Modifier.fillMaxSize()) {
        Header("herdr", "Machines") {
            IconBtn(Icons.Filled.Settings, "Settings", filled = true) { nav.push(Screen.Settings) }
            IconBtn(Icons.Filled.Add, "Add machine", filled = true) { nav.push(Screen.EditMachine(null)) }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Count(agents.count { it.pane.status == "blocked" }, "blocked", p.blocked, Modifier.weight(1f))
            Count(agents.count { it.pane.status == "working" }, "working", p.working, Modifier.weight(1f))
            Count(agents.count { it.pane.status == "done" || it.pane.status == "idle" }, "done / idle", p.text, Modifier.weight(1f))
        }
        Label("Saved over SSH")
        if (machines.isEmpty()) T("No machines yet. Tap + to add one.", 13, p.muted, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(machines, key = { it.id }) { m ->
                val st = states[m.id]
                val blocked = st?.snap?.agents?.count { it.status == "blocked" } ?: 0
                Panel(Modifier.clickable { nav.push(Screen.Workspaces(m.id)) }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (st?.online == true) p.done else p.muted, 10)
                        T(m.name, 17, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
                        if (blocked > 0) Badge("$blocked blocked", p.blocked, p.blockedBg)
                        IconBtn(Icons.Filled.Edit, "Edit ${m.name}") { nav.push(Screen.EditMachine(m.id)) }
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T("${m.user}@${m.host}${if (m.port != 22) ":${m.port}" else ""} · ${m.session}", 12, p.muted)
                        when {
                            st == null -> T("connecting…", 13, p.muted)
                            st.error != null -> T("offline · ${st.error}", 13, p.blocked)
                            else -> T("${st.snap?.workspaces?.size ?: 0} workspaces · ${st.snap?.agents?.size ?: 0} agents", 13)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) =
    Panel(modifier) {
        Column(Modifier.padding(12.dp)) {
            T("$n", 24, color, FontWeight.Bold)
            T(label, 12, LocalPalette.current.muted)
        }
    }

@Composable
fun AgentsScreen(nav: Nav) {
    val p = LocalPalette.current
    val rows = allAgents()
    var filter by remember { mutableStateOf("all") }
    val groups = listOf(
        Triple("blocked", "Needs you", rows.filter { it.pane.status == "blocked" }),
        Triple("working", "Working", rows.filter { it.pane.status == "working" }),
        Triple("done", "Done", rows.filter { it.pane.status == "done" || it.pane.status == "idle" }),
        Triple("other", "Unknown", rows.filter { it.pane.status !in setOf("blocked", "working", "done", "idle") }),
    )
    Column(Modifier.fillMaxSize()) {
        Header("all machines", "Agents")
        Row(Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All ${rows.size}", filter == "all") { filter = "all" }
            groups.take(3).forEach { (id, _, list) ->
                Chip("${id.replaceFirstChar { it.uppercase() }} ${list.size}", filter == id) { filter = id }
            }
        }
        if (rows.isEmpty()) T("No agents running.", 13, p.muted, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            groups.filter { (id, _, list) -> list.isNotEmpty() && (filter == "all" || filter == id) }.forEach { (id, title, list) ->
                item(key = "h-$id") { Label(title, if (id == "blocked") p.blocked else p.muted) }
                items(list, key = { "${it.m.id}/${it.pane.id}" }) { r ->
                    val blocked = r.pane.status == "blocked"
                    Panel(
                        Modifier.padding(bottom = 6.dp).clickable { nav.push(Screen.PaneView(r.m.id, r.pane.id)) },
                        border = if (blocked) p.blocked else p.line,
                        bg = if (blocked) p.blockedBg else p.surface,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(statusColor(r.pane.status, p))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                T(r.pane.name, 15, weight = FontWeight.SemiBold, maxLines = 1)
                                T("${r.m.name} · ${r.ws} · ${r.pane.id}", 12, p.muted, maxLines = 1)
                            }
                            T(r.pane.status, 12, statusColor(r.pane.status, p))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspacesScreen(nav: Nav, machineId: String?) {
    val p = LocalPalette.current
    val machines by Herdr.machines.collectAsState()
    val states by Herdr.states.collectAsState()
    var selected by remember { mutableStateOf(machineId) }
    val m = machines.find { it.id == selected } ?: machines.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Header(m?.name ?: "no machine", "Workspaces")
        if (machines.size > 1) {
            Row(Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                machines.forEach { x -> Chip(x.name, x.id == m?.id) { selected = x.id } }
            }
        }
        val snap = m?.let { states[it.id]?.snap }
        if (m == null || snap == null) {
            T(if (m == null) "Add a machine first." else states[m.id]?.error ?: "connecting…", 13, p.muted, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(snap.workspaces, key = { it.id }) { ws -> WorkspaceCard(nav, m, snap, ws.id) }
        }
    }
}

@Composable
private fun WorkspaceCard(nav: Nav, m: Machine, snap: Snapshot, wsId: String) {
    val p = LocalPalette.current
    val ws = snap.workspace(wsId) ?: return
    val panes = snap.panes.filter { it.workspaceId == wsId }
    val blocked = panes.count { it.status == "blocked" }
    Panel {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(ws.label, 17, weight = FontWeight.SemiBold)
                T("${panes.firstOrNull()?.cwd?.replace(Regex("^/(Users|home)/[^/]+"), "~") ?: ""} · ${ws.id}", 12, p.muted, maxLines = 1)
            }
            if (blocked > 0) Badge("$blocked blocked", p.blocked, p.blockedBg)
        }
        snap.tabs.filter { it.workspaceId == wsId }.forEach { tab ->
            T(tab.label, 12, p.muted, modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp))
            panes.filter { it.tabId == tab.id }.forEach { pane ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .background(if (pane.status == "blocked") p.blockedBg else p.surface)
                        .border(1.dp, p.line)
                        .clickable { nav.push(Screen.PaneView(m.id, pane.id)) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(pane.id.substringAfter(':'), 12, p.muted, modifier = Modifier.width(36.dp))
                    T(pane.name, 14, modifier = Modifier.weight(1f), maxLines = 1)
                    if (pane.agent != null) T(pane.status, 12, statusColor(pane.status, p))
                }
            }
        }
    }
}

@Composable
fun PaneScreen(nav: Nav, machineId: String, paneId: String) {
    val p = LocalPalette.current
    val machines by Herdr.machines.collectAsState()
    val states by Herdr.states.collectAsState()
    val m = machines.find { it.id == machineId } ?: return
    val snap = states[m.id]?.snap
    val pane = snap?.panes?.find { it.id == paneId }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(m, paneId) {
        while (true) {
            runCatching { Herdr.runAsync(m, "pane", "read", paneId, "--source", "recent", "--lines", "200") }
                .onSuccess { output = it.trimEnd(); error = null }
                .onFailure { error = it.message }
            delay(1500)
        }
    }
    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }

    fun act(vararg args: String) = scope.launch {
        runCatching { Herdr.runAsync(m, *args) }.onFailure { error = it.message }
    }

    val blocked = pane?.status == "blocked"
    Column(Modifier.fillMaxSize()) {
        Header(
            "${m.name} · ${pane?.let { snap.workspace(it.workspaceId)?.label } ?: ""} · $paneId",
            pane?.name ?: paneId,
            back = { nav.pop() },
        ) {
            pane?.agent?.let { Badge(pane.status.uppercase(), statusColor(pane.status, p), if (blocked) p.blockedBg else p.surface) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(p.term).border(1.dp, p.line)) {
            Text(
                output,
                Modifier.verticalScroll(scroll).padding(14.dp),
                color = p.text, fontFamily = Plex, fontSize = 12.sp,
            )
        }
        error?.let { T(it, 12, p.blocked, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (blocked) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1", "2", "3").forEach { k ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp)
                            .background(if (k == "1") p.blocked else p.surface).border(1.dp, p.line)
                            .clickable(role = Role.Button) { act("pane", "send-keys", paneId, k) },
                        contentAlignment = Alignment.Center,
                    ) { T(k, 14, if (k == "1") p.bg else p.text, FontWeight.SemiBold) }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("esc", "enter", "tab", "up", "down", "ctrl+c").forEach { k ->
                Box(
                    Modifier.heightIn(min = 36.dp).border(1.dp, p.line).clickable(role = Role.Button) { act("pane", "send-keys", paneId, k) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { T(mapOf("up" to "↑", "down" to "↓")[k] ?: k, 12, p.text) }
            }
        }
        fun send() {
            val text = prompt.trim()
            if (text.isEmpty()) return
            prompt = ""
            // agent prompt refuses blocked agents; answer those like a terminal.
            if (pane?.agent != null && !blocked) act("agent", "prompt", paneId, text) else act("pane", "run", paneId, text)
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                prompt, { prompt = it }, Modifier.weight(1f),
                placeholder = { Text(if (pane?.agent != null) "Prompt ${pane.name}…" else "Run a command…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Box(
                Modifier.padding(start = 8.dp).size(52.dp).background(p.accent).clickable(role = Role.Button) { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = p.onAccent) }
        }
    }
}
