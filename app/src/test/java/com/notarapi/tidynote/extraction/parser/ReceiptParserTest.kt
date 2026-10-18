package com.notarapi.tidynote.extraction.parser

import com.notarapi.tidynote.core.model.ExtractionSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReceiptParserTest {

    private val parser = ReceiptParser()

    @Test
    fun `parse menyimpan rupiah tanpa dikali 100`() {
        val text = """
            TOKO MAKMUR
            Nasi Goreng 15.000
            Teh Manis 5.000
            Subtotal Rp 20.000
            Total Rp 20.000
        """.trimIndent()

        val draft = parser.parse(text)

        assertEquals(20000L, draft.totalMinor)
        assertEquals(20000L, draft.subtotalMinor)
        assertEquals("IDR", draft.currency)
        assertEquals(ExtractionSource.LOCAL_PARSER, draft.source)
    }

    @Test
    fun `parse menangani format 25_000 sebagai 25000`() {
        val text = """
            WARUNG BU TEJO
            Mie Ayam 25.000
            Total 25.000
        """.trimIndent()

        val draft = parser.parse(text)

        assertEquals(25000L, draft.totalMinor)
        assertTrue(draft.items.isNotEmpty())
    }

    @Test
    fun `parse kosong tidak crash dan confidence rendah`() {
        val draft = parser.parse("")

        assertNull(draft.totalMinor)
        assertTrue(draft.items.isEmpty())
        assertTrue(draft.confidence.needsReview)
    }
}

