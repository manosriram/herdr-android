package dev.herdr.android

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MachineState(val snap: Snapshot? = null, val error: String? = null, val online: Boolean = false)

/** App-wide state: saved machines, their live snapshots, and the polling loop. */
object Herdr {
    lateinit var app: Context
    lateinit var store: Store
    val key by lazy { PhoneKey.load(store) }

    val machines = MutableStateFlow<List<Machine>>(emptyList())
    val states = MutableStateFlow<Map<String, MachineState>>(emptyMap())
    val theme = MutableStateFlow("system")
    /** Set by a notification tap: (machineId, paneId). */
    val openPane = MutableStateFlow<Pair<String, String>?>(null)

    private val conns = ConcurrentHashMap<String, Conn>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val lastStatus = ConcurrentHashMap<String, String>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        store = Store(app)
        machines.value = store.machines()
        theme.value = store.theme
    }

    fun newId() = UUID.randomUUID().toString()

    // ponytail: fixed 3s poll over SSH; switch to `events.subscribe` streaming if battery/latency matters.
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                machines.value.map { m -> async { poll(m) } }.awaitAll()
                delay(3000)
            }
        }
    }

    private fun poll(m: Machine) {
        val next = try {
            val snap = parseSnapshot(run(m, "api", "snapshot"))
            notifyChanges(m, snap)
            MachineState(snap, null, true)
        } catch (e: Exception) {
            MachineState(states.value[m.id]?.snap, e.message ?: e.javaClass.simpleName, false)
        }
        states.value = states.value + (m.id to next)
    }

    fun run(m: Machine, vararg args: String): String =
        conns.getOrPut(m.id) { Conn(m, store, key) }.exec(herdrCommand(m.session, args.toList()))

    suspend fun runAsync(m: Machine, vararg args: String) = withContext(Dispatchers.IO) { run(m, *args) }

    /** Fresh connection check for setup screens. stage: 1 reachable, 2 logged in, 3 herdr found. */
    suspend fun probe(m: Machine, stage: (Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val c = Conn(m, store, key)
        try {
            c.open { s -> scope.launch(Dispatchers.Main) { stage(s) } }
            val sessions = parseSessions(c.exec(herdrCommand("default", listOf("session", "list", "--json"))))
            withContext(Dispatchers.Main) { stage(3) }
            sessions
        } finally {
            c.close()
        }
    }

    fun saveMachine(m: Machine) {
        conns.remove(m.id)?.close()
        machines.value = machines.value.filter { it.id != m.id } + m
        store.saveMachines(machines.value)
        syncService()
    }

    fun removeMachine(id: String) {
        conns.remove(id)?.close()
        machines.value = machines.value.filter { it.id != id }
        states.value = states.value - id
        store.saveMachines(machines.value)
        store.setHostKey(id, null)
        syncService()
    }

    fun setTheme(t: String) {
        store.theme = t
        theme.value = t
    }

    /** Keeps polling alive in the background only while some notification is wanted. */
    fun syncService() {
        val want = (store.notifyBlocked || store.notifyDone) && machines.value.isNotEmpty() && store.onboarded
        val i = Intent(app, WatchService::class.java)
        if (want) ContextCompat.startForegroundService(app, i) else app.stopService(i)
    }

    private fun notifyChanges(m: Machine, snap: Snapshot) {
        for (p in snap.agents) {
            val k = "${m.id}/${p.id}"
            val prev = lastStatus.put(k, p.status) ?: continue // first sighting: no alert
            if (prev == p.status) continue
            val ws = snap.workspace(p.workspaceId)?.label ?: p.workspaceId
            when {
                p.status == "blocked" && store.notifyBlocked -> alert(m, p, "${p.name} is blocked", "${m.name} · $ws · ${p.id}")
                p.status == "done" && prev == "working" && store.notifyDone -> alert(m, p, "${p.name} finished", "${m.name} · $ws · ${p.id}")
            }
        }
    }

    private fun alert(m: Machine, p: Pane, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = "${m.id}/${p.id}".hashCode()
        val tap = Intent(app, MainActivity::class.java)
            .putExtra("machine", m.id).putExtra("pane", p.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(app, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, "agents")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(app).notify(id, n)
    }
}

class HerdrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android ships a stripped "BC"; sshj needs the full provider for curve25519/ed25519.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("agents", "Agent alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("watch", "Background watch", NotificationManager.IMPORTANCE_MIN))
        Herdr.init(this)
    }
}

/** Foreground service whose only job is keeping the process (and Herdr's poll loop) alive. */
class WatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "watch")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Watching your herdr machines")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, n, type)
        Herdr.start()
        return START_STICKY
    }
}
