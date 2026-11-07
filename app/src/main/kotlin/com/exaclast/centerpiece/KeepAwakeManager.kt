package com.exaclast.centerpiece

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AwakeMode {
    OFF,
    INDEFINITE,
    TIMED
}

data class AwakeState(
    val mode: AwakeMode = AwakeMode.OFF,
    val remainingSeconds: Long = 0L,
    val initialDurationMinutes: Int = 0
) {
    val isAwake: Boolean get() = mode != AwakeMode.OFF
}

/**
 * Manages Googlebook OS screen keep-awake functionality via WindowManager FLAG_KEEP_SCREEN_ON
 * and PowerManager WakeLock, with support for indefinite and timed durations.
 */
object KeepAwakeManager {
    private const val TAG = "KeepAwakeManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var appContext: Context? = null
    private var registry: WidgetRegistry? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var windowKeepScreenOnCallback: ((Boolean) -> Unit)? = null

    private val _state = MutableStateFlow(AwakeState())
    val state: StateFlow<AwakeState> = _state.asStateFlow()

    fun init(context: Context, registry: WidgetRegistry, onWindowKeepAwakeChanged: ((Boolean) -> Unit)? = null) {
        this.appContext = context.applicationContext
        this.registry = registry
        if (onWindowKeepAwakeChanged != null) {
            this.windowKeepScreenOnCallback = onWindowKeepAwakeChanged
        }
        updateWidget()
    }

    fun setWindowKeepAwakeCallback(callback: (Boolean) -> Unit) {
        this.windowKeepScreenOnCallback = callback
        callback(_state.value.isAwake)
    }

    fun enableIndefinite() {
        timerJob?.cancel()
        timerJob = null
        acquireWakeLock()
        windowKeepScreenOnCallback?.invoke(true)
        _state.value = AwakeState(mode = AwakeMode.INDEFINITE)
        updateWidget()
    }

    fun enableTimed(minutes: Int) {
        timerJob?.cancel()
        acquireWakeLock()
        windowKeepScreenOnCallback?.invoke(true)

        val totalSeconds = minutes * 60L
        _state.value = AwakeState(
            mode = AwakeMode.TIMED,
            remainingSeconds = totalSeconds,
            initialDurationMinutes = minutes
        )
        updateWidget()

        timerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                remaining--
                if (remaining <= 0) {
                    disable()
                    break
                } else {
                    _state.value = _state.value.copy(remainingSeconds = remaining)
                    // Update widget text periodically
                    if (remaining % 30 == 0L || remaining < 60) {
                        updateWidget()
                    }
                }
            }
        }
    }

    fun disable() {
        timerJob?.cancel()
        timerJob = null
        releaseWakeLock()
        windowKeepScreenOnCallback?.invoke(false)
        _state.value = AwakeState(mode = AwakeMode.OFF)
        updateWidget()
    }

    fun toggle() {
        if (_state.value.isAwake) {
            disable()
        } else {
            enableIndefinite()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val context = appContext ?: return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val lock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "Centerpiece:KeepAwake"
            )
            lock?.acquire()
            wakeLock = lock
            Log.i(TAG, "Screen wake lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Screen wake lock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null
    }

    fun updateWidget() {
        val reg = registry ?: return
        val context = appContext ?: return
        val currentState = _state.value

        val (title, iconRes) = when (currentState.mode) {
            AwakeMode.OFF -> "" to R.drawable.ic_coffee_outline
            AwakeMode.INDEFINITE -> "∞" to R.drawable.ic_coffee
            AwakeMode.TIMED -> {
                val text = if (currentState.remainingSeconds < 60) {
                    "${currentState.remainingSeconds}s"
                } else {
                    val mins = (currentState.remainingSeconds + 59) / 60
                    "${mins}m"
                }
                text to R.drawable.ic_coffee
            }
        }

        val item = CenterpieceItem(
            id = "builtin.awake",
            packageName = context.packageName,
            title = title,
            subtitle = null,
            iconRes = iconRes,
            priority = 700,
            isBuiltIn = true
        )
        reg.registerItem(item)
    }
}
