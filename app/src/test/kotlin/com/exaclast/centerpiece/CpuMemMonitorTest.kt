package com.exaclast.centerpiece

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuMemMonitorTest {

    @Test
    fun testParseCpuStatLine() {
        val line = "cpu  100 20 30 800 50 0 0 0 0 0"
        val parsed = CpuMemMonitor.parseProcStatLine(line)
        // total = 100 + 20 + 30 + 800 + 50 = 1000
        // idle = 800 + 50 = 850
        assertEquals(1000L, parsed.totalTime)
        assertEquals(850L, parsed.idleTime)
    }

    @Test
    fun testCalculateCpuLoad() {
        val t1 = CpuSnapshot(totalTime = 1000L, idleTime = 850L)
        // After 1 sec: 200 units of work, 800 units idle -> total = 2000, idle = 1650
        // deltaTotal = 1000, deltaIdle = 800
        // deltaUsage = 200 / 1000 = 20%
        val t2 = CpuSnapshot(totalTime = 2000L, idleTime = 1650L)

        val load = CpuMemMonitor.calculateCpuPercentage(t1, t2)
        assertEquals(20, load)
    }

    @Test
    fun testCalculateCpuidleUsageMatchesKernel() {
        // Simulating 8 cores over 1 second (1,000,000 us * 8 cores = 8,000,000 us total)
        // Idle delta = 6,880,000 us
        // Used delta = 1,120,000 us (14.0% usage)
        val s1 = CpuSnapshot(totalTime = 0L, idleTime = 0L, timestampNs = 0L)
        val s2 = CpuSnapshot(totalTime = 8_000_000L, idleTime = 6_880_000L, timestampNs = 1_000_000_000L)

        val pct = CpuMemMonitor.calculateCpuPercentage(s1, s2)
        assertEquals(14, pct)
    }

    @Test
    fun testParseOnlineCores() {
        assertEquals(8, CpuMemMonitor.parseOnlineCores("0-7"))
        assertEquals(8, CpuMemMonitor.parseOnlineCores("0-3,4-7"))
        assertEquals(1, CpuMemMonitor.parseOnlineCores("0"))
        assertEquals(16, CpuMemMonitor.parseOnlineCores("0-15"))
        assertEquals(6, CpuMemMonitor.parseOnlineCores("0-1,4-7"))
    }

    @Test
    fun testFormatMemory() {
        val totalBytes = 16L * 1024 * 1024 * 1024 // 16 GB
        val availableBytes = 11L * 1024 * 1024 * 1024 // 11 GB free, 5 GB used

        val memInfo = CpuMemMonitor.formatMemoryInfo(totalBytes, availableBytes)
        assertEquals("5.0", memInfo.usedGbStr)
        assertEquals("16.0", memInfo.totalGbStr)
        assertEquals(31, memInfo.usedPercent)
        assertTrue(memInfo.chipLabel.contains("RAM 5.0/16.0G"))
    }
}
