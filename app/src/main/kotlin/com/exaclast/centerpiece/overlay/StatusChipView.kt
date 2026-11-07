package com.exaclast.centerpiece.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.R

/**
 * High-performance status bar chip rendering a modular widget item in the Centerpiece top bar.
 * Matches the Googlebook OS desktop Material 3 status bar media chip geometry and styling.
 */
class StatusChipView(context: Context) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private val titleView = TextView(context)
    private var customRemoteView: View? = null

    var currentItem: CenterpieceItem? = null
        private set

    private var isChipActive: Boolean = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip)
        isClickable = true
        isFocusable = false

        val density = context.resources.displayMetrics.density
        val iconSize = (16 * density).toInt()
        val iconMarginEnd = (6 * density).toInt()

        val iconParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = iconMarginEnd
        }
        iconView.layoutParams = iconParams
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.setColorFilter(Color.parseColor("#FFFFFF"))
        addView(iconView)

        titleView.setTextColor(Color.parseColor("#F2F2F2"))
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        titleView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.fontFeatureSettings = "tnum"
        addView(titleView)
    }

    /**
     * Toggles active selection visual state when the popover dropdown is opened/closed,
     * mirroring the native Googlebook OS status bar media chip behavior.
     */
    fun setActive(active: Boolean) {
        isChipActive = active
        if (active) {
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_active)
            titleView.setTextColor(Color.parseColor("#001D35"))
            iconView.setColorFilter(Color.parseColor("#001D35"))
        } else {
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip)
            titleView.setTextColor(Color.parseColor("#F2F2F2"))
            iconView.setColorFilter(Color.parseColor("#FFFFFF"))
        }
    }

    /**
     * Binds the status item data and updates visuals.
     */
    fun bind(item: CenterpieceItem) {
        currentItem = item

        // Check if item supplies a custom RemoteViews micro-UI for the chip
        if (item.remoteViews != null) {
            iconView.visibility = GONE
            titleView.visibility = GONE

            customRemoteView?.let { removeView(it) }
            customRemoteView = null

            try {
                val inflated = item.remoteViews.apply(context, this)
                customRemoteView = inflated
                addView(inflated)
                return
            } catch (e: Exception) {
                Log.e("StatusChipView", "Failed to inflate RemoteViews for ${item.id}", e)
            }
        }

        // Remove custom RemoteView if reverting to standard item
        customRemoteView?.let {
            removeView(it)
            customRemoteView = null
        }

        // Icon handling
        if (item.iconBitmap != null) {
            iconView.setImageBitmap(item.iconBitmap)
            iconView.visibility = VISIBLE
        } else if (item.iconRes != 0) {
            try {
                iconView.setImageResource(item.iconRes)
                iconView.visibility = VISIBLE
            } catch (_: Exception) {
                iconView.visibility = GONE
            }
        } else {
            iconView.visibility = GONE
        }

        // Formats title and subtitle gracefully on a single line matching native chips
        val displayText = if (!item.subtitle.isNullOrEmpty() && !item.title.contains("·")) {
            "${item.title} · ${item.subtitle}"
        } else {
            item.title
        }

        val density = context.resources.displayMetrics.density
        val iconParams = iconView.layoutParams as LayoutParams

        if (displayText.isNullOrBlank()) {
            titleView.text = ""
            titleView.visibility = GONE
            titleView.minWidth = 0
            iconParams.marginEnd = 0
            // Symmetric horizontal padding for icon-only chips (7dp left and right)
            setPadding((7 * density).toInt(), (3 * density).toInt(), (7 * density).toInt(), (3 * density).toInt())
        } else {
            titleView.text = displayText
            titleView.visibility = VISIBLE
            iconParams.marginEnd = (6 * density).toInt()
            setPadding((9 * density).toInt(), (3 * density).toInt(), (11 * density).toInt(), (3 * density).toInt())

            // Consistent width stabilization to eliminate digit jumping
            if (item.id == "builtin.sysmon") {
                titleView.gravity = Gravity.CENTER
                titleView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                val benchmarkText = when {
                    displayText.contains("·") -> "CPU 100% · RAM 100%"
                    displayText.startsWith("CPU") -> "CPU 100%"
                    displayText.startsWith("RAM") -> "RAM 100%"
                    else -> "CPU 100% · RAM 100%"
                }
                val minTextWidth = kotlin.math.ceil(titleView.paint.measureText(benchmarkText)).toInt() + (4 * density).toInt()
                titleView.minWidth = minTextWidth
            } else if (item.id == "builtin.awake") {
                titleView.gravity = Gravity.CENTER
                titleView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                val benchmarkText = if (displayText == "∞") "∞" else "100m"
                val minTextWidth = kotlin.math.ceil(titleView.paint.measureText(benchmarkText)).toInt() + (2 * density).toInt()
                titleView.minWidth = minTextWidth
            } else {
                titleView.gravity = Gravity.START
                titleView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                titleView.minWidth = 0
            }
        }
        iconView.layoutParams = iconParams

        setActive(isChipActive)
    }
}
