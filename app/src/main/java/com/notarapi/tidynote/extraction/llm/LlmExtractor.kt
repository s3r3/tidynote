package com.notarapi.tidynote.extraction.llm

import com.notarapi.tidynote.core.model.ExtractionSource
import com.notarapi.tidynote.core.model.FieldConfidence
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ReceiptItemDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LlmExtractor(
    private val backendToken: String,
    private val baseUrl: String,
    private val modelName: String = "llama-3.1-8b-instruct",
    /**
     * true = MODE DEVELOPMENT: panggil provider OpenAI-compatible langsung
     * (POST {baseUrl}/chat/completions) memakai [backendToken] sebagai API key.
     * false (default) = panggil backend proxy milik sendiri (kontrak §9).
     */
    private val directProvider: Boolean = false,
) : ReceiptEnricher {
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    private val mediaType = "application/json".toMediaType()

    override suspend fun extractFromOcr(ocrText: String): ReceiptDraft =
        withContext(Dispatchers.IO) {
            val response =
                if (directProvider) {
                    callProvider(ocrText)
                } else {
                    callBackend(BackendContract.request(ocrText))
                }
            parseResponse(response, ocrText)
        }

    // (buildPrompt lama dihapus — backend/provider yang menyusun prompt dari ocrText.)

    /** MODE DEVELOPMENT: panggil provider langsung (OpenAI-compatible chat completions). */
    private fun callProvider(ocrText: String): String {
        val clipped = ocrText.take(BackendContract.MAX_OCR_CHARS)
        val body =
            json.encodeToString(
                ProviderRequest.serializer(),
                ProviderRequest(
                    model = modelName,
                    messages =
                        listOf(
                            ProviderMessage("system", BackendContract.devSystemPrompt()),
                            ProviderMessage("user", clipped),
                        ),
                ),
            )
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request =
            Request
                .Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $backendToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 429) throw RateLimitedException()
                if (response.code == 401 || response.code == 403) throw UnauthorizedException()
                throw ProviderException("LLM provider gagal: ${response.code}")
            }
            return BackendContract.extractProviderContent(text)
        }
    }

    private fun callBackend(req: ParseRequest): String {
        // Coba path baru dulu, fallback ke path lama bila 404.
        val paths = listOf(BackendContract.url(baseUrl), BackendContract.url(baseUrl, legacy = true))
        var lastError = 0
        for (url in paths) {
            val body = json.encodeToString(ParseRequest.serializer(), req)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $backendToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(mediaType))
                    .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return extractContent(text)
                if (response.code == 404) {
                    lastError = 404
                    return@use // coba path berikutnya
                }
                if (response.code == 429) throw RateLimitedException()
                if (response.code == 401 || response.code == 403) throw UnauthorizedException()
                throw ProviderException("LLM backend gagal: ${response.code}")
            }
        }
        throw ProviderException("LLM backend gagal: $lastError")
    }

    class RateLimitedException : RuntimeException("Rate limit — coba lagi nanti, input manual tetap bisa.")

    class UnauthorizedException : RuntimeException("Token backend ditolak — periksa token di Pengaturan.")

    /**
     * Provider membalas dengan status yang tidak ditangani (5xx, 400 body salah,
     * dll). Dipisahkan dari RuntimeException generik supaya pemanggil bisa
     * membedakan "LLM-nya yang bermasalah" dari bug di kode kita sendiri.
     */
    class ProviderException(
        message: String,
    ) : RuntimeException(message)

    private fun extractContent(backendJson: String): String =
        try {
            val root =
                json.parseToJsonElement(backendJson)
                    as? kotlinx.serialization.json.JsonObject
            val content =
                root?.get("content")
                    as? kotlinx.serialization.json.JsonPrimitive
            content?.content ?: backendJson
        } catch (_: Exception) {
            backendJson
        }

    private fun parseResponse(
        responseText: String,
        originalOcr: String,
    ): ReceiptDraft {
        var cleanText = responseText.trim()
        if (cleanText.startsWith("```")) {
            cleanText = cleanText.removePrefix("```json").removePrefix("```").trim()
            if (cleanText.endsWith("```")) cleanText = cleanText.dropLast(3).trim()
        }

        return try {
            // plan §9 Aturan kegagalan: bukan JSON / schema salah -> tolak.
            val llmResponse = json.decodeFromString<LlmReceiptResponse>(cleanText)
            llmResponse.validate()
            llmResponse.toReceiptDraft(originalOcr)
        } catch (_: Exception) {
            ReceiptDraft(rawOcrText = originalOcr, source = ExtractionSource.LLM_FALLBACK)
        }
    }

    companion object {
        const val MAX_OCR_CHARS = 8_000
    }
}

@Serializable
data class LlmReceiptResponse(
    val merchantName: String?,
    val transactionDate: String?,
    val currency: String = "IDR",
    val items: List<LlmItemResponse> = emptyList(),
    val subtotalMinor: Long?,
    val taxMinor: Long?,
    val discountMinor: Long?,
    val totalMinor: Long?,
    val confidence: Float = 0f,
) {
    /** Strict schema §9: tolak angka negatif, currency asing, item kosong-nama. */
    fun validate() {
        require(currency == "IDR") { "currency harus IDR" }
        val amounts = listOf(subtotalMinor, taxMinor, discountMinor, totalMinor)
        require(amounts.all { (it ?: 0L) >= 0L }) { "nominal negatif" }
        require(items.all { it.name.isNotBlank() && it.quantity in 1..999 && it.unitPriceMinor >= 0 && it.totalMinor >= 0 }) {
            "item tidak valid"
        }
        transactionDate?.let {
            require(runCatching { java.time.Instant.parse(it) }.isSuccess) { "tanggal bukan ISO-8601" }
        }
    }

    fun toReceiptDraft(rawOcr: String): ReceiptDraft {
        val draftItems = items.map { it.toReceiptItemDraft() }
        val avgConfidence =
            if (draftItems.isNotEmpty()) {
                draftItems.map { it.confidence }.average().toFloat()
            } else {
                confidence.toFloat()
            }

        return ReceiptDraft(
            merchantName = merchantName,
            transactionDate = transactionDate?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() },
            currency = currency,
            items = draftItems,
            subtotalMinor = subtotalMinor,
            discountMinor = discountMinor,
            taxMinor = taxMinor,
            totalMinor = totalMinor,
            source = ExtractionSource.LLM_FALLBACK,
            confidence =
                FieldConfidence(
                    merchant = avgConfidence,
                    date = avgConfidence,
                    total = avgConfidence,
                    items = avgConfidence,
                    tax = avgConfidence,
                    discount = avgConfidence,
                ),
            rawOcrText = rawOcr,
        )
    }
}

@Serializable
data class LlmItemResponse(
    val name: String,
    val quantity: Int = 1,
    val unitPriceMinor: Long,
    val totalMinor: Long,
    val confidence: Float = 0.8f,
) {
    fun toReceiptItemDraft() =
        ReceiptItemDraft(
            name = name,
            quantity = quantity,
            unitPriceMinor = unitPriceMinor,
            totalMinor = totalMinor,
            confidence = confidence,
        )
}

/** Request body OpenAI-compatible (mode DEVELOPMENT / direct provider). */
@Serializable
data class ProviderRequest(
    val model: String,
    val messages: List<ProviderMessage>,
    val temperature: Float = 0f,
    // Nama properti camelCase (konvensi Kotlin), nama di kabel snake_case
    // (yang diharapkan provider). Dulu propertinya dinamai langsung
    // `response_format`, jadi gaya Kotlin-nya ikut dikorbankan hanya demi
    // menyesuaikan API.
    @SerialName("response_format")
    val responseFormat: ProviderResponseFormat? = ProviderResponseFormat(),
)

@Serializable
data class ProviderMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ProviderResponseFormat(
    val type: String = "json_object",
)
