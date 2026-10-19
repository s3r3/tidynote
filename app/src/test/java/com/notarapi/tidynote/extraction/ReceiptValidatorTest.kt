package com.notarapi.tidynote.extraction

import com.notarapi.tidynote.core.model.FieldConfidence
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ReceiptItemDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ReceiptValidatorTest {
    @Test
    fun `tanggal masa depan ditolak`() {
        val draft =
            ReceiptDraft(
                transactionDate = Instant.now().plusSeconds(86400),
                confidence = FieldConfidence(date = 0.9f),
            )
        val out = ReceiptValidator.validate(draft)
        assertNull(out.transactionDate)
        assertTrue(out.confidence.date < 0.5f)
    }

    @Test
    fun `selisih total menurunkan confidence`() {
        val draft =
            ReceiptDraft(
                items = listOf(ReceiptItemDraft(name = "A", quantity = 1, unitPriceMinor = 10000, totalMinor = 10000)),
                subtotalMinor = 10000,
                totalMinor = 50000,
                confidence = FieldConfidence(total = 0.9f, items = 0.8f),
            )
        val out = ReceiptValidator.validate(draft)
        assertTrue(out.confidence.total < 0.9f)
    }

    @Test
    fun `nominal negatif menurunkan confidence`() {
        val draft =
            ReceiptDraft(
                totalMinor = -100,
                confidence = FieldConfidence(total = 0.9f),
            )
        val out = ReceiptValidator.validate(draft)
        assertTrue(out.confidence.total < 0.9f)
    }

    @Test
    fun `rekonsiliasi pas tidak menurunkan confidence`() {
        val draft =
            ReceiptDraft(
                items = listOf(ReceiptItemDraft(name = "A", quantity = 2, unitPriceMinor = 10000, totalMinor = 20000)),
                subtotalMinor = 20000,
                totalMinor = 20000,
                confidence = FieldConfidence(total = 0.9f, items = 0.8f),
            )
        val out = ReceiptValidator.validate(draft)
        assertEquals(0.9f, out.confidence.total)
    }
}
