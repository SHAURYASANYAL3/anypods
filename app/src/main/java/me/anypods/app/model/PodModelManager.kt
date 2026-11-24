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
    private const val PREFS_NAME = "anypods_prefs"
    private const val KEY_SELECTED_MODEL = "selected_pod_model"

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

        // Nothing Ear
        PodModelInfo("nothing_ear_2", "Nothing Ear (2)", "Nothing", R.drawable.nothing_ear_buds),

        // Beats
        PodModelInfo("beats_studio_buds", "Beats Studio Buds", "Beats", R.drawable.beats_buds),

        // OnePlus
        PodModelInfo("oneplus_buds_pro_2", "OnePlus Buds Pro 2", "OnePlus", R.drawable.oneplus_buds_pro_buds),

        // Universal / boAt / TWS
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
            // AirPods Max
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

            // AirPods 3 (e.g. "AirPods 3", "AirPods (3rd generation)", "AirPods 3rd Gen")
            (lower.contains("3") || lower.contains("third") || lower.contains("3rd")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_3")

            // AirPods 2 (e.g. "AirPods 2", "AirPods (2nd generation)", "AirPods 2nd Gen")
            (lower.contains("2") || lower.contains("second") || lower.contains("2nd")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_2")

            // AirPods 1 (e.g. "AirPods 1", "AirPods (1st generation)", "AirPods 1st Gen")
            (lower.contains("1") || lower.contains("first") || lower.contains("1st")) &&
                (lower.contains("airpod") || lower.contains("pod")) ->
                getModelById("airpods_1")

            // Generic AirPods (e.g. "Shaurya's AirPods", "AirPods", "AirPods - Find My")
            lower.contains("airpod") || lower.contains("air pod") ->
                getModelById("airpods_pro_2")

            // Samsung Galaxy Buds
            lower.contains("galaxy") && (lower.contains("3") || lower.contains("blade")) ->
                getModelById("galaxy_buds_3_pro")
            lower.contains("galaxy") || lower.contains("buds pro") || lower.contains("buds2") || lower.contains("buds live") || lower.contains("buds fe") ->
                getModelById("galaxy_buds_pro_2")

            // Google Pixel Buds
            lower.contains("pixel") && (lower.contains("a-series") || lower.contains(" a ") || lower.endsWith(" a")) ->
                getModelById("pixel_buds_a")
            lower.contains("pixel") ->
                getModelById("pixel_buds_pro")

            // Other brands
            lower.contains("nothing") || lower.contains("ear (") || lower.contains("ear 1") || lower.contains("ear 2") ->
                getModelById("nothing_ear_2")
            lower.contains("sony") || lower.contains("wf-") || lower.contains("wh-") || lower.contains("xm5") || lower.contains("xm4") ->
                getModelById("sony_wf1000xm5")
            lower.contains("beats") || lower.contains("powerbeats") || lower.contains("fit pro") ->
                getModelById("beats_studio_buds")
            lower.contains("oneplus") || lower.contains("nord buds") ->
                getModelById("oneplus_buds_pro_2")
            lower.contains("boat") || lower.contains("airdopes") || lower.contains("noise") || lower.contains("boult") || lower.contains("black") ->
                getModelById("tws_black")
            lower.contains("white") ->
                getModelById("tws_white")
            else ->
                getModelById("airpods_pro_2")
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
        return ALL_MODELS.first()
    }

    fun saveSelectedModel(context: Context, modelId: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
    }
}
