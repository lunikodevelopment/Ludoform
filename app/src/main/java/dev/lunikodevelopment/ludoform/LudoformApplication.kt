package dev.lunikodevelopment.ludoform

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LudoformApplication : Application() {
    @Inject lateinit var playSessions: PlaySessions

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Screen on and off are only delivered to receivers registered at runtime, so they reach play time only
        // while this process is alive.
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> playSessions.screenOff()
                        Intent.ACTION_SCREEN_ON -> scope.launch { playSessions.screenOn() }
                    }
                }
            },
            filter,
        )
        // RomM down and the network just came back: check now instead of waiting out the backoff.
        getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val settings = dev.lunikodevelopment.ludoform.romm.RommSettings(this@LudoformApplication)
                    if (settings.server() != null && settings.offline() != null) {
                        dev.lunikodevelopment.ludoform.romm.RommHealthWorker.enqueue(this@LudoformApplication)
                    }
                }
            },
        )
    }
}
