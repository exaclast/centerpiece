package com.exaclast.centerpiece.server

import android.content.Context
import android.util.Log
import com.exaclast.centerpiece.AppFilterMode
import com.exaclast.centerpiece.AppVisibilityCoordinator
import com.exaclast.centerpiece.BarAlignment
import com.exaclast.centerpiece.CenterpieceItem
import com.exaclast.centerpiece.CenterpiecePreferences
import com.exaclast.centerpiece.CpuMemMonitor
import com.exaclast.centerpiece.KeepAwakeManager
import com.exaclast.centerpiece.LayoutConfig
import com.exaclast.centerpiece.SafeInsetHelper
import com.exaclast.centerpiece.SysmonMetricMode
import com.exaclast.centerpiece.SystemMonitorCoordinator
import com.exaclast.centerpiece.WidgetRegistry
import com.exaclast.centerpiece.service.CenterpieceHostService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Lightweight, zero-dependency REST HTTP/1.1 micro-daemon running inside Centerpiece.
 * Exposes a desktop REST interface on port 7420 to allow instant scripting,
 * Linux VM commands, curl, web extensions, and CLI pipelines without ADB.
 */
class CenterpieceServer(
    private val context: Context,
    private val registry: WidgetRegistry,
    val port: Int = DEFAULT_PORT
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false

    fun start() {
        if (isRunning) return

        acceptJob = serverScope.launch {
            try {
                // Listen on all network interfaces (0.0.0.0) so both Android host and Linux VM can connect
                val socket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                serverSocket = socket
                Log.i(TAG, "Centerpiece REST Daemon listening on port $port (0.0.0.0:$port)")

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch {
                            handleClient(client)
                        }
                    } catch (e: SocketException) {
                        if (!socket.isClosed) {
                            Log.w(TAG, "Socket accept exception: ${e.message}")
                        }
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected client connection error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CenterpieceServer on port $port: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        serverScope.cancel()
        Log.i(TAG, "Centerpiece REST Daemon stopped")
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        client.use { socket ->
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(output, 400, "Bad Request", "text/plain", "Invalid HTTP request")
                return@withContext
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
                line = reader.readLine()
            }

            // Handle CORS preflight
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", "")
                return@withContext
            }

            // Read request body if Content-Length present
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(buf, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                String(buf, 0, readTotal)
            } else {
                ""
            }

            try {
                routeRequest(method, path, body, output)
            } catch (e: Exception) {
                Log.e(TAG, "Error routing request $method $path", e)
                try {
                    sendJson(output, 500, "Internal Server Error", JSONObject().put("error", e.message ?: "Unknown error"))
                } catch (_: Exception) {}
            }
        }
    }

    private fun routeRequest(method: String, path: String, body: String, output: OutputStream) {
        when {
            // Health & Status
            method == "GET" && (path == "/v1/health" || path == "/v1/status" || path == "/") -> {
                val json = JSONObject().apply {
                    put("status", "ok")
                    put("version", "1.0")
                    put("overlayEnabled", CenterpiecePreferences.isOverlayEnabled(context))
                    put("itemsCount", registry.getAllItems().size)
                }
                sendJson(output, 200, "OK", json)
            }

            // List all chips
            method == "GET" && path == "/v1/chips" -> {
                val array = JSONArray()
                for (item in registry.getAllItems()) {
                    val itemJson = JSONObject().apply {
                        put("id", item.id)
                        put("packageName", item.packageName)
                        put("title", item.title)
                        put("subtitle", item.subtitle ?: JSONObject.NULL)
                        put("priority", item.priority)
                        put("isBuiltIn", item.isBuiltIn)
                    }
                    array.put(itemJson)
                }
                sendJson(output, 200, "OK", array)
            }

            // Register or replace chip
            method == "POST" && path == "/v1/chips" -> {
                try {
                    val json = JSONObject(body)
                    val id = json.optString("id").takeIf { it.isNotBlank() }
                    val title = json.optString("title").takeIf { it.isNotBlank() }

                    if (id == null || title == null) {
                        sendJson(output, 400, "Bad Request", JSONObject().put("error", "Missing required fields 'id' and 'title'"))
                        return
                    }

                    val subtitle = json.optString("subtitle").takeIf { it.isNotBlank() }
                    val priority = json.optInt("priority", 500)
                    val pkg = json.optString("packageName", "cli")

                    val item = CenterpieceItem(
                        id = id,
                        packageName = pkg,
                        title = title,
                        subtitle = subtitle,
                        priority = priority,
                        isBuiltIn = false
                    )
                    registry.registerItem(item)
                    sendJson(output, 201, "Created", JSONObject().put("success", true).put("id", id))
                } catch (e: Exception) {
                    sendJson(output, 400, "Bad Request", JSONObject().put("error", "Invalid JSON: ${e.message}"))
                }
            }

            // Update chip
            (method == "PUT" || method == "POST") && path.startsWith("/v1/chips/") -> {
                val rawId = path.removePrefix("/v1/chips/")
                val id = URLDecoder.decode(rawId, "UTF-8")
                val existing = registry.getItem(id)

                if (existing == null) {
                    sendJson(output, 404, "Not Found", JSONObject().put("error", "Chip not found: $id"))
                    return
                }

                try {
                    val json = JSONObject(body)
                    val newTitle = json.optString("title").takeIf { it.isNotBlank() } ?: existing.title
                    val newSub = if (json.has("subtitle")) {
                        json.optString("subtitle").takeIf { it.isNotBlank() }
                    } else {
                        existing.subtitle
                    }
                    val newPriority = json.optInt("priority", existing.priority)

                    val updated = existing.copy(
                        title = newTitle,
                        subtitle = newSub,
                        priority = newPriority
                    )
                    registry.updateItem(updated)
                    sendJson(output, 200, "OK", JSONObject().put("success", true).put("id", id))
                } catch (e: Exception) {
                    sendJson(output, 400, "Bad Request", JSONObject().put("error", "Invalid JSON: ${e.message}"))
                }
            }

            // Delete chip
            method == "DELETE" && path.startsWith("/v1/chips/") -> {
                val rawId = path.removePrefix("/v1/chips/")
                val id = URLDecoder.decode(rawId, "UTF-8")
                val success = registry.unregisterItem(id)
                sendJson(output, 200, "OK", JSONObject().put("success", success).put("id", id))
            }

            // Master Overlay Controls
            method == "POST" && path == "/v1/toggle" -> {
                val current = CenterpiecePreferences.isOverlayEnabled(context)
                val next = !current
                CenterpiecePreferences.setOverlayEnabled(context, next)
                if (next) {
                    CenterpieceHostService.start(context)
                } else {
                    CenterpieceHostService.stop(context)
                }
                sendJson(output, 200, "OK", JSONObject().put("enabled", next))
            }

            method == "POST" && path == "/v1/show" -> {
                CenterpiecePreferences.setOverlayEnabled(context, true)
                CenterpieceHostService.start(context)
                sendJson(output, 200, "OK", JSONObject().put("enabled", true))
            }

            method == "POST" && path == "/v1/hide" -> {
                CenterpiecePreferences.setOverlayEnabled(context, false)
                CenterpieceHostService.stop(context)
                sendJson(output, 200, "OK", JSONObject().put("enabled", false))
            }

            // Layout & Positioning Customization
            method == "GET" && path == "/v1/layout" -> {
                val config = CenterpiecePreferences.getLayoutConfig(context)
                val density = try {
                    context.resources?.displayMetrics?.density ?: 1.625f
                } catch (_: Exception) {
                    1.625f
                }
                val statusBarHeight = SafeInsetHelper.getStatusBarHeight(context)
                val effectiveHeight = if (config.heightDp > 0) (config.heightDp * density).toInt() else statusBarHeight

                val json = JSONObject().apply {
                    put("alignment", config.alignment.name)
                    put("offsetX", config.offsetX)
                    put("offsetY", config.offsetY)
                    put("heightDp", config.heightDp)
                    put("effectiveHeightPx", effectiveHeight)
                }
                sendJson(output, 200, "OK", json)
            }

            (method == "POST" || method == "PUT") && path == "/v1/layout" -> {
                try {
                    val json = JSONObject(body)
                    if (json.optBoolean("reset", false)) {
                        CenterpiecePreferences.resetLayoutConfig(context)
                        sendJson(output, 200, "OK", JSONObject().put("success", true).put("reset", true))
                        return
                    }

                    val current = CenterpiecePreferences.getLayoutConfig(context)
                    val newAlign = if (json.has("alignment")) {
                        BarAlignment.fromString(json.getString("alignment"))
                    } else if (json.has("align")) {
                        BarAlignment.fromString(json.getString("align"))
                    } else {
                        current.alignment
                    }

                    val newX = if (json.has("offsetX")) json.getInt("offsetX") else {
                        if (json.has("offset_x")) json.getInt("offset_x") else current.offsetX
                    }
                    val newY = if (json.has("offsetY")) json.getInt("offsetY") else {
                        if (json.has("offset_y")) json.getInt("offset_y") else current.offsetY
                    }
                    val newH = if (json.has("heightDp")) json.getInt("heightDp") else {
                        if (json.has("height_dp")) json.getInt("height_dp") else {
                            if (json.has("height")) json.getInt("height") else current.heightDp
                        }
                    }

                    val updated = LayoutConfig(
                        alignment = newAlign,
                        offsetX = newX,
                        offsetY = newY,
                        heightDp = newH
                    )
                    CenterpiecePreferences.setLayoutConfig(context, updated)
                    sendJson(output, 200, "OK", JSONObject().apply {
                        put("success", true)
                        put("alignment", updated.alignment.name)
                        put("offsetX", updated.offsetX)
                        put("offsetY", updated.offsetY)
                        put("heightDp", updated.heightDp)
                    })
                } catch (e: Exception) {
                    sendJson(output, 400, "Bad Request", JSONObject().put("error", "Invalid layout JSON: ${e.message}"))
                }
            }

            // System Monitor Controls
            method == "GET" && path == "/v1/sysmon" -> {
                val mode = CenterpiecePreferences.getSysmonMode(context)
                val cpuUsage = CpuMemMonitor.sampleCpuUsage()
                val memInfo = CpuMemMonitor.readCurrentMemoryInfo(context)
                val json = JSONObject().apply {
                    put("mode", mode.name)
                    put("cpuPercent", cpuUsage)
                    put("ramUsedGb", memInfo.usedGbStr)
                    put("ramTotalGb", memInfo.totalGbStr)
                    put("ramPercent", memInfo.usedPercent)
                }
                sendJson(output, 200, "OK", json)
            }

            (method == "POST" || method == "PUT") && path == "/v1/sysmon" -> {
                val json = if (body.isNotBlank()) {
                    try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                } else JSONObject()

                val modeStr = json.optString("mode", "").ifEmpty { json.optString("action", "") }
                val newMode = SysmonMetricMode.fromString(modeStr)
                CenterpiecePreferences.setSysmonMode(context, newMode)
                SystemMonitorCoordinator.updateSysmon(context)

                val cpuUsage = CpuMemMonitor.sampleCpuUsage()
                val memInfo = CpuMemMonitor.readCurrentMemoryInfo(context)
                val resp = JSONObject().apply {
                    put("success", true)
                    put("mode", newMode.name)
                    put("cpuPercent", cpuUsage)
                    put("ramUsedGb", memInfo.usedGbStr)
                    put("ramTotalGb", memInfo.totalGbStr)
                    put("ramPercent", memInfo.usedPercent)
                }
                sendJson(output, 200, "OK", resp)
            }

            // Keep Awake Controls
            method == "GET" && path == "/v1/awake" -> {
                val state = KeepAwakeManager.state.value
                val json = JSONObject().apply {
                    put("mode", state.mode.name)
                    put("isAwake", state.isAwake)
                    put("remainingSeconds", state.remainingSeconds)
                    put("initialDurationMinutes", state.initialDurationMinutes)
                }
                sendJson(output, 200, "OK", json)
            }

            (method == "POST" || method == "PUT") && path == "/v1/awake" -> {
                val json = if (body.isNotBlank()) {
                    try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                } else JSONObject()

                val action = json.optString("action", "").lowercase()
                val minutes = json.optInt("minutes", 0)

                when {
                    action == "toggle" -> KeepAwakeManager.toggle()
                    action == "on" || action == "enable" -> {
                        if (minutes > 0) {
                            KeepAwakeManager.enableTimed(minutes)
                        } else {
                            KeepAwakeManager.enableIndefinite()
                        }
                    }
                    action == "off" || action == "disable" -> KeepAwakeManager.disable()
                    minutes > 0 -> KeepAwakeManager.enableTimed(minutes)
                    else -> KeepAwakeManager.toggle()
                }

                val state = KeepAwakeManager.state.value
                val resp = JSONObject().apply {
                    put("success", true)
                    put("mode", state.mode.name)
                    put("isAwake", state.isAwake)
                    put("remainingSeconds", state.remainingSeconds)
                }
                sendJson(output, 200, "OK", resp)
            }

            // App Visibility & Fullscreen Policies
            method == "GET" && path.startsWith("/v1/visibility") -> {
                val json = JSONObject().apply {
                    put("hideInFullscreen", CenterpiecePreferences.isHideInFullscreen(context))
                    put("filterMode", CenterpiecePreferences.getAppFilterMode(context).name)
                    val pkgs = JSONArray()
                    CenterpiecePreferences.getAppFilterPackages(context).forEach { pkgs.put(it) }
                    put("filterPackages", pkgs)
                    put("isFullscreen", AppVisibilityCoordinator.isAppFullscreenFlow.value)
                    put("foregroundPackage", AppVisibilityCoordinator.currentForegroundPackageFlow.value ?: JSONObject.NULL)
                    put("isCenterpieceVisible", AppVisibilityCoordinator.isOverlayVisibleFlow.value)
                    put("hideReason", AppVisibilityCoordinator.hideReasonFlow.value ?: JSONObject.NULL)
                }
                sendJson(output, 200, "OK", json)
            }

            (method == "POST" || method == "PUT") && path.startsWith("/v1/visibility") -> {
                val json = if (body.isNotBlank()) {
                    try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                } else JSONObject()

                // Check fullscreen toggle
                if (json.has("hideInFullscreen")) {
                    val hide = json.getBoolean("hideInFullscreen")
                    CenterpiecePreferences.setHideInFullscreen(context, hide)
                } else if (json.has("fullscreen")) {
                    val fs = json.optString("fullscreen").lowercase()
                    val hide = fs == "on" || fs == "true" || fs == "1"
                    CenterpiecePreferences.setHideInFullscreen(context, hide)
                }

                // Check mode
                if (json.has("mode")) {
                    val m = AppFilterMode.fromString(json.getString("mode"))
                    CenterpiecePreferences.setAppFilterMode(context, m)
                }

                // Check add
                val addPkg = json.optString("add").takeIf { it.isNotBlank() }
                if (addPkg != null) {
                    CenterpiecePreferences.addAppFilterPackage(context, addPkg)
                }

                // Check remove
                val removePkg = json.optString("remove").takeIf { it.isNotBlank() }
                if (removePkg != null) {
                    CenterpiecePreferences.removeAppFilterPackage(context, removePkg)
                }

                if (json.optBoolean("clear", false)) {
                    CenterpiecePreferences.setAppFilterPackages(context, emptySet())
                }

                AppVisibilityCoordinator.evaluateFromWindows()

                val resp = JSONObject().apply {
                    put("success", true)
                    put("hideInFullscreen", CenterpiecePreferences.isHideInFullscreen(context))
                    put("filterMode", CenterpiecePreferences.getAppFilterMode(context).name)
                    val pkgs = JSONArray()
                    CenterpiecePreferences.getAppFilterPackages(context).forEach { pkgs.put(it) }
                    put("filterPackages", pkgs)
                    put("isFullscreen", AppVisibilityCoordinator.isAppFullscreenFlow.value)
                    put("foregroundPackage", AppVisibilityCoordinator.currentForegroundPackageFlow.value ?: JSONObject.NULL)
                    put("isCenterpieceVisible", AppVisibilityCoordinator.isOverlayVisibleFlow.value)
                    put("hideReason", AppVisibilityCoordinator.hideReasonFlow.value ?: JSONObject.NULL)
                }
                sendJson(output, 200, "OK", resp)
            }

            else -> {
                sendJson(output, 404, "Not Found", JSONObject().put("error", "Endpoint not found: $method $path"))
            }
        }
    }

    private fun sendJson(output: OutputStream, statusCode: Int, statusText: String, json: Any) {
        val jsonStr = json.toString()
        sendResponse(output, statusCode, statusText, "application/json; charset=utf-8", jsonStr)
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        content: String
    ) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        if (bytes.isNotEmpty()) {
            output.write(bytes)
        }
        output.flush()
    }

    companion object {
        const val TAG = "CenterpieceServer"
        const val DEFAULT_PORT = 7420

        @Volatile
        private var activeServer: CenterpieceServer? = null

        fun startGlobalServer(context: Context, registry: WidgetRegistry, port: Int = DEFAULT_PORT): CenterpieceServer {
            synchronized(this) {
                activeServer?.let {
                    if (it.isRunning && it.port == port) return it
                    it.stop()
                }
                val newServer = CenterpieceServer(context.applicationContext, registry, port)
                newServer.start()
                activeServer = newServer
                return newServer
            }
        }

        fun stopGlobalServer() {
            synchronized(this) {
                activeServer?.stop()
                activeServer = null
            }
        }
    }
}
