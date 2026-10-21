package dev.herdr.android

import org.json.JSONArray
import org.json.JSONObject

data class Machine(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val session: String = "default",
    val password: String? = null,
)

data class Workspace(val id: String, val label: String, val status: String)
data class Tab(val id: String, val workspaceId: String, val label: String)
data class Pane(
    val id: String,
    val workspaceId: String,
    val tabId: String,
    val agent: String?,
    val status: String,
    val title: String,
    val cwd: String,
) {
    val name get() = agent ?: title.ifBlank { "shell" }
}

data class Snapshot(val workspaces: List<Workspace>, val tabs: List<Tab>, val panes: List<Pane>) {
    val agents get() = panes.filter { it.agent != null }
    fun workspace(id: String) = workspaces.find { it.id == id }
}

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

private fun <T> JSONArray?.map(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

/** Parses `herdr api snapshot` output. */
fun parseSnapshot(out: String): Snapshot {
    val s = JSONObject(out).getJSONObject("result").getJSONObject("snapshot")
    return Snapshot(
        workspaces = s.optJSONArray("workspaces").map {
            Workspace(it.getString("workspace_id"), it.str("label") ?: it.getString("workspace_id"), it.str("agent_status") ?: "unknown")
        },
        tabs = s.optJSONArray("tabs").map {
            Tab(it.getString("tab_id"), it.getString("workspace_id"), it.str("label") ?: it.getString("tab_id"))
        },
        panes = s.optJSONArray("panes").map {
            Pane(
                id = it.getString("pane_id"),
                workspaceId = it.getString("workspace_id"),
                tabId = it.getString("tab_id"),
                agent = it.str("agent"),
                status = it.str("agent_status") ?: "unknown",
                title = it.str("terminal_title_stripped") ?: "",
                cwd = it.str("cwd") ?: "",
            )
        },
    )
}

/** Names of running sessions from `herdr session list --json`. */
fun parseSessions(out: String): List<String> =
    JSONObject(out).optJSONArray("sessions").map { it }.filter { it.optBoolean("running") }.map { it.getString("name") }

fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

/** Remote command line; non-interactive SSH shells often lack herdr's install dirs on PATH. */
fun herdrCommand(session: String, args: List<String>): String {
    val env = if (session == "default") "" else "HERDR_SESSION=${shellQuote(session)} "
    return "PATH=\"\$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:\$PATH\" ${env}herdr " +
        args.joinToString(" ") { shellQuote(it) }
}

fun Machine.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("host", host).put("port", port)
    .put("user", user).put("session", session)

fun machineFromJson(o: JSONObject, password: String?) = Machine(
    id = o.getString("id"),
    name = o.getString("name"),
    host = o.getString("host"),
    port = o.optInt("port", 22),
    user = o.getString("user"),
    session = o.optString("session", "default"),
    password = password,
)
