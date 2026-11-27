package hu.rmz.prefiscore.data

import android.content.Context
import hu.rmz.prefiscore.model.PaperConvention

/** Alkalmazás-beállítások (az alkalmazás privát tárhelyén). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("anthropic_api_key", "").orEmpty()
        set(v) = prefs.edit().putString("anthropic_api_key", v.trim()).apply()

    var paperConvention: PaperConvention
        get() = runCatching { PaperConvention.valueOf(prefs.getString("paper_convention", null)!!) }.getOrDefault(PaperConvention.FROM_ZERO)
        set(v) = prefs.edit().putString("paper_convention", v.name).apply()
}
