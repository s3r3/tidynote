package com.notarapi.tidynote.core.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CsvBackupParserTest {

    @Test
    fun `parse baris valid jadi draft`() {
        // formatDate menghasilkan "dd MMM yyyy, HH:mm" → kini di-quote exporter.
        val csv = """
            Tanggal,Merchant,NPWP,Kategori,Subtotal,Diskon,PPN,Total,Status,Teks OCR
            "01 Jan 2025, 08:00",TOKO MAKMUR,,,20000,,0,20000,REVIEWED,TOTAL 20000
        """.trimIndent()
        val rows = CsvBackupParser.parse(csv)
        assertEquals(1, rows.size)
        val (draft, status) = rows[0]
        assertEquals("TOKO MAKMUR", draft.merchantName)
        assertEquals(20000L, draft.totalMinor)
        assertEquals("REVIEWED", status)
    }

    @Test
    fun `kolom kurang dari 9 dilewati`() {
        val csv = "Tanggal,Merchant\n01 Jan 2025,ONLYTWO"
        val rows = CsvBackupParser.parse(csv)
        assertEquals(0, rows.size)
    }

    @Test
    fun `teks OCR berkoma dalam kutip tidak pecah kolom`() {
        val csv = """
            Tanggal,Merchant,NPWP,Kategori,Subtotal,Diskon,PPN,Total,Status,Teks OCR
            "01 Jan 2025, 08:00",TOKO X,,,5000,,0,5000,PENDING,"BAYAR, TUNAI, 5000"
        """.trimIndent()
        val rows = CsvBackupParser.parse(csv)
        assertEquals(1, rows.size)
        val (draft, _) = rows[0]
        assertEquals("BAYAR, TUNAI, 5000", draft.rawOcrText)
        assertEquals(5000L, draft.totalMinor)
    }
}
