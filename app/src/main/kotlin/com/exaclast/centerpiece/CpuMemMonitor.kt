package com.exaclast.centerpiece

import android.app.ActivityManager
import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Snapshot of CPU times from /proc/stat or kernel sysfs cpuidle.
 * Both totalTime and idleTime are in arbitrary consistent units (kernel ticks or microseconds).
 */
data class CpuSnapshot(
    val totalTime: Long,
    val idleTime: Long,
    val timestampNs: Long = System.nanoTime()
)

/**
 * Formatted memory statistics for display.
 */
data class MemoryDisplayInfo(
    val usedBytes: Long,
    val totalBytes: Long,
    val usedGbStr: String,
    val totalGbStr: String,
    val usedPercent: Int,
    val chipLabel: String
)

/**
 * Real system monitor reading live CPU and Memory stats from the Linux kernel.
 * On modern Android (10+) where /proc/stat is restricted by SELinux,
 * reads kernel cpuidle metrics (/sys/devices/system/cpu/cpu* /cpuidle/state* /time)
 * which accurately measures true CPU utilization matching Task Manager.
 */
object CpuMemMonitor {

    private var lastSnapshot: CpuSnapshot? = null
    private var lastCpuPercent: Int = 12

    private var cachedCpuidleFiles: List<File>? = null
    private var lastCpuidleScanTime: Long = 0L

    /**
     * Resets internal snapshot state (useful for unit testing).
     */
    fun resetForTesting(initialSnapshot: CpuSnapshot? = null, initialPercent: Int = 12) {
        lastSnapshot = initialSnapshot
        lastCpuPercent = initialPercent
        cachedCpuidleFiles = null
        lastCpuidleScanTime = 0L
    }

    /**
     * Parses a `/proc/stat` "cpu  ..." line into total time and idle time.
     */
    fun parseProcStatLine(line: String): CpuSnapshot {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 5 && parts[0] == "cpu") {
            val user = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val system = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            val idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

            val total = user + nice + system + idle + iowait + irq + softirq + steal
            val idleAll = idle + iowait
            return CpuSnapshot(totalTime = total, idleTime = idleAll)
        }
        return CpuSnapshot(totalTime = 0L, idleTime = 0L)
    }

    /**
     * Parses kernel `/sys/devices/system/cpu/online` core specifier (e.g. "0-7" or "0-3,4-7").
     */
    fun parseOnlineCores(onlineStr: String): Int {
        var count = 0
        val ranges = onlineStr.trim().split(",")
        for (range in ranges) {
            val bounds = range.split("-")
            if (bounds.size == 2) {
                val start = bounds[0].toIntOrNull() ?: continue
                val end = bounds[1].toIntOrNull() ?: continue
                if (end >= start) count += (end - start + 1)
            } else if (bounds.size == 1) {
                if (bounds[0].toIntOrNull() != null) count++
            }
        }
        return if (count > 0) count else Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    /**
     * Resolves the number of online CPU cores from sysfs or JVM fallback.
     */
    fun getOnlineCoreCount(): Int {
        try {
            val onlineFile = File("/sys/devices/system/cpu/online")
            if (onlineFile.exists() && onlineFile.canRead()) {
                val text = onlineFile.readText().trim()
                if (text.isNotEmpty()) {
                    return parseOnlineCores(text)
                }
            }
        } catch (_: Exception) {}
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    /**
     * Discovers all readable state time files across all CPU cores in sysfs.
     * Caches the list for up to 60 seconds to avoid filesystem directory churn.
     */
    fun getCpuidleFiles(forceRefresh: Boolean = false): List<File> {
        val now = System.currentTimeMillis()
        val cached = cachedCpuidleFiles
        if (!forceRefresh && cached != null && cached.isNotEmpty() && (now - lastCpuidleScanTime) < 60_000L) {
            return cached
        }

        val files = mutableListOf<File>()
        val cpuBase = File("/sys/devices/system/cpu")
        val cpuDirs = cpuBase.listFiles { f -> f.isDirectory && f.name.matches(Regex("^cpu\\d+$")) }
        if (cpuDirs != null) {
            for (cpuDir in cpuDirs) {
                val idleDir = File(cpuDir, "cpuidle")
                if (idleDir.exists() && idleDir.isDirectory) {
                    val stateDirs = idleDir.listFiles { f -> f.isDirectory && f.name.startsWith("state") } ?: continue
                    for (stateDir in stateDirs) {
                        val timeFile = File(stateDir, "time")
                        if (timeFile.exists() && timeFile.canRead()) {
                            files.add(timeFile)
                        }
                    }
                }
            }
        }

        cachedCpuidleFiles = files
        lastCpuidleScanTime = now
        return files
    }

    /**
     * Calculates CPU utilization percentage between two snapshots.
     */
    fun calculateCpuPercentage(prev: CpuSnapshot, curr: CpuSnapshot): Int {
        val deltaTotal = curr.totalTime - prev.totalTime
        val deltaIdle = curr.idleTime - prev.idleTime
        if (deltaTotal <= 0) return 0
        val deltaUsed = (deltaTotal - deltaIdle).coerceAtLeast(0L)
        val pct = Math.round((deltaUsed.toDouble() * 100.0) / deltaTotal.toDouble()).toInt()
        return pct.coerceIn(0, 100)
    }

    /**
     * Reads current /proc/stat snapshot from the kernel if accessible.
     */
    fun readProcStatSnapshot(): CpuSnapshot {
        try {
            val file = File("/proc/stat")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    val line = reader.readLine()
                    if (line != null) {
                        return parseProcStatLine(line)
                    }
                }
            }
        } catch (_: Exception) {}
        return CpuSnapshot(0L, 0L)
    }

    /**
     * Reads current cpuidle snapshot from sysfs across all CPU cores.
     */
    fun readCpuidleSnapshot(): CpuSnapshot {
        val files = getCpuidleFiles()
        if (files.isEmpty()) return CpuSnapshot(0L, 0L)

        var totalIdleUs = 0L
        for (file in files) {
            try {
                val text = file.readText().trim()
                val us = text.toLongOrNull() ?: continue
                totalIdleUs += us
            } catch (_: Exception) {}
        }

        val cores = getOnlineCoreCount()
        val nowNs = System.nanoTime()
        val nowUs = nowNs / 1000L
        val totalTimeUs = nowUs * cores

        return CpuSnapshot(totalTime = totalTimeUs, idleTime = totalIdleUs, timestampNs = nowNs)
    }

    /**
     * Reads current CPU snapshot either from /proc/stat or sysfs cpuidle.
     */
    fun readCurrentCpuSnapshot(): CpuSnapshot {
        val stat = readProcStatSnapshot()
        if (stat.totalTime > 0) return stat
        return readCpuidleSnapshot()
    }

    /**
     * Samples CPU usage percentage. Uses /proc/stat if accessible;
     * otherwise computes true kernel CPU utilization across cores via sysfs cpuidle.
     */
    fun sampleCpuUsage(): Int {
        val current = readCurrentCpuSnapshot()
        if (current.totalTime <= 0L) {
            return lastCpuPercent
        }

        val prev = lastSnapshot
        if (prev == null) {
            lastSnapshot = current
            return lastCpuPercent
        }

        // Debounce rapid calls (< 150ms) to prevent microsecond jitter
        val elapsedNs = current.timestampNs - prev.timestampNs
        if (elapsedNs < 150_000_000L && lastCpuPercent >= 0) {
            return lastCpuPercent
        }

        val pct = calculateCpuPercentage(prev, current)
        lastSnapshot = current
        lastCpuPercent = pct
        return pct
    }

    /**
     * Fallback delegate to cpuidle snapshot.
     */
    fun sampleCpuFromSysfs(): Int {
        return sampleCpuUsage()
    }

    /**
     * Formats memory values into human-readable gigabytes and percentage string.
     */
    fun formatMemoryInfo(totalBytes: Long, availableBytes: Long): MemoryDisplayInfo {
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val gbDivider = 1024.0 * 1024.0 * 1024.0
        val usedGb = usedBytes / gbDivider
        val totalGb = totalBytes / gbDivider
        val usedPercent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        val usedStr = String.format(Locale.US, "%.1f", usedGb)
        val totalStr = String.format(Locale.US, "%.1f", totalGb)
        val label = "RAM $usedStr/${totalStr}G ($usedPercent%)"

        return MemoryDisplayInfo(
            usedBytes = usedBytes,
            totalBytes = totalBytes,
            usedGbStr = usedStr,
            totalGbStr = totalStr,
            usedPercent = usedPercent,
            chipLabel = label
        )
    }

    /**
     * Reads system RAM metrics using ActivityManager.MemoryInfo.
     */
    fun readCurrentMemoryInfo(context: Context): MemoryDisplayInfo {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                return formatMemoryInfo(memInfo.totalMem, memInfo.availMem)
            }
        } catch (_: Exception) {}
        return formatMemoryInfo(0L, 0L)
    }
}
