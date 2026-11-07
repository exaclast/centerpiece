package com.exaclast.centerpiece

import android.graphics.Rect
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class AppVisibilityCoordinatorTest {

    @Test
    fun isTrueFullscreen_distinguishesMaximizedFromFullscreen() {
        val screenWidth = 2880
        val screenHeight = 1800

        // 1. Maximized desktop window on Googlebook OS: bounded below status bar (y >= 59)
        assertFalse(
            "Maximized window should NOT be identified as true fullscreen",
            AppVisibilityCoordinator.isTrueFullscreen(0, 59, 2880, 1709, screenWidth, screenHeight)
        )

        // 2. Freeform floating window
        assertFalse(
            "Freeform window should NOT be identified as true fullscreen",
            AppVisibilityCoordinator.isTrueFullscreen(200, 150, 1600, 1100, screenWidth, screenHeight)
        )

        // 3. True immersive fullscreen window (video/game) covering y=0 to y=1800
        assertTrue(
            "Full display window covering status bar should be identified as true fullscreen",
            AppVisibilityCoordinator.isTrueFullscreen(0, 0, 2880, 1800, screenWidth, screenHeight)
        )
    }

    @Test
    fun computeVisibility_fullscreenRules() {
        val homePackages = setOf("com.google.android.apps.nexuslauncher")
        val selfPkg = "com.exaclast.centerpiece"

        // Case A: App is fullscreen, hideInFullscreen is true -> Hidden
        val (visibleA, reasonA) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = true,
            isAppFullscreen = true,
            filterMode = AppFilterMode.DISABLED,
            filterPackages = emptySet(),
            foregroundPackage = "com.google.android.youtube",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertFalse(visibleA)
        assertEquals("fullscreen", reasonA)

        // Case B: App is fullscreen, but hideInFullscreen is false -> Visible
        val (visibleB, reasonB) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = true,
            filterMode = AppFilterMode.DISABLED,
            filterPackages = emptySet(),
            foregroundPackage = "com.google.android.youtube",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertTrue(visibleB)
        assertEquals(null, reasonB)

        // Case C: App is maximized (not fullscreen), hideInFullscreen is true -> Visible
        val (visibleC, reasonC) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = true,
            isAppFullscreen = false,
            filterMode = AppFilterMode.DISABLED,
            filterPackages = emptySet(),
            foregroundPackage = "com.android.chrome",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertTrue(visibleC)
        assertEquals(null, reasonC)
    }

    @Test
    fun computeVisibility_blacklistRules() {
        val homePackages = setOf("com.google.android.apps.nexuslauncher")
        val selfPkg = "com.exaclast.centerpiece"
        val blacklist = setOf("com.twodboy.wog2", "org.krita")

        // Case A: Foreground app is in blacklist -> Hidden
        val (visibleA, reasonA) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = false,
            filterMode = AppFilterMode.BLACKLIST,
            filterPackages = blacklist,
            foregroundPackage = "com.twodboy.wog2",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertFalse(visibleA)
        assertTrue(reasonA?.startsWith("blacklisted") == true)

        // Case B: Foreground app is NOT in blacklist -> Visible
        val (visibleB, reasonB) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = false,
            filterMode = AppFilterMode.BLACKLIST,
            filterPackages = blacklist,
            foregroundPackage = "com.android.chrome",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertTrue(visibleB)
        assertEquals(null, reasonB)
    }

    @Test
    fun computeVisibility_whitelistRules() {
        val homePackages = setOf("com.google.android.apps.nexuslauncher")
        val selfPkg = "com.exaclast.centerpiece"
        val whitelist = setOf("com.android.chrome", "com.android.virtualization.terminal")

        // Case A: Foreground app IS in whitelist -> Visible
        val (visibleA, reasonA) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = false,
            filterMode = AppFilterMode.WHITELIST,
            filterPackages = whitelist,
            foregroundPackage = "com.android.chrome",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertTrue(visibleA)
        assertEquals(null, reasonA)

        // Case B: Foreground app is NOT in whitelist -> Hidden
        val (visibleB, reasonB) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = false,
            filterMode = AppFilterMode.WHITELIST,
            filterPackages = whitelist,
            foregroundPackage = "com.random.app",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertFalse(visibleB)
        assertTrue(reasonB?.startsWith("not_whitelisted") == true)

        // Case C: Desktop Launcher with whitelist mode -> Still Visible
        val (visibleC, reasonC) = AppVisibilityCoordinator.computeVisibility(
            isOverlayEnabled = true,
            isHideInFullscreen = false,
            isAppFullscreen = false,
            filterMode = AppFilterMode.WHITELIST,
            filterPackages = whitelist,
            foregroundPackage = "com.google.android.apps.nexuslauncher",
            homePackages = homePackages,
            selfPackage = selfPkg
        )
        assertTrue(visibleC)
        assertEquals(null, reasonC)
    }
}
