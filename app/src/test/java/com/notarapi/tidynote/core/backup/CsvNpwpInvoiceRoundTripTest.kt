package com.notarapi.tidynote.core.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Mengunci T5: NPWP dan nomor invoice sekarang ikut bolak-balik lewat CSV.
 *
 * Sebelumnya CsvExporter menulis NPWP sebagai string kosong hardcoded (dengan
 * komentar "not in entity yet") dan tidak punya kolom nomor invoice sama sekali —
 * jadi backup menghilangkan dua data itu tanpa memberi tahu siapa pun.
 *
 * Baris lama (10 kolom) juga harus tetap bisa dibaca; file backup yang dibuat
 * versi sebelumnya tidak boleh mendadak tidak bisa dipulihkan.
 *
 * Catatan format: [com.notarapi.tidynote.core.util.formatDate] memakai pola
 * "dd MMM yyyy, HH:mm" yang MENGANDUNG KOMA, jadi kolom tanggal selalu dikutip
 * ganda oleh CsvExporter. Baris uji di sini harus meniru itu — kalau tidak,
 * pemisah kolom bergeser satu dan seluruh baris salah dibaca.
 */
class CsvNpwpInvoiceRoundTripTest {

    private val header = "Tanggal,Merchant,NPWP,Kategori,Subtotal,Diskon,PPN,Total,Status,Teks OCR,No Invoice"

    /** Tanggal selalu dalam kutip ganda, persis seperti keluaran CsvExporter. */
    private val tanggal = "\"12 Jan 2026, 14:30\""

    @Test
    fun `npwp dan nomor invoice terbaca dari baris 11 kolom`() {
        val csv = "$header\n" +
            "$tanggal,TOKO SEGAR,01.234.567.8-901.000,1,20000,0,2000,22000,REVIEWED,beras 2x10000,INV-2026-001"

        val rows = CsvBackupParser.parse(csv)
        assertEquals(1, rows.size)
        val draft = rows.single().first
        assertEquals("TOKO SEGAR", draft.merchantName)
        assertEquals("01.234.567.8-901.000", draft.npwp)
        assertEquals("INV-2026-001", draft.invoiceNumber)
    }

    @Test
    fun `baris 10 kolom lama tetap terbaca tanpa npwp`() {
        val csv = "$header\n" +
            "$tanggal,TOKO LAMA,,1,20000,0,2000,22000,REVIEWED,beras 2x10000"

        val rows = CsvBackupParser.parse(csv)
        assertEquals(1, rows.size)
        val draft = rows.single().first
        assertEquals("TOKO LAMA", draft.merchantName)
        // Kolom NPWP ada tapi kosong -> null, bukan string kosong.
        assertNull(draft.npwp)
        // Kolom invoice tidak ada sama sekali -> null, bukan gagal parse.
        assertNull(draft.invoiceNumber)
    }

    @Test
    fun `kolom opsional kosong jadi null bukan string kosong`() {
        val csv = "$header\n" +
            "$tanggal,TOKO,,1,20000,0,2000,22000,PENDING,teks,   "

        val draft = CsvBackupParser.parse(csv).single().first
        assertNull(draft.npwp, "spasi saja harus jadi null")
        assertNull(draft.invoiceNumber, "spasi saja harus jadi null")
    }

    @Test
    fun `nomor invoice dengan koma tetap utuh`() {
        val csv = "$header\n" +
            "$tanggal,TOKO,,1,20000,0,2000,22000,REVIEWED,teks,\"INV,2026,001\""

        val draft = CsvBackupParser.parse(csv).single().first
        assertEquals("INV,2026,001", draft.invoiceNumber)
    }

    @Test
    fun `tanggal berkoma tidak menggeser kolom sesudahnya`() {
        // Regresi halus: kalau kutip tanggal hilang (mis. ada yang menulis ulang
        // exporter dan lupa escapeCsv), koma di dalam tanggal memecah baris jadi
        // satu kolom ekstra dan SEMUA kolom sesudahnya bergeser — merchant jadi
        // berisi jam, dst. Test ini mengunci bahwa tanggal memang dikutip.
        val csv = "$header\n" +
            "$tanggal,TOKO SEGAR,,1,20000,0,2000,22000,REVIEWED,teks,INV-1"

        val draft = CsvBackupParser.parse(csv).single().first
        assertEquals("TOKO SEGAR", draft.merchantName, "kutip tanggal hilang -> kolom bergeser")
        assertEquals(22000L, draft.totalMinor)
        assertEquals("INV-1", draft.invoiceNumber)
    }
}
