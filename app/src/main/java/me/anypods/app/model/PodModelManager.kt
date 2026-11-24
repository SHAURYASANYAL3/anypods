package me.anypods.app.model

import android.content.Context
import androidx.annotation.DrawableRes
import me.anypods.app.R

data class PodModelInfo(
    val id: String,
    val name: String,
    val brand: String,
    @DrawableRes val imageRes: Int,
    @DrawableRes val caseRes: Int = imageRes,
    @DrawableRes val leftRes: Int = imageRes,
    @DrawableRes val rightRes: Int = imageRes
)

object PodModelManager {
    const val PREFS_NAME = "anypods_prefs"
    const val KEY_SELECTED_MODEL = "selected_pod_model"

    val ALL_MODELS = listOf(
        // Apple AirPods - Complete Lineup
        PodModelInfo("airpods_pro_2", "AirPods Pro 2", "Apple", R.drawable.airpods_pro_2_buds, R.drawable.airpods_pro_2_case, R.drawable.airpods_pro_2_left, R.drawable.airpods_pro_2_right),
        PodModelInfo("airpods_pro_1", "AirPods Pro 1", "Apple", R.drawable.airpods_pro_1_buds, R.drawable.airpods_pro_1_case, R.drawable.airpods_pro_1_left, R.drawable.airpods_pro_1_right),
        PodModelInfo("airpods_4_anc", "AirPods 4 ANC", "Apple", R.drawable.airpods_4_anc_buds, R.drawable.airpods_4_anc_case, R.drawable.airpods_4_anc_left, R.drawable.airpods_4_anc_right),
        PodModelInfo("airpods_4", "AirPods 4", "Apple", R.drawable.airpods_4_buds, R.drawable.airpods_4_case, R.drawable.airpods_4_left, R.drawable.airpods_4_right),
        PodModelInfo("airpods_3", "AirPods 3", "Apple", R.drawable.airpods_3_buds, R.drawable.airpods_3_case, R.drawable.airpods_3_left, R.drawable.airpods_3_right),
        PodModelInfo("airpods_2", "AirPods 2", "Apple", R.drawable.airpods_2_buds, R.drawable.airpods_2_case, R.drawable.airpods_2_left, R.drawable.airpods_2_right),
        PodModelInfo("airpods_1", "AirPods 1", "Apple", R.drawable.airpods_1_buds, R.drawable.airpods_1_case, R.drawable.airpods_1_left, R.drawable.airpods_1_right),
        PodModelInfo("airpods_max", "AirPods Max", "Apple", R.drawable.airpods_max_buds, R.drawable.airpods_max_buds, R.drawable.airpods_max_buds, R.drawable.airpods_max_buds),

        // Samsung Galaxy Buds
        PodModelInfo("galaxy_buds_pro_2", "Galaxy Buds Pro 2", "Samsung", R.drawable.galaxy_buds_pro_buds),
        PodModelInfo("galaxy_buds_3_pro", "Galaxy Buds 3 Pro", "Samsung", R.drawable.galaxy_buds_3_buds),

        // Google Pixel Buds
        PodModelInfo("pixel_buds_pro", "Pixel Buds Pro", "Google", R.drawable.pixel_buds_pro_buds),
        PodModelInfo("pixel_buds_a", "Pixel Buds A-Series", "Google", R.drawable.pixel_buds_a_buds),

        // Sony
        PodModelInfo("sony_wf1000xm5", "Sony WF-1000XM5", "Sony", R.drawable.sony_wf1000xm5_buds),

        // Nothing & CMF
        PodModelInfo("nothing_ear_2", "Nothing Ear", "Nothing", R.drawable.nothing_ear_buds),
        PodModelInfo("cmf_buds", "CMF Buds", "CMF by Nothing", R.drawable.nothing_ear_buds),

        // Beats
        PodModelInfo("beats_studio_buds", "Beats Studio Buds", "Beats", R.drawable.beats_buds),

        // OnePlus
        PodModelInfo("oneplus_buds_pro_2", "OnePlus Buds Pro", "OnePlus", R.drawable.oneplus_buds_pro_buds),

        // boAt Airdopes
        PodModelInfo("boat_airdopes", "boAt Airdopes", "boAt", R.drawable.tws_black_buds),

        // Noise
        PodModelInfo("noise_buds", "Noise Buds", "Noise", R.drawable.tws_black_buds),

        // Boult Audio
        PodModelInfo("boult_buds", "Boult Audio", "Boult", R.drawable.tws_black_buds),

        // JBL
        PodModelInfo("jbl_tune", "JBL Earbuds", "JBL", R.drawable.tws_black_buds),

        // Bose
        PodModelInfo("bose_qc", "Bose QuietComfort", "Bose", R.drawable.tws_black_buds),

        // Realme
        PodModelInfo("realme_buds", "Realme Buds Air", "Realme", R.drawable.tws_white_buds),

        // Xiaomi & Redmi
        PodModelInfo("xiaomi_buds", "Redmi / Xiaomi Buds", "Xiaomi", R.drawable.tws_white_buds),

        // Anker Soundcore
        PodModelInfo("soundcore_liberty", "Soundcore Liberty", "Anker", R.drawable.tws_black_buds),

        // Sennheiser
        PodModelInfo("sennheiser_momentum", "Sennheiser Momentum", "Sennheiser", R.drawable.tws_black_buds),

        // Skullcandy
        PodModelInfo("skullcandy_buds", "Skullcandy Earbuds", "Skullcandy", R.drawable.tws_black_buds),

        // Marshall
        PodModelInfo("marshall_minor", "Marshall Minor", "Marshall", R.drawable.tws_black_buds),

        // OPPO
        PodModelInfo("oppo_enco", "OPPO Enco", "OPPO", R.drawable.tws_white_buds),

        // Vivo
        PodModelInfo("vivo_tws", "Vivo TWS", "Vivo", R.drawable.tws_white_buds),

        // Huawei
        PodModelInfo("huawei_freebuds", "Huawei FreeBuds", "Huawei", R.drawable.tws_white_buds),

        // Universal / Generic TWS Lineup
        PodModelInfo("universal_tws", "Universal Wireless Earbuds", "Universal", R.drawable.tws_black_buds),
        PodModelInfo("tws_black", "Stealth Black TWS", "Universal", R.drawable.tws_black_buds),
        PodModelInfo("tws_white", "White Stem TWS", "Universal", R.drawable.tws_white_buds)
    )

    fun getModelById(id: String): PodModelInfo {
        return ALL_MODELS.firstOrNull { it.id == id } ?: ALL_MODELS.first()
    }

    fun getModelByName(name: String): PodModelInfo {
        return ALL_MODELS.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALL_MODELS.first()
    }

    fun getModelByBeaconId(idFull: String): PodModelInfo {
        val upper = idFull.uppercase()
        return when {
            upper == "0220" -> getModelById("airpods_1")
            upper == "0F20" -> getModelById("airpods_2")
            upper == "1320" -> getModelById("airpods_3")
            upper == "1B20" -> getModelById("airpods_4")
            upper == "1C20" -> getModelById("airpods_4_anc")
            upper == "0E20" -> getModelById("airpods_pro_1")
            upper == "1420" || upper == "2420" -> getModelById("airpods_pro_2")
            upper == "2720" -> getModelById("airpods_pro_2")
            upper.startsWith("0A") || upper.contains("A") -> getModelById("airpods_max")
            upper == "0B20" || upper == "1220" || upper == "1720" -> getModelById("beats_studio_buds")
            else -> getModelById("airpods_pro_2")
        }
    }

    fun detectModel(deviceName: String): PodModelInfo {
        val lower = deviceName.lowercase()
            .replace("’", "'")
            .replace("`", "'")
            .trim()

        return when {
            // 1. Beats
            lower.contains("beats") || lower.contains("powerbeats") || lower.contains("fit pro") || lower.contains("solo buds") ->
                getModelById("beats_studio_buds")

            // 2. Google Pixel Buds
            lower.contains("pixel") && (lower.contains("a-series") || lower.contains(" a ") || lower.endsWith(" a")) ->
                getModelById("pixel_buds_a")
            lower.contains("pixel") ->
                getModelById("pixel_buds_pro")

            // 3. OnePlus & Nord
            lower.contains("oneplus") || lower.contains("nord buds") ->
                getModelById("oneplus_buds_pro_2")

            // 4. Nothing & CMF
            lower.contains("cmf") ->
                getModelById("cmf_buds")
            lower.contains("nothing") || lower.contains("ear (") || lower.contains("ear 1") || lower.contains("ear 2") || lower.contains("ear stick") ->
                getModelById("nothing_ear_2")

            // 5. Samsung Galaxy Buds
            (lower.contains("galaxy") || lower.contains("samsung")) && (lower.contains("3") || lower.contains("blade")) ->
                getModelById("galaxy_buds_3_pro")
            (lower.contains("galaxy") || lower.contains("samsung")) ||
                (lower.contains("buds pro") && !lower.contains("cmf") && !lower.contains("oneplus") && !lower.contains("pixel")) ||
                lower.contains("buds2") || lower.contains("buds live") || lower.contains("buds fe") ->
                getModelById("galaxy_buds_pro_2")

            // 6. Sony
            lower.contains("sony") || lower.contains("wf-") || lower.contains("wh-") || lower.contains("xm5") || lower.contains("xm4") || lower.contains("xm3") || lower.contains("linkbuds") || lower.contains("c500") || lower.contains("c700") ->
                getModelById("sony_wf1000xm5")

            // 7. boAt
            lower.contains("boat") || lower.contains("airdopes") || lower.contains("nirvana") || lower.contains("rockerz") ->
                getModelById("boat_airdopes")

            // 8. Noise (Ensure "noise cancel" or "noise reduction" feature phrases are not confused with Noise brand)
            ((lower.startsWith("noise") || lower.contains(" noise ")) && !lower.contains("noise cancel") && !lower.contains("noise-cancel") && !lower.contains("noise reduction")) ||
                lower.contains("noise buds") || lower.contains("shots") || lower.contains("airwave") ->
                getModelById("noise_buds")

            // 9. Boult Audio
            lower.contains("boult") || lower.contains("airbass") || lower.contains("curve") || lower.contains("z40") ->
                getModelById("boult_buds")

            // 10. JBL
            lower.contains("jbl") || lower.contains("tune") || lower.contains("live pro") || lower.contains("wave") || lower.contains("reflect") || lower.contains("endurance") ->
                getModelById("jbl_tune")

            // 11. Bose
            lower.contains("bose") || lower.contains("quietcomfort") || lower.contains("soundsport") ->
                getModelById("bose_qc")

            // 12. Realme
            lower.contains("realme") || lower.contains("dizo") ->
                getModelById("realme_buds")

            // 13. Xiaomi & Redmi
            lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("airdots") || lower.contains("poco buds") ->
                getModelById("xiaomi_buds")

            // 14. Anker Soundcore
            lower.contains("soundcore") || lower.contains("anker") || lower.contains("liberty") || lower.contains("life p") || lower.contains("space a") ->
                getModelById("soundcore_liberty")

            // 15. Sennheiser
            lower.contains("sennheiser") || lower.contains("momentum") || lower.contains("cx true") ->
                getModelById("sennheiser_momentum")

            // 16. Skullcandy
            lower.contains("skullcandy") || lower.contains("dime") || lower.contains("indy") || lower.contains("sesh") || lower.contains("rail") ->
                getModelById("skullcandy_buds")

            // 17. Marshall
            lower.contains("marshall") || lower.contains("minor") || lower.contains("motif") ->
                getModelById("marshall_minor")

            // 18. OPPO
            lower.contains("oppo") || lower.contains("enco") ->
                getModelById("oppo_enco")

            // 19. Vivo
            lower.contains("vivo") || lower.contains("iqoo") ->
                getModelById("vivo_tws")

            // 20. Huawei
            lower.contains("huawei") || lower.contains("freebuds") || lower.contains("freelace") ->
                getModelById("huawei_freebuds")

            // 21. Apple AirPods Lineup
            lower.contains("max") && (lower.contains("airpod") || lower.contains("pod") || lower.contains("apple")) ->
                getModelById("airpods_max")

            // AirPods Pro 2 (e.g. "AirPods Pro 2", "AirPods Pro (2nd generation)", "AirPods Pro Gen 2", "Shaurya's AirPods Pro 2")
            (lower.contains("pro") && (lower.contains("2") || lower.contains("second") || lower.contains("2nd"))) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_pro_2")

            // AirPods Pro 1 (e.g. "AirPods Pro", "AirPods Pro 1", "AirPods Pro (1st generation)")
            lower.contains("pro") && (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_pro_1")

            // AirPods 4 ANC
            (lower.contains("4") || lower.contains("fourth") || lower.contains("4th")) &&
                (lower.contains("anc") || lower.contains("noise cancel")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_4_anc")

            // AirPods 4 Standard
            (lower.contains("4") || lower.contains("fourth") || lower.contains("4th")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_4")

            // AirPods 3
            (lower.contains("3") || lower.contains("third") || lower.contains("3rd")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_3")

            // AirPods 2
            (lower.contains("2") || lower.contains("second") || lower.contains("2nd")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_2")

            // AirPods 1
            (lower.contains("1") || lower.contains("first") || lower.contains("1st")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_1")

            // Generic AirPods
            lower.contains("airpod") || lower.contains("air pod") || lower.contains("earpods") ->
                getModelById("airpods_pro_2")

            // 22. Color-specific TWS
            lower.contains("white") || lower.contains("light") ->
                getModelById("tws_white")
            lower.contains("black") || lower.contains("dark") || lower.contains("stealth") ->
                getModelById("tws_black")

            // 23. Universal Fallback - Clean Universal Earbuds rather than forcing Apple AirPods!
            else ->
                getModelById("universal_tws")
        }
    }

    fun getSelectedModel(context: Context, deviceName: String = ""): PodModelInfo {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = sp.getString(KEY_SELECTED_MODEL, null)
        if (savedId != null) {
            val found = ALL_MODELS.firstOrNull { it.id == savedId }
            if (found != null) return found
        }
        if (deviceName.isNotEmpty() && deviceName != "Wireless Earbuds") {
            return detectModel(deviceName)
        }
        return getModelById("universal_tws")
    }

    fun saveSelectedModel(context: Context, modelId: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }
}
