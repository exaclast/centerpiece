package com.exaclast.centerpiece.server

import android.content.Context
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.WidgetRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class CenterpieceServerTest {

    private lateinit var registry: WidgetRegistry
    private lateinit var server: CenterpieceServer
    private val testPort = 7425

    @Before
    fun setUp() {
        registry = WidgetRegistry()
        // Provide mock/stub context for unit testing
        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        server = CenterpieceServer(dummyContext, registry, port = testPort)
        server.start()
        Thread.sleep(150) // Wait for server to bind
    }

    @After
    fun tearDown() {
        server.stop()
        Thread.sleep(100)
    }

    @Test
    fun testHealthEndpointReturnsOk() {
        val (code, body) = httpGet("http://127.0.0.1:$testPort/v1/health")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertEquals("ok", json.getString("status"))
        assertEquals("1.0", json.getString("version"))
    }

    @Test
    fun testRegisterAndGetChipsViaRest() {
        // Register chip via POST
        val postBody = JSONObject().apply {
            put("id", "test_rest_chip")
            put("title", "REST Title")
            put("subtitle", "REST Sub")
            put("priority", 600)
        }.toString()

        val (postCode, postResp) = httpPost("http://127.0.0.1:$testPort/v1/chips", postBody)
        assertEquals(201, postCode)
        val postJson = JSONObject(postResp)
        assertTrue(postJson.getBoolean("success"))
        assertEquals("test_rest_chip", postJson.getString("id"))

        // Verify chip in registry
        val item = registry.getItem("test_rest_chip")
        assertNotNull(item)
        assertEquals("REST Title", item?.title)
        assertEquals("REST Sub", item?.subtitle)
        assertEquals(600, item?.priority)

        // Verify chip appears in GET /v1/chips
        val (getCode, getResp) = httpGet("http://127.0.0.1:$testPort/v1/chips")
        assertEquals(200, getCode)
        val array = JSONArray(getResp)
        var found = false
        for (i in 0 until array.length()) {
            val chip = array.getJSONObject(i)
            if (chip.getString("id") == "test_rest_chip") {
                found = true
                assertEquals("REST Title", chip.getString("title"))
            }
        }
        assertTrue(found)

        // Delete chip via DELETE
        val (delCode, delResp) = httpDelete("http://127.0.0.1:$testPort/v1/chips/test_rest_chip")
        assertEquals(200, delCode)
        val delJson = JSONObject(delResp)
        assertTrue(delJson.getBoolean("success"))

        assertNull(registry.getItem("test_rest_chip"))
    }

    @Test
    fun testLayoutEndpoints() {
        // GET initial layout
        val (getCode, getResp) = httpGet("http://127.0.0.1:$testPort/v1/layout")
        assertEquals(200, getCode)
        val getJson = JSONObject(getResp)
        assertTrue(getJson.has("alignment"))
        assertTrue(getJson.has("offsetX"))
        assertTrue(getJson.has("offsetY"))
        assertTrue(getJson.has("heightDp"))

        // Update layout via POST
        val updatePayload = JSONObject().apply {
            put("alignment", "LEFT")
            put("offsetX", 12)
            put("offsetY", 3)
            put("heightDp", 40)
        }.toString()

        val (postCode, postResp) = httpPost("http://127.0.0.1:$testPort/v1/layout", updatePayload)
        assertEquals(200, postCode)
        val postJson = JSONObject(postResp)
        assertTrue(postJson.getBoolean("success"))
        assertEquals("LEFT", postJson.getString("alignment"))
        assertEquals(12, postJson.getInt("offsetX"))
        assertEquals(3, postJson.getInt("offsetY"))
        assertEquals(40, postJson.getInt("heightDp"))

        // Verify with GET
        val (get2Code, get2Resp) = httpGet("http://127.0.0.1:$testPort/v1/layout")
        assertEquals(200, get2Code)
        val get2Json = JSONObject(get2Resp)
        assertEquals("LEFT", get2Json.getString("alignment"))
        assertEquals(12, get2Json.getInt("offsetX"))
        assertEquals(3, get2Json.getInt("offsetY"))
        assertEquals(40, get2Json.getInt("heightDp"))

        // Reset layout
        val resetPayload = JSONObject().apply {
            put("reset", true)
        }.toString()
        val (resetCode, resetResp) = httpPost("http://127.0.0.1:$testPort/v1/layout", resetPayload)
        assertEquals(200, resetCode)
        val resetJson = JSONObject(resetResp)
        assertTrue(resetJson.getBoolean("reset"))

        val (get3Code, get3Resp) = httpGet("http://127.0.0.1:$testPort/v1/layout")
        assertEquals(200, get3Code)
        val get3Json = JSONObject(get3Resp)
        assertEquals("CENTER", get3Json.getString("alignment"))
        assertEquals(0, get3Json.getInt("offsetX"))
        assertEquals(0, get3Json.getInt("offsetY"))
        assertEquals(0, get3Json.getInt("heightDp"))
    }

    private fun httpGet(urlStr: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        return Pair(code, text)
    }

    private fun httpPost(urlStr: String, json: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        conn.outputStream.use { os ->
            OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(json) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        return Pair(code, text)
    }

    private fun httpDelete(urlStr: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        return Pair(code, text)
    }
}
