package com.notarapi.tidynote.core.prefs

/**
 * Pilihan tema yang disimpan user. SYSTEM mengikuti setelan Android.
 *
 * Ditempatkan di core, bukan di ui.theme: nilai ini bagian dari state yang
 * dipersistensikan (AppPreferences), jadi UI yang mengonsumsinya — bukan
 * sebaliknya.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStorage(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
