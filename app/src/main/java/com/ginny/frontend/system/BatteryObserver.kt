package com.ginny.frontend.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryStatus(val percent: Int, val isCharging: Boolean)

/**
 * Watches ACTION_BATTERY_CHANGED and decides whether the reported level is trustworthy enough to
 * show in the top bar, per the "Detección de batería no confiable" spec:
 * - No sticky broadcast at all within a short grace period after registering -> hidden.
 * - Level outside 0..100 -> hidden.
 * - The system reports no battery present (`EXTRA_PRESENT` = false) -> hidden.
 * - Level frozen at the exact same value for longer than [stuckThresholdMs] while NOT charging ->
 *   hidden (a value that never moves while draining is almost certainly a stub/fake reading;
 *   pinned-at-100-while-charging is normal and explicitly excluded from this check).
 */
class BatteryObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stuckThresholdMs: Long = 10 * 60 * 1000L,
    private val startupGraceMs: Long = 5_000L,
) {
    private val _status = MutableStateFlow<BatteryStatus?>(null)
    val status: StateFlow<BatteryStatus?> = _status.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var receivedAnyEvent = false
    private var lastPercent: Int? = null
    private var lastCharging: Boolean? = null
    private var lastChangedAtMs: Long = 0L
    private var unreliable = false

    fun start() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                receivedAnyEvent = true
                onReading(intent)
            }
        }
        this.receiver = receiver
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        scope.launch {
            delay(startupGraceMs)
            if (!receivedAnyEvent) markUnreliable()
        }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun onReading(intent: Intent) {
        if (unreliable) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        // Equipo sin batería (mini PC, TV box con Android): suele reportar "100 % enchufado", que parece un dato real y no lo es.
        // EXTRA_PRESENT solo es false si el sistema lo dice; si falta el extra se asume que hay batería.
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)) {
            markUnreliable()
            return
        }
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        if (percent !in 0..100) {
            markUnreliable()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val chargingChanged = lastCharging != null && lastCharging != charging
        lastCharging = charging
        if (lastPercent != percent || chargingChanged) {
            lastPercent = percent
            lastChangedAtMs = now
        } else if (!charging && now - lastChangedAtMs > stuckThresholdMs) {
            markUnreliable()
            return
        }

        _status.value = BatteryStatus(percent, charging)
    }

    private fun markUnreliable() {
        unreliable = true
        _status.value = null
    }
}
