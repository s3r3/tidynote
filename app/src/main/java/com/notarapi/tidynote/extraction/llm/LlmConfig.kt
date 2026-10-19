package com.notarapi.tidynote.extraction.llm

import com.notarapi.tidynote.BuildConfig

/**
 * Resolver tunggal sumber AI (plan §7.5/§9).
 *
 * Prioritas (dipakai yang pertama tersedia):
 *  1. Backend proxy milik pengguna (URL + token diisi di Pengaturan) — paling aman.
 *  2. API key diisi LANGSUNG di aplikasi (Pengaturan) → panggil provider OpenAI-compatible.
 *     Key hanya tersimpan di device pengguna, TIDAK ikut ke dalam APK. Cocok tanpa backend.
 *  3. MODE DEVELOPMENT: API key dari `local.properties` → BuildConfig (ter-baked di APK).
 *     Hanya aktif bila key terisi; JANGAN disebar ke publik.
 *  4. null → AI mati (default), ekstraksi lokal deterministik saja.
 *
 * AI tidak pernah aktif tanpa opt-in pengguna ([aiEnabled]) DAN sumber yang valid.
 */
object LlmConfig {
    val devApiKey: String get() = BuildConfig.LLM_API_KEY
    val devBaseUrl: String get() = BuildConfig.LLM_BASE_URL
    val devModel: String get() = BuildConfig.LLM_MODEL

    /** true bila build ini membawa API key mode development. */
    val hasDevKey: Boolean get() = devApiKey.isNotBlank()

    /** true bila ada sumber AI yang bisa dipakai. */
    fun isAvailable(
        backendUrl: String,
        backendToken: String,
        directApiKey: String = "",
    ): Boolean =
        (backendUrl.isNotBlank() && backendToken.isNotBlank()) ||
            directApiKey.isNotBlank() ||
            hasDevKey

    /**
     * Bangun [ReceiptEnricher] sesuai sumber yang tersedia, atau null bila tak ada.
     * @param aiEnabled opt-in eksplisit pengguna (wajib).
     * @param directApiKey API key yang diisi pengguna di aplikasi (tanpa backend).
     * @param directBaseUrl base URL provider (mis. https://api.openai.com/v1); bila kosong pakai default dev.
     * @param directModel nama model; bila kosong pakai default dev.
     */
    fun build(
        aiEnabled: Boolean,
        backendUrl: String,
        backendToken: String,
        directApiKey: String = "",
        directBaseUrl: String = "",
        directModel: String = "",
    ): ReceiptEnricher? {
        if (!aiEnabled) return null
        // 1) Backend milik pengguna (prioritas utama, tanpa secret di APK).
        if (backendUrl.isNotBlank() && backendToken.isNotBlank()) {
            return LlmExtractor(backendToken = backendToken, baseUrl = backendUrl)
        }
        // 2) API key diisi pengguna di aplikasi → panggil provider langsung.
        //    Key tidak pernah masuk APK; disimpan di device pengguna saja.
        if (directApiKey.isNotBlank()) {
            return LlmExtractor(
                backendToken = directApiKey,
                baseUrl = directBaseUrl.ifBlank { BuildConfig.LLM_BASE_URL },
                modelName = directModel.ifBlank { BuildConfig.LLM_MODEL },
                directProvider = true,
            )
        }
        // 3) MODE DEVELOPMENT: panggil provider langsung dengan key dari BuildConfig.
        if (hasDevKey) {
            return LlmExtractor(
                backendToken = devApiKey,
                baseUrl = devBaseUrl,
                modelName = devModel,
                directProvider = true,
            )
        }
        // 4) AI mati.
        return null
    }

    /** Label sumber untuk ditampilkan di UI Pengaturan. */
    fun sourceLabel(
        backendUrl: String,
        backendToken: String,
        directApiKey: String = "",
    ): String =
        when {
            backendUrl.isNotBlank() && backendToken.isNotBlank() -> "Backend proxy Anda"
            directApiKey.isNotBlank() -> "API key di aplikasi (provider langsung)"
            hasDevKey -> "Mode development (API key di APK — jangan disebar)"
            else -> "Tidak aktif (ekstraksi lokal saja)"
        }
}
