package com.exaclast.centerpiece.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.KeepAwakeManager
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.service.CenterpieceHostService

/**
 * BroadcastReceiver enabling external applications, scripts, and ADB shell commands
 * to register, update, or remove top-bar widgets dynamically.
 */
class CenterpieceWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getStringExtra(EXTRA_ID)
            ?: intent.getStringExtra("itemId")
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: intent.getStringExtra("text")
            ?: intent.getStringExtra("name")
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
            ?: intent.getStringExtra("sub")
            ?: intent.getStringExtra("desc")

        val priorityInt = intent.getIntExtra(EXTRA_PRIORITY, -1)
        val priority = if (priorityInt != -1) {
            priorityInt
        } else {
            intent.getStringExtra(EXTRA_PRIORITY)?.toIntOrNull() ?: 500
        }

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: context.packageName

        val alignStr = intent.getStringExtra("alignment")
            ?: intent.getStringExtra("align")
        val offsetXStr = intent.getStringExtra("offset_x")
            ?: intent.getStringExtra("offsetX")
        val offsetX = intent.getIntExtra("offset_x", intent.getIntExtra("offsetX", Int.MIN_VALUE)).let {
            if (it != Int.MIN_VALUE) it else offsetXStr?.toIntOrNull()
        }
        val offsetYStr = intent.getStringExtra("offset_y")
            ?: intent.getStringExtra("offsetY")
        val offsetY = intent.getIntExtra("offset_y", intent.getIntExtra("offsetY", Int.MIN_VALUE)).let {
            if (it != Int.MIN_VALUE) it else offsetYStr?.toIntOrNull()
        }
        val heightStr = intent.getStringExtra("height")
            ?: intent.getStringExtra("height_dp")
            ?: intent.getStringExtra("heightDp")
        val height = intent.getIntExtra("height", intent.getIntExtra("height_dp", Int.MIN_VALUE)).let {
            if (it != Int.MIN_VALUE) it else heightStr?.toIntOrNull()
        }
        val reset = intent.getBooleanExtra("reset", false) || intent.getStringExtra("reset")?.toBooleanStrictOrNull() == true

        Log.i(TAG, "onReceive: action=$action, id=$id, title=$title, subtitle=$subtitle, priority=$priority, pkg=$pkg")

        val result = handleBroadcastAction(
            action = action,
            id = id,
            title = title,
            subtitle = subtitle,
            priority = priority,
            packageName = pkg,
            alignment = alignStr,
            offsetX = offsetX,
            offsetY = offsetY,
            heightDp = height,
            reset = reset,
            context = context
        )
        Log.i(TAG, "handleBroadcastAction finished with result=$result")
    }

    fun handleBroadcastAction(
        action: String,
        id: String?,
        title: String?,
        subtitle: String?,
        priority: Int,
        packageName: String?,
        alignment: String? = null,
        offsetX: Int? = null,
        offsetY: Int? = null,
        heightDp: Int? = null,
        reset: Boolean = false,
        context: Context? = null
    ): Boolean {
        val registry = CenterpieceHostService.globalRegistry

        return when (action) {
            ACTION_REGISTER_WIDGET -> {
                if (id.isNullOrBlank()) {
                    Log.w(TAG, "REGISTER_WIDGET failed: empty or null 'id'")
                    return false
                }
                val item = CenterpieceItem(
                    id = id,
                    packageName = packageName ?: "unknown",
                    title = title ?: "Widget",
                    subtitle = subtitle,
                    priority = priority,
                    isBuiltIn = false
                )
                val success = registry.registerItem(item)
                Log.i(TAG, "REGISTER_WIDGET success=$success for id=$id")
                success
            }

            ACTION_UPDATE_WIDGET -> {
                if (id.isNullOrBlank()) {
                    Log.w(TAG, "UPDATE_WIDGET failed: empty or null 'id'")
                    return false
                }
                val existing = registry.getItem(id)
                if (existing == null) {
                    Log.i(TAG, "UPDATE_WIDGET: id '$id' not found in registry, registering new item")
                    val item = CenterpieceItem(
                        id = id,
                        packageName = packageName ?: "unknown",
                        title = title ?: "Widget",
                        subtitle = subtitle,
                        priority = priority,
                        isBuiltIn = false
                    )
                    return registry.registerItem(item)
                }
                val updated = existing.copy(
                    title = title ?: existing.title,
                    subtitle = subtitle ?: existing.subtitle,
                    priority = if (priority != 0 && priority != 500) priority else existing.priority
                )
                val success = registry.updateItem(updated)
                Log.i(TAG, "UPDATE_WIDGET success=$success for id=$id")
                success
            }

            ACTION_UNREGISTER_WIDGET -> {
                if (id.isNullOrBlank()) {
                    Log.w(TAG, "UNREGISTER_WIDGET failed: empty or null 'id'")
                    return false
                }
                val success = registry.unregisterItem(id)
                Log.i(TAG, "UNREGISTER_WIDGET success=$success for id=$id")
                success
            }

            ACTION_TOGGLE_SERVICE -> {
                if (context != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastToggleTime < 300) {
                        Log.i(TAG, "Ignoring rapid duplicate TOGGLE_SERVICE")
                        return true
                    }
                    lastToggleTime = now
                    val current = CenterpiecePreferences.isOverlayEnabled(context)
                    val next = !current
                    Log.i(TAG, "TOGGLE_SERVICE: current=$current -> next=$next")
                    CenterpiecePreferences.setOverlayEnabled(context, next)
                    if (next) {
                        CenterpieceHostService.start(context)
                    } else {
                        CenterpieceHostService.stop(context)
                    }
                    true
                } else {
                    false
                }
            }

            ACTION_SHOW_OVERLAY -> {
                if (context != null) {
                    Log.i(TAG, "SHOW_OVERLAY: enabling overlay")
                    CenterpiecePreferences.setOverlayEnabled(context, true)
                    CenterpieceHostService.start(context)
                    true
                } else {
                    false
                }
            }

            ACTION_HIDE_OVERLAY -> {
                if (context != null) {
                    Log.i(TAG, "HIDE_OVERLAY: disabling overlay")
                    CenterpiecePreferences.setOverlayEnabled(context, false)
                    CenterpieceHostService.stop(context)
                    true
                } else {
                    false
                }
            }

            ACTION_SET_LAYOUT -> {
                if (context != null) {
                    if (reset) {
                        Log.i(TAG, "SET_LAYOUT: resetting to defaults")
                        CenterpiecePreferences.resetLayoutConfig(context)
                        return true
                    }
                    val current = CenterpiecePreferences.getLayoutConfig(context)
                    val newAlign = if (!alignment.isNullOrBlank()) BarAlignment.fromString(alignment) else current.alignment
                    val newX = offsetX ?: current.offsetX
                    val newY = offsetY ?: current.offsetY
                    val newH = heightDp ?: current.heightDp
                    val updated = LayoutConfig(
                        alignment = newAlign,
                        offsetX = newX,
                        offsetY = newY,
                        heightDp = newH
                    )
                    Log.i(TAG, "SET_LAYOUT: updated to $updated")
                    CenterpiecePreferences.setLayoutConfig(context, updated)
                    true
                } else {
                    false
                }
            }

            ACTION_TOGGLE_AWAKE -> {
                KeepAwakeManager.toggle()
                true
            }

            ACTION_SET_AWAKE -> {
                KeepAwakeManager.toggle()
                true
            }

            else -> false
        }
    }

    companion object {
        const val TAG = "CenterpieceReceiver"
        private var lastToggleTime = 0L
        const val ACTION_REGISTER_WIDGET = "com.exaclast.centerpiece.REGISTER_WIDGET"
        const val ACTION_UPDATE_WIDGET = "com.exaclast.centerpiece.UPDATE_WIDGET"
        const val ACTION_UNREGISTER_WIDGET = "com.exaclast.centerpiece.UNREGISTER_WIDGET"
        const val ACTION_TOGGLE_SERVICE = "com.exaclast.centerpiece.TOGGLE_SERVICE"
        const val ACTION_SHOW_OVERLAY = "com.exaclast.centerpiece.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.exaclast.centerpiece.HIDE_OVERLAY"
        const val ACTION_SET_LAYOUT = "com.exaclast.centerpiece.SET_LAYOUT"
        const val ACTION_TOGGLE_AWAKE = "com.exaclast.centerpiece.TOGGLE_AWAKE"
        const val ACTION_SET_AWAKE = "com.exaclast.centerpiece.SET_AWAKE"

        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_PACKAGE = "package"
    }
}
