package me.anypods.app.model

import android.content.Context
import androidx.annotation.DrawableRes
import me.anypods.app.R

data class PodModelInfo(
    val id: String,
    val name: String,
    val brand: String,
    @DrawableRes val imageRes: Int
)

object PodModelManager {
    private const val PREFS_NAME = "anypods_prefs"
    private const val KEY_SELECTED_MODEL = "selected_pod_model"

    val ALL_MODELS = listOf(
        // Apple AirPods
        PodModelInfo("airpods_pro_2", "AirPods Pro 2", "Apple", R.drawable.airpods_pro_2_buds),
        PodModelInfo("airpods_pro_1", "AirPods Pro 1", "Apple", R.drawable.airpods_pro_1_buds),
        PodModelInfo("airpods_4_anc", "AirPods 4 ANC", "Apple", R.drawable.airpods_4_anc_buds),
        PodModelInfo("airpods_3", "AirPods 3", "Apple", R.drawable.airpods_3_buds),
        PodModelInfo("airpods_2", "AirPods 2", "Apple", R.drawable.airpods_2_buds),
        PodModelInfo("airpods_max", "AirPods Max", "Apple", R.drawable.airpods_max_buds),

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

    fun detectModel(deviceName: String): PodModelInfo {
        val lower = deviceName.lowercase()
        return when {
            lower.contains("max") && lower.contains("airpod") -> getModelById("airpods_max")
            lower.contains("pro 2") || lower.contains("pro (2") -> getModelById("airpods_pro_2")
            lower.contains("pro") && (lower.contains("airpod") || lower.contains("pod")) -> getModelById("airpods_pro_1")
            lower.contains("airpods 4") || lower.contains("airpod 4") -> getModelById("airpods_4_anc")
            lower.contains("airpods 3") || lower.contains("airpod 3") -> getModelById("airpods_3")
            lower.contains("airpods 2") || lower.contains("airpod 2") -> getModelById("airpods_2")
            lower.contains("galaxy") && (lower.contains("3") || lower.contains("blade")) -> getModelById("galaxy_buds_3_pro")
            lower.contains("galaxy") || lower.contains("buds pro") || lower.contains("buds2") || lower.contains("buds live") || lower.contains("buds fe") -> getModelById("galaxy_buds_pro_2")
            lower.contains("pixel") && (lower.contains("a-series") || lower.contains(" a ") || lower.endsWith(" a")) -> getModelById("pixel_buds_a")
            lower.contains("pixel") -> getModelById("pixel_buds_pro")
            lower.contains("nothing") || lower.contains("ear (") || lower.contains("ear 1") || lower.contains("ear 2") -> getModelById("nothing_ear_2")
            lower.contains("sony") || lower.contains("wf-") || lower.contains("wh-") || lower.contains("xm5") || lower.contains("xm4") -> getModelById("sony_wf1000xm5")
            lower.contains("beats") || lower.contains("powerbeats") || lower.contains("fit pro") -> getModelById("beats_studio_buds")
            lower.contains("oneplus") || lower.contains("nord buds") -> getModelById("oneplus_buds_pro_2")
            lower.contains("boat") || lower.contains("airdopes") || lower.contains("noise") || lower.contains("boult") || lower.contains("black") -> getModelById("tws_black")
            lower.contains("white") -> getModelById("tws_white")
            else -> getModelById("airpods_pro_2")
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
