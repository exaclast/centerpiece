package com.exaclast.centerpiece

import android.os.RemoteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry that stores, updates, and orders all active Centerpiece status bar items.
 */
class WidgetRegistry {

    private val itemsMap = ConcurrentHashMap<String, CenterpieceItem>()
    private val callbacksMap = ConcurrentHashMap<String, ICenterpieceCallback>()

    private val _itemsFlow = MutableStateFlow<List<CenterpieceItem>>(emptyList())
    val itemsFlow: StateFlow<List<CenterpieceItem>> = _itemsFlow.asStateFlow()

    /**
     * Registers a new status item. Updates if the item already exists.
     */
    fun registerItem(item: CenterpieceItem, callback: ICenterpieceCallback? = null): Boolean {
        itemsMap[item.id] = item
        if (callback != null) {
            callbacksMap[item.id] = callback
        }
        publishSortedItems()
        return true
    }

    /**
     * Updates an existing status item. Returns false if the item was not registered.
     */
    fun updateItem(item: CenterpieceItem): Boolean {
        if (!itemsMap.containsKey(item.id)) {
            return false
        }
        itemsMap[item.id] = item
        publishSortedItems()
        return true
    }

    /**
     * Unregisters a status item by its unique ID.
     */
    fun unregisterItem(itemId: String): Boolean {
        val removed = itemsMap.remove(itemId) != null
        callbacksMap.remove(itemId)
        if (removed) {
            publishSortedItems()
        }
        return removed
    }

    /**
     * Returns an item by ID, or null if not registered.
     */
    fun getItem(itemId: String): CenterpieceItem? = itemsMap[itemId]

    /**
     * Returns all registered items ordered by priority descending, then by title ascending.
     */
    fun getAllItems(): List<CenterpieceItem> {
        return itemsMap.values
            .sortedWith(
                compareByDescending<CenterpieceItem> { it.priority }
                    .thenBy { it.id }
            )
    }

    /**
     * Dispatches item click event to the associated IPC callback if registered.
     */
    fun dispatchItemClick(itemId: String) {
        val callback = callbacksMap[itemId] ?: return
        try {
            callback.onItemClicked(itemId)
        } catch (e: RemoteException) {
            // Dead object; clean up stale callback
            callbacksMap.remove(itemId)
        }
    }

    /**
     * Dispatches action click event inside a popover sheet to the associated IPC callback.
     */
    fun dispatchActionClick(itemId: String, actionId: String) {
        val callback = callbacksMap[itemId] ?: return
        try {
            callback.onActionClicked(itemId, actionId)
        } catch (e: RemoteException) {
            callbacksMap.remove(itemId)
        }
    }

    /**
     * Dispatches popover dismissed event to the associated IPC callback.
     */
    fun dispatchPopoverDismissed(itemId: String) {
        val callback = callbacksMap[itemId] ?: return
        try {
            callback.onPopoverDismissed(itemId)
        } catch (e: RemoteException) {
            callbacksMap.remove(itemId)
        }
    }

    private fun publishSortedItems() {
        _itemsFlow.value = getAllItems()
    }
}
