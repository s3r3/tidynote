package com.notarapi.tidynote.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mengunci perbaikan P1: `needsReview` sekarang menyertakan rekonsiliasi.
 *
 * Sebelumnya keputusan PENDING/REVIEWED hanya melihat rata-rata confidence.
 * `ReceiptValidator` cuma menurunkan confidence.total saat hitungan tidak cocok,
 * sehingga struk yang gagal rekonsiliasi tetap bisa punya rata-rata tinggi dan
 * tersimpan sebagai REVIEWED — padahal layar Review menampilkan peringatan
 * "Selisih hitung". Kalau perilaku itu kembali, test ini yang menangkapnya.
 */
class ReceiptDraftNeedsReviewTest {

    @Test
    fun `hitungan tidak cocok wajib diperiksa walau confidence tinggi`() {
        val draft = ReceiptDraft(
            items = listOf(
                ReceiptItemDraft(name = "Aqua", quantity = 1, unitPriceMinor = 5_000, totalMinor = 5_000),
            ),
            taxMinor = 500,
            totalMinor = 99_000, // jauh dari item + pajak = 5.500
            confidence = FieldConfidence(
                merchant = 0.95f,
                date = 0.95f,
                items = 0.9f,
                tax = 0.9f,
                total = 0.5f,
            ),
        )
        // Rata-rata confidence saja masih 0.85 -> dulu lolos jadi REVIEWED.
        assertFalse(draft.confidence.needsReview, "prasyarat: rata-rata harus >= 0.7")
        assertFalse(draft.isReconciled, "prasyarat: hitungan harus tidak cocok")
        assertTrue(draft.needsReview, "struk yang tidak rekonsiliasi harus minta diperiksa")
    }

    @Test
    fun `confidence rendah wajib diperiksa`() {
        val draft = ReceiptDraft(
            totalMinor = 0,
            confidence = FieldConfidence(merchant = 0.2f, total = 0.2f),
        )
        assertTrue(draft.needsReview)
    }

    @Test
    fun `hitungan cocok dan confidence tinggi tidak perlu diperiksa`() {
        val draft = ReceiptDraft(
            items = listOf(
                ReceiptItemDraft(name = "Beras", quantity = 2, unitPriceMinor = 10_000, totalMinor = 20_000),
            ),
            taxMinor = 2_000,
            discountMinor = 1_000,
            totalMinor = 21_000, // 20.000 + 2.000 - 1.000
            confidence = FieldConfidence(
                merchant = 0.9f,
                date = 0.9f,
                items = 0.9f,
                tax = 0.9f,
                discount = 0.9f,
                total = 0.9f,
            ),
        )
        assertTrue(draft.isReconciled, "prasyarat: hitungan harus cocok")
        assertFalse(draft.needsReview)
    }

    @Test
    fun `total null tidak dianggap rekonsiliasi`() {
        val draft = ReceiptDraft(totalMinor = null)
        assertFalse(draft.isReconciled)
        assertTrue(draft.needsReview)
    }
}
