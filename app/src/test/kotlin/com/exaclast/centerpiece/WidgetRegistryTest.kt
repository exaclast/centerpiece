package com.exaclast.centerpiece

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetRegistryTest {

    private lateinit var registry: WidgetRegistry

    @Before
    fun setUp() {
        registry = WidgetRegistry()
    }

    @Test
    fun testRegisterAndRetrieve() {
        val item = CenterpieceItem(
            id = "test.item.1",
            packageName = "com.test.app",
            title = "Test Item",
            subtitle = "Active",
            priority = 10
        )

        val registered = registry.registerItem(item, null)
        assertTrue(registered)

        val retrieved = registry.getItem("test.item.1")
        assertNotNull(retrieved)
        assertEquals("Test Item", retrieved?.title)
        assertEquals("Active", retrieved?.subtitle)
        assertEquals(1, registry.getAllItems().size)
    }

    @Test
    fun testSortingByPriorityDescending() {
        val low = CenterpieceItem(id = "low", packageName = "p", title = "Low", priority = 1)
        val high = CenterpieceItem(id = "high", packageName = "p", title = "High", priority = 100)
        val med = CenterpieceItem(id = "med", packageName = "p", title = "Medium", priority = 50)

        registry.registerItem(low, null)
        registry.registerItem(high, null)
        registry.registerItem(med, null)

        val items = registry.getAllItems()
        assertEquals(3, items.size)
        assertEquals("high", items[0].id)
        assertEquals("med", items[1].id)
        assertEquals("low", items[2].id)
    }

    @Test
    fun testUpdateItem() {
        val item = CenterpieceItem(id = "item1", packageName = "p", title = "V1", priority = 5)
        registry.registerItem(item, null)

        val updated = CenterpieceItem(id = "item1", packageName = "p", title = "V2", subtitle = "New", priority = 5)
        val success = registry.updateItem(updated)
        assertTrue(success)

        val retrieved = registry.getItem("item1")
        assertEquals("V2", retrieved?.title)
        assertEquals("New", retrieved?.subtitle)
    }

    @Test
    fun testUnregisterItem() {
        val item = CenterpieceItem(id = "item1", packageName = "p", title = "V1")
        registry.registerItem(item, null)
        assertEquals(1, registry.getAllItems().size)

        val removed = registry.unregisterItem("item1")
        assertTrue(removed)
        assertNull(registry.getItem("item1"))
        assertEquals(0, registry.getAllItems().size)

        // Removing non-existent returns false
        assertFalse(registry.unregisterItem("item1"))
    }
}
