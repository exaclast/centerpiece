package com.exaclast.centerpiece.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.exaclast.centerpiece.AwakeMode
import com.exaclast.centerpiece.AwakeState
import com.exaclast.centerpiece.CenterpieceAction
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.CpuMemMonitor
import com.exaclast.centerpiece.KeepAwakeManager
import com.exaclast.centerpiece.MainActivity
import com.exaclast.centerpiece.PopoverPositioner
import com.exaclast.centerpiece.R
import com.exaclast.centerpiece.SafeInsetHelper
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.SystemMonitorCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages floating popover sheets anchored directly beneath status items.
 * Uses a secondary WindowManager overlay styled to match the native Googlebook OS desktop media dialog.
 */
class PopoverCoordinator(
    private val context: Context,
    private val windowManager: WindowManager,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val onActionTriggered: (itemId: String, actionId: String, action: CenterpieceAction) -> Unit,
    private val onDismissed: (itemId: String) -> Unit
) {

    private var activePopoverView: View? = null
    var activeItemId: String? = null
        private set
    var lastDismissedItemId: String? = null
        private set
    var lastDismissedTime: Long = 0L
        private set
    private var activeAnchorView: StatusChipView? = null
    private var liveSysmonJob: Job? = null
    private var liveAwakeJob: Job? = null

    val isShowing: Boolean
        get() = activePopoverView != null

    /**
     * Shows a popover anchored beneath the specified chip view.
     */
    fun showPopover(item: CenterpieceItem, anchorView: StatusChipView) {
        dismiss()
        lastDismissedItemId = null
        lastDismissedTime = 0L

        activeItemId = item.id
        activeAnchorView = anchorView
        anchorView.setActive(true)

        val density = context.resources.displayMetrics.density
        val popoverWidth = (370 * density).toInt()

        // Create root popover card container
        val rootCard = object : LinearLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_popover_card)
            elevation = 16 * density
        }

        populatePopoverContent(rootCard, item)

        // Measure content height
        rootCard.measure(
            View.MeasureSpec.makeMeasureSpec(popoverWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredHeight = rootCard.measuredHeight

        // Calculate screen coordinates
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height

        val screenWidth = SafeInsetHelper.getScreenWidth(context)
        val screenHeight = SafeInsetHelper.getScreenHeight(context)

        val position = PopoverPositioner.calculatePosition(
            anchorX = anchorX,
            anchorY = anchorY,
            anchorWidth = anchorWidth,
            anchorHeight = anchorHeight,
            popoverWidth = popoverWidth,
            popoverHeight = measuredHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            margin = (12 * density).toInt(),
            gap = (8 * density).toInt()
        )

        val layoutParams = WindowManager.LayoutParams(
            popoverWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
            windowAnimations = android.R.style.Animation_Dialog
        }

        try {
            android.util.Log.d("Centerpiece", "Adding popover window at x=${position.x}, y=${position.y}")
            windowManager.addView(rootCard, layoutParams)
            activePopoverView = rootCard
            rootCard.post {
                rootCard.requestFocus()
            }
        } catch (e: Exception) {
            android.util.Log.e("Centerpiece", "Failed to add popover window", e)
        }
    }

    /**
     * Dismisses the active popover window.
     */
    fun dismiss() {
        liveSysmonJob?.cancel()
        liveSysmonJob = null
        liveAwakeJob?.cancel()
        liveAwakeJob = null

        val currentView = activePopoverView
        val currentId = activeItemId
        val currentAnchor = activeAnchorView

        currentAnchor?.setActive(false)
        activeAnchorView = null

        if (currentView != null) {
            lastDismissedItemId = currentId
            lastDismissedTime = android.os.SystemClock.uptimeMillis()
            try {
                windowManager.removeView(currentView)
            } catch (_: Exception) {
            }
            activePopoverView = null
            activeItemId = null
            if (currentId != null) {
                onDismissed(currentId)
            }
        }
    }

    private fun populatePopoverContent(container: LinearLayout, item: CenterpieceItem) {
        val density = context.resources.displayMetrics.density

        // --- Header ---
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * density).toInt())
        }

        val headerIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt()).apply {
                marginEnd = (10 * density).toInt()
            }
            if (item.iconBitmap != null) {
                setImageBitmap(item.iconBitmap)
            } else if (item.iconRes != 0) {
                setImageResource(item.iconRes)
            }
            setColorFilter(Color.parseColor("#FFFFFF"))
        }
        header.addView(headerIcon)

        val headerText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            text = when (item.id) {
                "builtin.awake" -> "Keep Screen Awake"
                "builtin.logo" -> "Centerpiece"
                else -> if (item.title.isNotBlank()) item.title else "Centerpiece"
            }
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        headerText.addView(titleView)

        if (!item.subtitle.isNullOrEmpty()) {
            val subtitleView = TextView(context).apply {
                text = item.subtitle
                setTextColor(Color.parseColor("#C4C7C5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            headerText.addView(subtitleView)
        }
        header.addView(headerText)
        container.addView(header)

        // --- Specialized Content according to Item ID or Custom RemoteViews ---
        if (item.popoverRemoteViews != null) {
            try {
                val customPopover = item.popoverRemoteViews.apply(context, container)
                container.addView(customPopover)
                return
            } catch (e: Exception) {
                android.util.Log.e("Centerpiece", "Failed to apply custom popover RemoteViews for ${item.id}", e)
            }
        }

        when (item.id) {
            "builtin.logo" -> populateLogoContent(container)
            "builtin.sysmon" -> populateSysmonContent(container)
            "builtin.awake" -> populateAwakeContent(container)
            else -> populateGenericActions(container, item)
        }
    }

    private fun populateLogoContent(container: LinearLayout) {
        val density = context.resources.displayMetrics.density

        val infoCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_popover_inner_card)
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        val desc = TextView(context).apply {
            text = "Googlebook OS Desktop Status Bar Utility\nActive on Layer 30 (Above Status Bar)\nZero-ADB REST Daemon: 0.0.0.0:7420"
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setLineSpacing(4 * density, 1f)
        }
        infoCard.addView(desc)
        container.addView(infoCard)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (14 * density).toInt(), 0, 0)
        }

        val openSettingsBtn = Button(context).apply {
            text = "Open Dashboard Settings"
            setTextColor(Color.parseColor("#001D35"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button_primary)
            stateListAnimator = null
            elevation = 0f
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                dismiss()
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            }
        }
        btnRow.addView(openSettingsBtn)
        container.addView(btnRow)
    }

    private fun populateSysmonContent(container: LinearLayout) {
        val density = context.resources.displayMetrics.density

        // CPU section
        val cpuLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        container.addView(cpuLabel)

        val cpuBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (6 * density).toInt()
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, (14 * density).toInt())
            }
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.progress_sysmon)
            max = 100
        }
        container.addView(cpuBar)

        // RAM section
        val ramLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        container.addView(ramLabel)

        val ramBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (6 * density).toInt()
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, (18 * density).toInt())
            }
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.progress_sysmon)
            max = 100
        }
        container.addView(ramBar)

        fun updateMeters() {
            val cpuUsage = CpuMemMonitor.sampleCpuUsage()
            val memInfo = CpuMemMonitor.readCurrentMemoryInfo(context)
            cpuLabel.text = "CPU Load: $cpuUsage%"
            cpuBar.progress = cpuUsage
            ramLabel.text = "RAM Usage: ${memInfo.usedGbStr} GB / ${memInfo.totalGbStr} GB (${memInfo.usedPercent}%)"
            ramBar.progress = memInfo.usedPercent
        }

        updateMeters()

        liveSysmonJob?.cancel()
        liveSysmonJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(2000)
                updateMeters()
            }
        }

        // Metric display mode selector
        val optionsLabel = TextView(context).apply {
            text = "Show in Status Bar"
            setTextColor(Color.parseColor("#8E918F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        container.addView(optionsLabel)

        val optionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
        }

        val modes = listOf(
            "Both" to SysmonMetricMode.BOTH,
            "CPU Only" to SysmonMetricMode.CPU_ONLY,
            "RAM Only" to SysmonMetricMode.RAM_ONLY
        )

        val modeButtons = mutableListOf<Button>()

        fun updateModeButtons() {
            val currentMode = CenterpiecePreferences.getSysmonMode(context)
            modes.forEachIndexed { index, (_, mode) ->
                val btn = modeButtons.getOrNull(index) ?: return@forEachIndexed
                val isSelected = mode == currentMode
                if (isSelected) {
                    btn.background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button_primary)
                    btn.setTextColor(Color.parseColor("#001D35"))
                } else {
                    btn.background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button)
                    btn.setTextColor(Color.parseColor("#E3E3E3"))
                }
            }
        }

        modes.forEach { (label, mode) ->
            val btn = Button(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                stateListAnimator = null
                elevation = 0f
                setPadding((4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0)
                }
                setOnClickListener {
                    CenterpiecePreferences.setSysmonMode(context, mode)
                    SystemMonitorCoordinator.updateSysmon(context)
                    updateModeButtons()
                }
            }
            modeButtons.add(btn)
            optionsRow.addView(btn)
        }

        updateModeButtons()
        container.addView(optionsRow)

        // Open Task Manager shortcut
        val openTaskManagerBtn = Button(context).apply {
            text = "Open Task Manager"
            setTextColor(Color.parseColor("#001D35"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button_primary)
            stateListAnimator = null
            elevation = 0f
            setPadding(0, (11 * density).toInt(), 0, (11 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                dismiss()
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage("com.google.android.factory.taskmanager")
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        setClassName("com.google.android.factory.taskmanager", "com.google.android.factory.taskmanager.MainActivity")
                    }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(fallbackIntent)
                    } catch (_: Exception) {}
                }
            }
        }
        container.addView(openTaskManagerBtn)
    }

    private fun populateAwakeContent(container: LinearLayout) {
        val density = context.resources.displayMetrics.density

        // Status Card
        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_popover_inner_card)
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        val statusHeader = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        statusCard.addView(statusHeader)

        val statusDesc = TextView(context).apply {
            text = "Prevents display timeout and system idle sleep on Googlebook OS."
            setTextColor(Color.parseColor("#C4C7C5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        statusCard.addView(statusDesc)
        container.addView(statusCard)

        // Main Toggle Button
        val toggleBtn = Button(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            stateListAnimator = null
            elevation = 0f
            setPadding(0, (11 * density).toInt(), 0, (11 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * density).toInt()
            }
        }
        container.addView(toggleBtn)

        // Preset Duration Row
        val presetLabel = TextView(context).apply {
            text = "Set Awake Duration"
            setTextColor(Color.parseColor("#8E918F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, (14 * density).toInt(), 0, (6 * density).toInt())
        }
        container.addView(presetLabel)

        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val presets = listOf(
            "15m" to 15,
            "30m" to 30,
            "1 hour" to 60,
            "Infinite" to 0
        )

        fun updateUI(state: AwakeState) {
            if (state.isAwake) {
                val durationText = when (state.mode) {
                    AwakeMode.INDEFINITE -> "Indefinite"
                    AwakeMode.TIMED -> {
                        val mins = state.remainingSeconds / 60
                        val secs = state.remainingSeconds % 60
                        String.format("%02d:%02d remaining", mins, secs)
                    }
                    AwakeMode.OFF -> ""
                }
                statusHeader.text = "Screen Awake: ACTIVE ($durationText)"
                statusHeader.setTextColor(Color.parseColor("#D3E3FD"))

                toggleBtn.text = "Turn Off Keep Awake"
                toggleBtn.setTextColor(Color.parseColor("#FFB4AB"))
                toggleBtn.background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button_danger)
            } else {
                statusHeader.text = "Screen Awake: DISABLED"
                statusHeader.setTextColor(Color.parseColor("#8E918F"))

                toggleBtn.text = "Turn On (Indefinite)"
                toggleBtn.setTextColor(Color.parseColor("#001D35"))
                toggleBtn.background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button_primary)
            }
        }

        toggleBtn.setOnClickListener {
            KeepAwakeManager.toggle()
            updateUI(KeepAwakeManager.state.value)
        }

        for ((idx, preset) in presets.withIndex()) {
            val (name, minutes) = preset
            val btn = Button(context).apply {
                text = name
                setTextColor(Color.parseColor("#F2F2F2"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button)
                stateListAnimator = null
                elevation = 0f
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx > 0) marginStart = (4 * density).toInt()
                }
                setOnClickListener {
                    if (minutes > 0) {
                        KeepAwakeManager.enableTimed(minutes)
                    } else {
                        KeepAwakeManager.enableIndefinite()
                    }
                    updateUI(KeepAwakeManager.state.value)
                }
            }
            presetRow.addView(btn)
        }
        container.addView(presetRow)

        updateUI(KeepAwakeManager.state.value)

        liveAwakeJob?.cancel()
        liveAwakeJob = CoroutineScope(Dispatchers.Main).launch {
            KeepAwakeManager.state.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun populateGenericActions(container: LinearLayout, item: CenterpieceItem) {
        val density = context.resources.displayMetrics.density

        if (item.actions.isEmpty()) {
            val emptyMsg = TextView(context).apply {
                text = "No additional actions available"
                setTextColor(Color.parseColor("#8E918F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            container.addView(emptyMsg)
            return
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val actionList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (action in item.actions) {
            val actionBtn = Button(context).apply {
                text = action.label
                setTextColor(Color.parseColor("#E3E3E3"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = ContextCompat.getDrawable(context, R.drawable.bg_popover_button)
                stateListAnimator = null
                elevation = 0f
                setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
                }
                setOnClickListener {
                    dismiss()
                    if (action.pendingIntent != null) {
                        try {
                            action.pendingIntent.send()
                        } catch (_: Exception) {
                        }
                    }
                    onActionTriggered(item.id, action.id, action)
                }
            }
            actionList.addView(actionBtn)
        }
        scroll.addView(actionList)
        container.addView(scroll)
    }
}
