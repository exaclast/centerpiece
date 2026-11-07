package com.exaclast.centerpiece

import com.exaclast.centerpiece.receiver.CenterpieceWidgetReceiver
import com.exaclast.centerpiece.service.CenterpieceHostService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetReceiverTest {

    private lateinit var receiver: CenterpieceWidgetReceiver

    @Before
    fun setUp() {
        receiver = CenterpieceWidgetReceiver()
    }

    @Test
    fun testRegisterBroadcastUpdatesRegistry() {
        val success = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_REGISTER_WIDGET,
            id = "test_bcast_widget",
            title = "BcastTitle",
            subtitle = "BcastSub",
            priority = 42,
            packageName = "com.test.app"
        )
        assertTrue(success)

        val item = CenterpieceHostService.globalRegistry.getItem("test_bcast_widget")
        assertNotNull(item)
        assertEquals("BcastTitle", item?.title)
        assertEquals("BcastSub", item?.subtitle)
        assertEquals(42, item?.priority)

        // Clean up
        CenterpieceHostService.globalRegistry.unregisterItem("test_bcast_widget")
    }

    @Test
    fun testUpdateAndUnregisterBroadcast() {
        // First register
        receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_REGISTER_WIDGET,
            id = "upd_widget",
            title = "OldTitle",
            subtitle = null,
            priority = 10,
            packageName = "com.test.app"
        )

        // Then update
        val updatedSuccess = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_UPDATE_WIDGET,
            id = "upd_widget",
            title = "NewTitle",
            subtitle = "Updated",
            priority = 10,
            packageName = "com.test.app"
        )
        assertTrue(updatedSuccess)

        val updated = CenterpieceHostService.globalRegistry.getItem("upd_widget")
        assertEquals("NewTitle", updated?.title)
        assertEquals("Updated", updated?.subtitle)

        // Then unregister
        val unregSuccess = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_UNREGISTER_WIDGET,
            id = "upd_widget",
            title = null,
            subtitle = null,
            priority = 0,
            packageName = null
        )
        assertTrue(unregSuccess)

        assertNull(CenterpieceHostService.globalRegistry.getItem("upd_widget"))
    }

    @Test
    fun testInvalidActionOrEmptyIdReturnsFalse() {
        val result = receiver.handleBroadcastAction(
            action = "INVALID_ACTION",
            id = "test",
            title = null,
            subtitle = null,
            priority = 0,
            packageName = null
        )
        assertFalse(result)

        val emptyIdResult = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_REGISTER_WIDGET,
            id = "",
            title = "Title",
            subtitle = null,
            priority = 0,
            packageName = null
        )
        assertFalse(emptyIdResult)
    }

    @Test
    fun testUpdateNonExistentWidgetRegistersIt() {
        val updateResult = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_UPDATE_WIDGET,
            id = "auto_reg_widget",
            title = "AutoRegistered",
            subtitle = "Active",
            priority = 400,
            packageName = "com.test"
        )
        assertTrue(updateResult)

        val item = CenterpieceHostService.globalRegistry.getItem("auto_reg_widget")
        assertNotNull(item)
        assertEquals("AutoRegistered", item?.title)

        CenterpieceHostService.globalRegistry.unregisterItem("auto_reg_widget")
    }

    @Test
    fun testSetLayoutBroadcastAction() {
        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
        val result = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_SET_LAYOUT,
            id = null,
            title = null,
            subtitle = null,
            priority = 0,
            packageName = null,
            alignment = "RIGHT",
            offsetX = 20,
            offsetY = -2,
            heightDp = 38,
            context = dummyContext
        )
        assertTrue(result)
        val config = CenterpiecePreferences.getLayoutConfig(dummyContext)
        assertEquals(BarAlignment.RIGHT, config.alignment)
        assertEquals(20, config.offsetX)
        assertEquals(-2, config.offsetY)
        assertEquals(38, config.heightDp)

        // Test reset
        val resetResult = receiver.handleBroadcastAction(
            action = CenterpieceWidgetReceiver.ACTION_SET_LAYOUT,
            id = null,
            title = null,
            subtitle = null,
            priority = 0,
            packageName = null,
            reset = true,
            context = dummyContext
        )
        assertTrue(resetResult)
        val resetConfig = CenterpiecePreferences.getLayoutConfig(dummyContext)
        assertEquals(BarAlignment.CENTER, resetConfig.alignment)
        assertEquals(0, resetConfig.offsetX)
        assertEquals(0, resetConfig.offsetY)
        assertEquals(0, resetConfig.heightDp)
    }
}
