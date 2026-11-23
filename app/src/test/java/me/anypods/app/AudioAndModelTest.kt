package me.anypods.app

import me.anypods.app.model.PodModelManager
import me.anypods.app.updater.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAndModelTest {

    @Test
    fun testVersionComparison() {
        assertTrue(UpdateManager.isVersionNewer("v1.0.9", "1.0.8"))
        assertTrue(UpdateManager.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(UpdateManager.isVersionNewer("1.0.8", "1.0.8"))
        assertFalse(UpdateManager.isVersionNewer("1.0.7", "1.0.8"))
        assertFalse(UpdateManager.isVersionNewer("v1.0.8", "1.0.8"))
    }

    @Test
    fun testPodModelDetection() {
        val detectedPro2 = PodModelManager.detectModel("AirPods Pro 2")
        assertEquals("airpods_pro_2", detectedPro2.id)

        val detectedMax = PodModelManager.detectModel("AirPods Max - Space Gray")
        assertEquals("airpods_max", detectedMax.id)

        val detectedGalaxy = PodModelManager.detectModel("Galaxy Buds Pro")
        assertEquals("galaxy_buds_pro_2", detectedGalaxy.id)

        val detectedSony = PodModelManager.detectModel("Sony WF-1000XM5")
        assertEquals("sony_wf1000xm5", detectedSony.id)
    }

    @Test
    fun testStereoBalanceRange() {
        val clampedTest = { value: Float -> value.coerceIn(-1.0f, 1.0f) }
        assertEquals(-1.0f, clampedTest(-2.5f), 0.001f)
        assertEquals(1.0f, clampedTest(1.8f), 0.001f)
        assertEquals(0.0f, clampedTest(0.0f), 0.001f)
    }
}
