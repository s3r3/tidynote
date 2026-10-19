package com.notarapi.tidynote.extraction.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Kontrak backend §9 — satu tempat agar app + backend konsisten.
 * Endpoint: POST /v1/receipts/parse (alias lama /v1/receipt/extract).
 */
@Serializable
data class ParseRequest(
    val schemaVersion: Int = 1,
    val locale: String = "id-ID",
    val currency: String = "IDR",
    val ocrText: String,
    val requestedFields: List<String> = listOf("merchant", "date", "items", "totals"),
)

@Serializable
data class ParseResponse(
    val content: String,
)

object BackendContract {
    const val SCHEMA_VERSION = 1
    const val MAX_OCR_CHARS = 8_000
    const val PATH = "/v1/receipts/parse"
    const val LEGACY_PATH = "/v1/receipt/extract"

    fun url(
        baseUrl: String,
        legacy: Boolean = false,
    ): String {
        val base = baseUrl.trimEnd('/')
        return base + if (legacy) LEGACY_PATH else PATH
    }

    fun request(ocrText: String): ParseRequest {
        require(ocrText.isNotBlank()) { "OCR kosong" }
        // Potong ke batas aman agar OCR besar tak membuat request gagal.
        return ParseRequest(ocrText = ocrText.take(MAX_OCR_CHARS))
    }

    /**
     * Ambil teks JSON isi struk dari respons provider OpenAI-compatible:
     * `{ "choices": [ { "message": { "content": "...json..." } } ] }`.
     * Dipisah murni agar bisa diuji tanpa jaringan.
     */
    fun extractProviderContent(providerJson: String): String {
        val root =
            try {
                providerJson.parseToJsonElement() as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return ""
        val choices = root["choices"] as? JsonArray ?: return ""
        val message =
            choices
                .firstOrNull()
                ?.let { it as? JsonObject }
                ?.get("message") as? JsonObject ?: return ""
        return (message["content"] as? JsonPrimitive)?.content.orEmpty()
    }

    /**
     * Susun prompt sistem untuk mode DEVELOPMENT (panggil provider langsung).
     * Backend proxy punya prompt sendiri; ini hanya dipakai saat tak ada backend.
     */
    fun devSystemPrompt(): String =
        buildString {
            append("Kamu mengekstrak data struk belanja dari teks OCR. ")
            append("Balas HANYA dengan JSON valid (tanpa penjelasan, tanpa markdown). ")
            append("Skema: {merchantName:string|null, transactionDate:ISO8601|null, ")
            append("currency:\"IDR\", items:[{name:string, quantity:int, unitPriceMinor:long, ")
            append("totalMinor:long, confidence:0..1}], subtotalMinor:long|null, ")
            append("taxMinor:long|null, discountMinor:long|null, totalMinor:long|null, confidence:0..1}. ")
            append("Semua nominal dalam satuan terkecil rupiah (mis. Rp 25.000 -> 25000). ")
            append("Jangan mengarang; jika tak yakin isi null. currency selalu \"IDR\".")
        }
}

private fun String.parseToJsonElement(): JsonElement = Json.parseToJsonElement(this)
