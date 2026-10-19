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
    MANUAL_INPUT,
}

@Serializable
@Parcelize
data class FieldConfidence(
    val merchant: Float = 0f,
    val date: Float = 0f,
    val total: Float = 0f,
    val items: Float = 0f,
    val tax: Float = 0f,
    val discount: Float = 0f,
) : Parcelable {
    val average: Float
        get() =
            listOf(merchant, date, total, items, tax, discount)
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
    val confidence: Float = 0f,
) : Parcelable {
    val lineTotal: Long
        get() = if (totalMinor > 0) totalMinor else quantity.toLong() * unitPriceMinor
}

@Serializable
@Parcelize
data class ReceiptDraft(
    val merchantName: String? = null,
    /** NPWP merchant. Disimpan ke MerchantEntity.taxId, bukan ke tabel receipts. */
    val npwp: String? = null,
    /** Nomor invoice/faktur dari struk. Kolom sendiri di receipts sejak skema v3. */
    val invoiceNumber: String? = null,
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
    val imagePath: String? = null,
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

    /**
     * Wajib diperiksa manusia bila confidence rendah ATAU hitungan tidak cocok.
     *
     * `FieldConfidence.needsReview` memakai RATA-RATA, jadi struk yang gagal
     * rekonsiliasi tetap bisa lolos: ReceiptValidator hanya menurunkan
     * confidence.total ke 0.5, sehingga {merchant .9, date .9, items .8, total .5}
     * rata-ratanya 0.8 dan struk disimpan REVIEWED padahal ReviewScreen
     * menampilkan peringatan "Selisih hitung". Menyertakan isReconciled membuat
     * status tersimpan sejalan dengan yang dilihat user.
     */
    val needsReview: Boolean
        get() = confidence.needsReview || !isReconciled

    /**
     * Status yang harus disimpan.
     *
     * [existingStatus] adalah status baris yang sedang ditimpa, null untuk struk baru.
     * REVIEWED dipertahankan selama hitungan masih cocok: menyimpan ulang tanpa
     * mengubah angka tidak boleh menurunkan status yang sudah ditandai user.
     */
    fun reviewStatusForSave(existingStatus: String? = null): String =
        when {
            !isReconciled -> "PENDING"
            existingStatus == "REVIEWED" -> "REVIEWED"
            confidence.needsReview -> "PENDING"
            else -> "REVIEWED"
        }

    fun copyWithDefaults(): ReceiptDraft {
        val computedTotal = totalMinor ?: (itemSumMinor + (taxMinor ?: 0) - (discountMinor ?: 0))
        return copy(totalMinor = computedTotal)
    }
}
