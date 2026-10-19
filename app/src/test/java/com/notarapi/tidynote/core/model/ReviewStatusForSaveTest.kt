package com.notarapi.tidynote.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Mengunci perbaikan: menyimpan ulang struk yang sudah REVIEWED tidak boleh
 * menurunkannya ke PENDING.
 *
 * Bug aslinya: `getDraftForEdit` hanya menyimpan rata-rata confidence
 * (`FieldConfidence(total = r.confidence)`), jadi struk ber-confidence rendah
 * selalu `needsReview == true`. Akibatnya setiap Simpan dari layar edit
 * membalik REVIEWED -> PENDING, tepat setelah user menandainya sudah diperiksa.
 */
class ReviewStatusForSaveTest {
    private fun draft(
        total: Long = 10_000,
        confidence: Float = 0.9f,
    ) = ReceiptDraft(
        items = listOf(ReceiptItemDraft(name = "Aqua", quantity = 1, unitPriceMinor = total, totalMinor = total)),
        totalMinor = total,
        confidence = FieldConfidence(total = confidence),
    )

    @Test
    fun `REVIEWED bertahan saat simpan ulang tanpa ubah angka`() {
        // confidence rendah mensimulasikan rekonstruksi dari rata-rata tersimpan
        assertEquals("REVIEWED", draft(confidence = 0.3f).reviewStatusForSave("REVIEWED"))
    }

    @Test
    fun `struk baru dengan confidence rendah tetap PENDING`() {
        assertEquals("PENDING", draft(confidence = 0.3f).reviewStatusForSave())
    }

    @Test
    fun `hitungan tidak cocok menurunkan REVIEWED ke PENDING`() {
        val broken = draft(total = 10_000).copy(totalMinor = 99_000)
        assertEquals("PENDING", broken.reviewStatusForSave("REVIEWED"))
    }

    @Test
    fun `struk baru yang rekonsiliasi dan confidence tinggi jadi REVIEWED`() {
        assertEquals("REVIEWED", draft(confidence = 0.9f).reviewStatusForSave())
    }

    @Test
    fun `PENDING naik ke REVIEWED hanya saat disimpan dengan confidence tinggi`() {
        // Disengaja: menekan Simpan di layar Review adalah tindakan manusia.
        // Sama dengan saveDraft untuk struk baru yang rekonsiliasi + confidence tinggi.
        assertEquals("REVIEWED", draft(confidence = 0.9f).reviewStatusForSave("PENDING"))
    }

    @Test
    fun `PENDING bertahan bila confidence masih rendah`() {
        assertEquals("PENDING", draft(confidence = 0.3f).reviewStatusForSave("PENDING"))
    }
}
