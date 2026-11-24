package me.anypods.app

import me.anypods.app.audio.AncIntensity
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.audio.FrequencyTuningMode
import me.anypods.app.audio.SpatialAudioMode
import me.anypods.app.audio.ToneIntensity
import me.anypods.app.audio.ToneProfile
import me.anypods.app.bluetooth.AppleBeaconParser
import me.anypods.app.model.PodModelManager
import me.anypods.app.updater.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun testPodModelDetectionComprehensive() {
        // Test all generations and naming variations
        assertEquals("airpods_max", PodModelManager.detectModel("AirPods Max - Space Gray").id)
        assertEquals("airpods_max", PodModelManager.detectModel("Shaurya's AirPods Max").id)

        assertEquals("airpods_pro_2", PodModelManager.detectModel("AirPods Pro 2").id)
        assertEquals("airpods_pro_2", PodModelManager.detectModel("AirPods Pro (2nd generation)").id)
        assertEquals("airpods_pro_2", PodModelManager.detectModel("Alex’s AirPods Pro 2nd Gen").id)

        assertEquals("airpods_pro_1", PodModelManager.detectModel("AirPods Pro").id)
        assertEquals("airpods_pro_1", PodModelManager.detectModel("AirPods Pro (1st generation)").id)
        assertEquals("airpods_pro_1", PodModelManager.detectModel("John's AirPods Pro - Find My").id)

        assertEquals("airpods_4_anc", PodModelManager.detectModel("AirPods 4 ANC").id)
        assertEquals("airpods_4_anc", PodModelManager.detectModel("AirPods 4 with Active Noise Cancellation").id)

        assertEquals("airpods_4", PodModelManager.detectModel("AirPods 4").id)
        assertEquals("airpods_4", PodModelManager.detectModel("AirPods (4th generation)").id)

        assertEquals("airpods_3", PodModelManager.detectModel("AirPods 3").id)
        assertEquals("airpods_3", PodModelManager.detectModel("AirPods (3rd generation)").id)
        assertEquals("airpods_3", PodModelManager.detectModel("Shaurya’s AirPods 3rd Gen").id)

        assertEquals("airpods_2", PodModelManager.detectModel("AirPods 2").id)
        assertEquals("airpods_2", PodModelManager.detectModel("AirPods (2nd generation)").id)

        assertEquals("airpods_1", PodModelManager.detectModel("AirPods 1").id)
        assertEquals("airpods_1", PodModelManager.detectModel("AirPods (1st generation)").id)

        // General fallback
        assertEquals("airpods_pro_2", PodModelManager.detectModel("Shaurya's AirPods").id)

        // Other brands
        assertEquals("galaxy_buds_3_pro", PodModelManager.detectModel("Galaxy Buds 3 Pro").id)
        assertEquals("galaxy_buds_pro_2", PodModelManager.detectModel("Galaxy Buds Pro").id)
        assertEquals("pixel_buds_a", PodModelManager.detectModel("Pixel Buds A-Series").id)
        assertEquals("pixel_buds_pro", PodModelManager.detectModel("Pixel Buds Pro").id)
        assertEquals("sony_wf1000xm5", PodModelManager.detectModel("Sony WF-1000XM5").id)
        assertEquals("beats_studio_buds", PodModelManager.detectModel("Beats Studio Buds").id)

        // All popular brands
        assertEquals("boat_airdopes", PodModelManager.detectModel("boAt Airdopes 141").id)
        assertEquals("boat_airdopes", PodModelManager.detectModel("boAt Nirvana Ion").id)
        assertEquals("noise_buds", PodModelManager.detectModel("Noise Buds VS104").id)
        assertEquals("boult_buds", PodModelManager.detectModel("Boult Audio Airbass Z40").id)
        assertEquals("jbl_tune", PodModelManager.detectModel("JBL Tune 230NC TWS").id)
        assertEquals("bose_qc", PodModelManager.detectModel("Bose QuietComfort Earbuds").id)
        assertEquals("realme_buds", PodModelManager.detectModel("realme Buds Air 5 Pro").id)
        assertEquals("xiaomi_buds", PodModelManager.detectModel("Redmi Buds 5").id)
        assertEquals("soundcore_liberty", PodModelManager.detectModel("Soundcore Liberty 4 NC").id)
        assertEquals("sennheiser_momentum", PodModelManager.detectModel("Sennheiser Momentum True Wireless 4").id)
        assertEquals("skullcandy_buds", PodModelManager.detectModel("Skullcandy Dime 3").id)
        assertEquals("marshall_minor", PodModelManager.detectModel("Marshall Minor IV").id)
        assertEquals("oppo_enco", PodModelManager.detectModel("OPPO Enco Air 3").id)
        assertEquals("vivo_tws", PodModelManager.detectModel("vivo TWS 3e").id)
        assertEquals("huawei_freebuds", PodModelManager.detectModel("HUAWEI FreeBuds 5i").id)
        assertEquals("cmf_buds", PodModelManager.detectModel("CMF Buds Pro").id)
        assertEquals("nothing_ear_2", PodModelManager.detectModel("Nothing Ear (2)").id)

        // Color & Universal Fallback - Any unrecognized pod is properly mapped to universal TWS
        assertEquals("tws_white", PodModelManager.detectModel("White Bluetooth Earphones").id)
        assertEquals("tws_black", PodModelManager.detectModel("Stealth Black Earbuds").id)
        assertEquals("universal_tws", PodModelManager.detectModel("Custom Chinese TWS Pods").id)
        assertEquals("universal_tws", PodModelManager.detectModel("Wireless Headset 5.3").id)
    }

    @Test
    fun testAppleBeaconDecoding() {
        // Sample standard AirPods Pro 1 beacon (ID 0E20)
        // 07 19 01 0E 20 15 A9 B6 01 ...
        // Index breakdown:
        // 0-3: 0719
        // 4-5: 01
        // 6-9: 0E20 (AirPods Pro 1)
        // 10: 1 (flip bit check: 1 & 2 == 0 -> flip is true!)
        // 11: 5 (in-ear status)
        // 12: A (10 -> 100%)
        // 13: 9 (9 -> 95%)
        // 14: 1 (charging status bit 0: charge left)
        // 15: B (11 -> disconnected or 6 for case)
        val testHexPro1 = "0719010E2015A916"
        val beaconPro1 = AppleBeaconParser.parseHex(testHexPro1)
        assertNotNull(beaconPro1)
        assertEquals("airpods_pro_1", beaconPro1?.modelId)

        // Test AirPods Pro 2 (ID 1420)
        val testHexPro2 = "071901142000A908"
        val beaconPro2 = AppleBeaconParser.parseHex(testHexPro2)
        assertNotNull(beaconPro2)
        assertEquals("airpods_pro_2", beaconPro2?.modelId)

        // Test AirPods 3 (ID 1320)
        val testHex3 = "071901132000A808"
        val beacon3 = AppleBeaconParser.parseHex(testHex3)
        assertNotNull(beacon3)
        assertEquals("airpods_3", beacon3?.modelId)

        // Test AirPods 4 ANC (ID 1C20)
        val testHex4Anc = "0719011C2000A908"
        val beacon4Anc = AppleBeaconParser.parseHex(testHex4Anc)
        assertNotNull(beacon4Anc)
        assertEquals("airpods_4_anc", beacon4Anc?.modelId)

        // Test AirPods Max (ID 0A20)
        val testHexMax = "0719010A2000A908"
        val beaconMax = AppleBeaconParser.parseHex(testHexMax)
        assertNotNull(beaconMax)
        assertEquals("airpods_max", beaconMax?.modelId)
    }

    @Test
    fun testAirPodsFrequencyTuningCurves() {
        // Verify that in ANC mode, mid frequencies (e.g. 1000 Hz) are NOT cut/destroyed!
        val ancMidGain = AudioEnhancer.calculateTargetGain(
            centerFreqHz = 1000,
            modelId = "airpods_pro_2",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "ANC",
            ancIntensity = AncIntensity.ULTRA,
            antiHiss = true,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.MODERATE,
            spatialMode = SpatialAudioMode.OFF
        )
        // Mid frequencies must remain positive or neutral (> 0), preserving vocal clarity
        assertTrue("ANC mode must not hollow out vocals! Mid gain: $ancMidGain", ancMidGain >= 0.05f)

        // Verify AirPods 1/2 open-fit bass compensation below 120 Hz
        val openFitBassGain = AudioEnhancer.calculateTargetGain(
            centerFreqHz = 80,
            modelId = "airpods_2",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            antiHiss = false,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.SLIGHT,
            spatialMode = SpatialAudioMode.OFF
        )
        assertTrue("AirPods 2 open fit requires strong low shelf (> +0.3f): $openFitBassGain", openFitBassGain > 0.35f)

        // Verify AirPods Pro 2 Audiophile micro-detail treble presence at 7000 Hz
        val pro2TrebleGain = AudioEnhancer.calculateTargetGain(
            centerFreqHz = 7000,
            modelId = "airpods_pro_2",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            antiHiss = false,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.SLIGHT,
            spatialMode = SpatialAudioMode.OFF
        )
        assertTrue("AirPods Pro 2 requires micro-detail treble boost (> +0.15f): $pro2TrebleGain", pro2TrebleGain > 0.15f)

        // Verify boAt / Universal TWS energetic bass response
        val boatBassGain = AudioEnhancer.calculateTargetGain(
            centerFreqHz = 80,
            modelId = "boat_airdopes",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            antiHiss = false,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.SLIGHT,
            spatialMode = SpatialAudioMode.OFF
        )
        assertTrue("boAt Airdopes requires energetic bass compensation (> +0.25f): $boatBassGain", boatBassGain > 0.25f)

        val universalGain = AudioEnhancer.calculateTargetGain(
            centerFreqHz = 1000,
            modelId = "universal_tws",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            antiHiss = false,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.SLIGHT,
            spatialMode = SpatialAudioMode.OFF
        )
        assertTrue("Universal TWS vocal clarity must be balanced (> 0.05f): $universalGain", universalGain > 0.05f)
    }

    @Test
    fun testBassBoostHardwareCalculation() {
        val bassBoostOpen = AudioEnhancer.calculateBassBoost(
            modelId = "airpods_2",
            tuningMode = FrequencyTuningMode.MODEL_AUTO,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            toneProfile = ToneProfile.BALANCED_TONE,
            toneIntensity = ToneIntensity.MODERATE
        )
        assertTrue("AirPods 2 open earbud requires high hardware bass boost: $bassBoostOpen", bassBoostOpen >= 450)

        val bassBoostFlat = AudioEnhancer.calculateBassBoost(
            modelId = "airpods_pro_2",
            tuningMode = FrequencyTuningMode.STUDIO_FLAT,
            noiseMode = "OFF",
            ancIntensity = AncIntensity.BALANCED,
            toneProfile = ToneProfile.VOCAL_RANGE,
            toneIntensity = ToneIntensity.SLIGHT
        )
        assertTrue("Studio flat tuning should have minimal bass boost: $bassBoostFlat", bassBoostFlat <= 100)
    }

    @Test
    fun testStereoBalanceRange() {
        val clampedTest = { value: Float -> value.coerceIn(-1.0f, 1.0f) }
        assertEquals(-1.0f, clampedTest(-2.5f), 0.001f)
        assertEquals(1.0f, clampedTest(1.8f), 0.001f)
        assertEquals(0.0f, clampedTest(0.0f), 0.001f)
    }

    @Test
    fun testDiagnosticManagerAddressMasking() {
        assertEquals("AA:BB:CC:**:**:**", me.anypods.app.diagnostics.DiagnosticManager.maskAddress("AA:BB:CC:11:22:33"))
        assertEquals("None", me.anypods.app.diagnostics.DiagnosticManager.maskAddress(""))
        assertEquals("1234****", me.anypods.app.diagnostics.DiagnosticManager.maskAddress("12345678"))
    }
}
