package com.notarapi.tidynote.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mengunci perbaikan P1: rekonsiliasi memakai dasar hitung yang sama dengan
 * `ReceiptValidator` (subtotal cetak kalau ada, kalau tidak jumlah item) dan
 * toleransi pembulatan yang sama.
 *
 * Bug aslinya: `ReceiptDraft.isReconciled` hanya menjumlah item tanpa toleransi,
 * sementara validator memakai subtotal + toleransi Rp5. Hasilnya dua kesimpulan
 * berbeda untuk struk yang sama: validator menurunkan confidence (struk jadi
 * PENDING) padahal total yang tercetak sudah cocok, atau sebaliknya.
 */
class ReceiptDraftReconciliationBasisTest {
    private val goodConfidence =
        FieldConfidence(merchant = 0.9f, date = 0.9f, items = 0.8f, total = 0.85f)

    @Test
    fun `item yang terlewat di OCR tidak membuat struk valid jadi PENDING`() {
        // Struk tercetak: subtotal 20.000, total 20.000. Parser hanya menangkap
        // satu baris item (18.000) karena satu baris lain tidak terbaca.
        val draft =
            ReceiptDraft(
                items =
                    listOf(
                        ReceiptItemDraft(name = "Aqua", quantity = 1, unitPriceMinor = 18_000, totalMinor = 18_000),
                    ),
                subtotalMinor = 20_000,
                totalMinor = 20_000,
                confidence = goodConfidence,
            )

        assertTrue(draft.isReconciled, "subtotal cetak cocok dengan total; item yang bolong bukan alasan PENDING")
        assertEquals("REVIEWED", draft.reviewStatusForSave())
    }

    @Test
    fun `selisih pembulatan di bawah toleransi tetap dianggap cocok`() {
        val draft =
            ReceiptDraft(
                items =
                    listOf(
                        ReceiptItemDraft(name = "Kopi", quantity = 1, unitPriceMinor = 25_000, totalMinor = 25_000),
                    ),
                subtotalMinor = 25_000,
                totalMinor = 25_003,
                confidence = goodConfidence,
            )

        assertTrue(draft.isReconciled, "selisih Rp3 masih pembulatan, bukan salah hitung")
    }

    @Test
    fun `subtotal nol diperlakukan seperti tidak diisi`() {
        // Layar Review menulis subtotalMinor = 0 ketika field dikosongkan.
        // Nilai itu bukan dasar hitung, jadi harus jatuh kembali ke jumlah item.
        val draft =
            ReceiptDraft(
                items =
                    listOf(
                        ReceiptItemDraft(name = "Beras", quantity = 1, unitPriceMinor = 60_000, totalMinor = 60_000),
                    ),
                subtotalMinor = 0,
                totalMinor = 60_000,
                confidence = goodConfidence,
            )

        assertTrue(draft.isReconciled, "subtotal 0 tidak boleh membuat struk yang cocok jadi PENDING")
        assertEquals("REVIEWED", draft.reviewStatusForSave())
    }

    @Test
    fun `subtotal cetak yang benar-benar selisih tetap PENDING`() {
        val draft =
            ReceiptDraft(
                items =
                    listOf(
                        ReceiptItemDraft(name = "Teh", quantity = 1, unitPriceMinor = 10_000, totalMinor = 10_000),
                    ),
                subtotalMinor = 10_000,
                totalMinor = 15_000,
                confidence = goodConfidence,
            )

        assertFalse(draft.isReconciled)
        assertEquals("PENDING", draft.reviewStatusForSave())
    }

    @Test
    fun `tanpa item dan tanpa subtotal tidak diklaim cocok`() {
        assertFalse(ReceiptDraft(totalMinor = 50_000).isReconciled)
    }
}
