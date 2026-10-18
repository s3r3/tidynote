package com.notarapi.tidynote.extraction

import com.notarapi.tidynote.core.model.FieldConfidence
import com.notarapi.tidynote.core.model.ReceiptDraft
import java.time.Instant

/**
 * Validasi deterministik §7.4 plan.md:
 * - tanggal valid dan tidak di masa depan
 * - subtotal + PPN - diskon ≈ total (toleransi Rp 5)
 * - item total vs total (toleransi max(Rp 100, 5%))
 * - semua nominal non-negatif
 * Menurunkan confidence field yang gagal validasi, tidak melempar.
 */
object ReceiptValidator {

    private const val TOTAL_TOLERANCE_MINOR = 5L

    fun validate(draft: ReceiptDraft): ReceiptDraft {
        var confidence = draft.confidence
        var date = draft.transactionDate

        // 1. Tanggal: tolak masa depan.
        if (date != null && date.isAfter(Instant.now())) {
            date = null
            confidence = confidence.copy(date = 0.2f)
        }

        // 2. Nominal non-negatif.
        val negatives = listOf(
            draft.subtotalMinor, draft.taxMinor,
            draft.discountMinor, draft.totalMinor,
        ).any { (it ?: 0L) < 0L } || draft.items.any {
            it.unitPriceMinor < 0 || it.totalMinor < 0 || it.quantity <= 0
        }
        var totalConf = confidence.total
        if (negatives) totalConf = minOf(totalConf, 0.3f)

        // 3. Rekonsiliasi subtotal + pajak - diskon vs total.
        val computed = (draft.subtotalMinor ?: draft.itemSumMinor) +
            (draft.taxMinor ?: 0L) - (draft.discountMinor ?: 0L)
        val total = draft.totalMinor
        if (total != null && total > 0) {
            val diff = kotlin.math.abs(computed - total)
            if (diff > TOTAL_TOLERANCE_MINOR && draft.items.isNotEmpty()) {
                totalConf = minOf(totalConf, 0.5f)
            }
        } else if (total != null && total <= 0) {
            totalConf = minOf(totalConf, 0.3f)
        }

        // 4. Item sum vs total (toleransi longgar untuk OCR berisik).
        if (total != null && total > 0 && draft.items.isNotEmpty()) {
            val itemSum = draft.itemSumMinor
            val tolerance = maxOf(100L, (total * 5) / 100)
            if (kotlin.math.abs(itemSum - computed) > tolerance &&
                kotlin.math.abs(itemSum - total) > tolerance
            ) {
                confidence = confidence.copy(items = minOf(confidence.items, 0.5f))
            }
        }

        confidence = confidence.copy(total = totalConf)
        return draft.copy(transactionDate = date, confidence = confidence)
    }
}
