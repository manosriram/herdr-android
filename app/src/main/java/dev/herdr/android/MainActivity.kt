package dev.herdr.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.herdr.android.ui.App
import dev.herdr.android.ui.HerdrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val theme by Herdr.theme.collectAsState()
            HerdrTheme(theme) { App() }
        }
    }

    override fun onStart() {
        super.onStart()
        Herdr.start()
        Herdr.syncService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        val m = i.getStringExtra("machine") ?: return
        val p = i.getStringExtra("pane") ?: return
        Herdr.openPane.value = m to p
    }
}
