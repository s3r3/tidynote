package com.notarapi.tidynote.core.serialization

import com.notarapi.tidynote.core.model.ReceiptDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Memastikan [ReceiptDraft] (yang memuat field `@Contextual Instant?`) bisa
 * di-encode/decode tanpa crash — regresi dari bug "Serializer for class 'Instant'".
 */
class ReceiptDraftInstantSerializationTest {

    @Test
    fun `encode-decode draft dengan tanggal ISO berhasil`() {
        val when0 = Instant.parse("2024-01-15T10:30:00Z")
        val draft = ReceiptDraft(
            merchantName = "Warung Bu Sari",
            transactionDate = when0,
            totalMinor = 25000L,
            categoryId = 1L,
            rawOcrText = "TOTAL 25.000",
        )

        val json = AppJson.encodeToString(ReceiptDraft.serializer(), draft)
        val back = AppJson.decodeFromString(ReceiptDraft.serializer(), json)

        assertEquals("Warung Bu Sari", back.merchantName)
        assertEquals(25000L, back.totalMinor)
        assertNotNull(back.transactionDate)
        assertEquals(when0, back.transactionDate)
    }

    @Test
    fun `draft tanpa tanggal tetap aman`() {
        val draft = ReceiptDraft(merchantName = "Toko A", totalMinor = 1000L)
        val json = AppJson.encodeToString(ReceiptDraft.serializer(), draft)
        val back = AppJson.decodeFromString(ReceiptDraft.serializer(), json)
        assertEquals(null, back.transactionDate)
        assertEquals("Toko A", back.merchantName)
    }

    @Test
    fun `tanggal dari epoch millis terbaca`() {
        val millis = 1705314600000L
        val expected = Instant.ofEpochMilli(millis)
        val encoded = AppJson.encodeToString(InstantSerializer, expected)
        // Serializer menulis ISO; pastikan parser menerima bentuk ISO hasil serialize.
        val back = AppJson.decodeFromString(InstantSerializer, encoded)
        assertEquals(expected, back)
        // Bentuk mentah epoch millis (data lama) juga harus terbaca.
        val legacy = AppJson.decodeFromString(InstantSerializer, "\"$millis\"")
        assertEquals(expected, legacy)
    }
}
