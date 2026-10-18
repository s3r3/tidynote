package com.notarapi.tidynote.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
@Parcelize
enum class ExtractionSource : Parcelable {
    LOCAL_PARSER,
    LLM_FALLBACK,
    MANUAL_INPUT
}

@Serializable
@Parcelize
data class FieldConfidence(
    val merchant: Float = 0f,
    val date: Float = 0f,
    val total: Float = 0f,
    val items: Float = 0f,
    val tax: Float = 0f,
    val discount: Float = 0f
) : Parcelable {
    val average: Float
        get() = listOf(merchant, date, total, items, tax, discount)
            .filter { it > 0f }
            .average()
            .toFloat()
            .takeIf { it.isFinite() } ?: 0f

    val needsReview: Boolean
        get() = average < 0.7f
}

@Serializable
@Parcelize
data class ReceiptItemDraft(
    val name: String = "",
    val quantity: Int = 1,
    val unitPriceMinor: Long = 0,
    val totalMinor: Long = 0,
    val confidence: Float = 0f
) : Parcelable {
    val lineTotal: Long
        get() = if (totalMinor > 0) totalMinor else quantity.toLong() * unitPriceMinor
}

@Serializable
@Parcelize
data class ReceiptDraft(
    val merchantName: String? = null,
    @Contextual
    val transactionDate: Instant? = null,
    val currency: String = "IDR",
    val items: List<ReceiptItemDraft> = emptyList(),
    val subtotalMinor: Long? = null,
    val discountMinor: Long? = null,
    val taxMinor: Long? = null,
    val totalMinor: Long? = null,
    val categoryId: Long? = null,
    val rawOcrText: String = "",
    val source: ExtractionSource = ExtractionSource.LOCAL_PARSER,
    val confidence: FieldConfidence = FieldConfidence(),
    val imagePath: String? = null
) : Parcelable {
    val itemSumMinor: Long
        get() = items.sumOf { it.lineTotal }

    val isReconciled: Boolean
        get() {
            val itemSum = itemSumMinor
            val tax = taxMinor ?: 0L
            val discount = discountMinor ?: 0L
            val computed = itemSum + tax - discount
            val total = totalMinor
            return when {
                total == null -> false
                total == computed -> true
                else -> false
            }
        }

    fun copyWithDefaults(): ReceiptDraft {
        val computedTotal = totalMinor ?: (itemSumMinor + (taxMinor ?: 0) - (discountMinor ?: 0))
        return copy(totalMinor = computedTotal)
    }
}