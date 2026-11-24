package dev.lunikodevelopment.ludoform.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DeviceStatus(val time: String, val battery: Int?, val online: Boolean)

/** Clock, battery and network for the Stage status pill. */
@Composable
fun rememberDeviceStatus(): DeviceStatus {
    val context = LocalContext.current.applicationContext
    var time by remember { mutableStateOf(now()) }
    var battery by remember { mutableIntStateOf(-1) }
    var online by remember { mutableStateOf(isOnline(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            time = now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0 && scale > 0) battery = level * 100 / scale
                }
            }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED)
            ?.let { receiver.onReceive(context, it) }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online = true
                }

                override fun onLost(network: Network) {
                    online = isOnline(context)
                }
            }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose {
            context.unregisterReceiver(receiver)
            connectivity.unregisterNetworkCallback(callback)
        }
    }
    return DeviceStatus(time, battery.takeIf { it >= 0 }, online)
}

private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
