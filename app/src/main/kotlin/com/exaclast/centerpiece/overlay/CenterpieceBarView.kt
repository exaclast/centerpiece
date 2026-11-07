package com.exaclast.centerpiece.overlay

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.SafeCenterBounds

/**
 * Top-level overlay container view positioned in the status bar center.
 * Houses modular chips and enforces safe bounds to prevent corner clashing.
 * Efficiently updates chips in-place and preserves active highlight states.
 */
class CenterpieceBarView(
    context: Context,
    private val onChipClicked: (item: CenterpieceItem, anchorView: StatusChipView) -> Unit
) : FrameLayout(context) {

    private val chipsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private var currentBounds: SafeCenterBounds? = null

    /**
     * ID of the currently active item whose popover is open.
     */
    var activeItemId: String? = null
        set(value) {
            field = value
            updateActiveStates()
        }

    init {
        val density = context.resources.displayMetrics.density
        val padHorizontal = (8 * density).toInt()
        chipsContainer.setPadding(padHorizontal, 0, padHorizontal, 0)

        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        addView(chipsContainer, params)
    }

    /**
     * Updates the safe center boundaries for width clamping.
     */
    fun updateSafeBounds(bounds: SafeCenterBounds) {
        currentBounds = bounds
        requestLayout()
    }

    /**
     * Finds the StatusChipView corresponding to a specific item ID.
     */
    fun findChipView(itemId: String): StatusChipView? {
        for (i in 0 until chipsContainer.childCount) {
            val chip = chipsContainer.getChildAt(i) as? StatusChipView ?: continue
            if (chip.currentItem?.id == itemId) {
                return chip
            }
        }
        return null
    }

    /**
     * Updates active visual state across all child chips.
     */
    private fun updateActiveStates() {
        for (i in 0 until chipsContainer.childCount) {
            val chip = chipsContainer.getChildAt(i) as? StatusChipView ?: continue
            val isActive = chip.currentItem?.id != null && chip.currentItem?.id == activeItemId
            chip.setActive(isActive)
        }
    }

    /**
     * Rebinds the list of status items into interactive chips.
     * Reuses existing views where possible to prevent visual flicker and retain active states.
     */
    fun submitItems(items: List<CenterpieceItem>) {
        val density = context.resources.displayMetrics.density
        val chipMargin = (4 * density).toInt()

        // Index existing chip views by item ID
        val existingChips = mutableMapOf<String, StatusChipView>()
        for (i in 0 until chipsContainer.childCount) {
            val chip = chipsContainer.getChildAt(i) as? StatusChipView ?: continue
            val id = chip.currentItem?.id ?: continue
            existingChips[id] = chip
        }

        chipsContainer.removeAllViews()

        for (item in items) {
            val chip = existingChips[item.id] ?: StatusChipView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(chipMargin, 0, chipMargin, 0)
                }
            }

            chip.bind(item)
            val isActive = item.id == activeItemId
            chip.setActive(isActive)
            chip.setOnClickListener {
                onChipClicked(item, chip)
            }

            chipsContainer.addView(chip)
        }

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounds = currentBounds
        if (bounds != null && bounds.maxCenterWidth > 0) {
            val maxAllowedWidth = bounds.maxCenterWidth
            val maxWidthSpec = MeasureSpec.makeMeasureSpec(maxAllowedWidth, MeasureSpec.AT_MOST)
            super.onMeasure(maxWidthSpec, heightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
