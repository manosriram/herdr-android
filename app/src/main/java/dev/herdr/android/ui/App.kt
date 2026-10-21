package dev.herdr.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.herdr.android.Herdr

sealed interface Screen {
    data object Onboarding : Screen
    data object Machines : Screen
    data object Agents : Screen
    data class Workspaces(val machineId: String? = null) : Screen
    data class Workspace(val machineId: String, val wsId: String) : Screen
    data class PaneView(val machineId: String, val paneId: String) : Screen
    data class EditMachine(val id: String?) : Screen
    data object Settings : Screen
    data object Key : Screen
}

class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val top get() = stack.last()
    fun push(s: Screen) = stack.add(s)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun reset(s: Screen) {
        stack.clear()
        stack.add(s)
    }
}

@Composable
fun App() {
    val p = LocalPalette.current
    val nav = remember { Nav(if (Herdr.store.onboarded) Screen.Machines else Screen.Onboarding) }
    val open by Herdr.openPane.collectAsState()
    LaunchedEffect(open) {
        open?.let { (m, pane) ->
            nav.push(Screen.PaneView(m, pane))
            Herdr.openPane.value = null
        }
    }
    BackHandler(nav.stack.size > 1) { nav.pop() }
    Column(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (val s = nav.top) {
                Screen.Onboarding -> Onboarding(nav)
                Screen.Machines -> MachinesScreen(nav)
                Screen.Agents -> AgentsScreen(nav)
                is Screen.Workspaces -> WorkspacesScreen(nav, s.machineId)
                is Screen.Workspace -> WorkspaceScreen(nav, s.machineId, s.wsId)
                is Screen.PaneView -> PaneScreen(nav, s.machineId, s.paneId)
                is Screen.EditMachine -> EditMachineScreen(nav, s.id)
                Screen.Settings -> SettingsScreen(nav)
                Screen.Key -> KeyScreen(nav)
            }
        }
        val top = nav.top
        if (top == Screen.Machines || top == Screen.Agents || top is Screen.Workspaces) BottomNav(nav)
    }
}

@Composable
private fun BottomNav(nav: Nav) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(p.surface).border(1.dp, p.line).padding(horizontal = 8.dp, vertical = 6.dp)) {
        listOf(
            Triple("Machines", Icons.Filled.Home, Screen.Machines),
            Triple("Agents", Icons.Filled.Person, Screen.Agents),
            Triple("Workspaces", Icons.AutoMirrored.Filled.List, Screen.Workspaces()),
        ).forEach { (label, icon, screen) ->
            val on = nav.top::class == screen::class
            val c = if (on) p.text else p.muted
            Column(
                Modifier.weight(1f).heightIn(min = 52.dp).clickable(role = Role.Tab) { nav.reset(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = c)
                T(label, 12, c, if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ---- shared widgets ----

@Composable
fun T(
    text: String,
    size: Int = 14,
    color: Color = LocalPalette.current.text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = Plex,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.45).sp,
)

@Composable
fun Header(kicker: String?, title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (back != null) 8.dp else 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        back?.let { IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = it) }
        Column(Modifier.weight(1f)) {
            kicker?.let { T(it, 13, LocalPalette.current.muted) }
            T(title, if (back == null) 28 else 20, weight = FontWeight.Bold, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, filled: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(44.dp).background(if (filled) p.surface else Color.Transparent).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = p.text) }
}

@Composable
fun Label(text: String, color: Color = LocalPalette.current.muted) =
    T(text.uppercase(), 12, color, FontWeight.SemiBold, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = LocalPalette.current.line,
    bg: Color = LocalPalette.current.surface,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier.fillMaxWidth().background(bg).border(1.dp, border), content = content)

@Composable
fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (enabled) p.accent else p.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 15, if (enabled) p.onAccent else p.muted, FontWeight.SemiBold) }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.heightIn(min = 48.dp).border(1.dp, p.line).background(p.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13, if (enabled) p.text else p.muted) }
}

@Composable
fun Ghost(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.muted, onClick: () -> Unit) =
    Box(modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        T(text, 13, color)
    }

@Composable
fun Dot(color: Color, size: Int = 8) = Box(Modifier.size(size.dp).background(color))

fun statusColor(status: String, p: Palette) = when (status) {
    "blocked" -> p.blocked
    "working" -> p.working
    "done", "idle" -> p.done
    else -> p.muted
}

@Composable
fun Badge(text: String, color: Color, bg: Color) =
    T(text, 12, color, modifier = Modifier.background(bg).padding(horizontal = 8.dp, vertical = 3.dp))

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.heightIn(min = 36.dp).background(if (selected) p.text else Color.Transparent)
            .border(1.dp, if (selected) p.text else p.line).clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13, if (selected) p.bg else p.text, if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) = OutlinedTextField(
    value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
)

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Checkbox) { onChange(!checked) }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, 14, modifier = Modifier.weight(1f))
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.onAccent))
    }
}

@Composable
fun CodeBox(caption: String, code: String) {
    val p = LocalPalette.current
    Panel(bg = p.term) {
        T(caption, 11, p.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        T(code, 11, p.text, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

fun copy(ctx: Context, text: String) =
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("herdr", text))

fun share(ctx: Context, text: String) =
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
