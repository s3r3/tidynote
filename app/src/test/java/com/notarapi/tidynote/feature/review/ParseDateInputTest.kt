package com.notarapi.tidynote.feature.review

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Mengunci perbaikan P1: field tanggal di layar Review dulu menerima ketikan
 * user tapi tidak pernah memanggil onEdit, jadi nilainya hilang saat Save.
 *
 * Parser ini sengaja mengembalikan null untuk input yang belum lengkap supaya
 * pemanggil TIDAK menimpa tanggal valid dengan hasil ketikan setengah jadi.
 */
class ParseDateInputTest {
    private val jakarta: ZoneId = ZoneId.of("Asia/Jakarta")

    @Test
    fun `format yang ditampilkan formatDate bisa dibaca balik`() {
        // formatDate menghasilkan "dd MMM yyyy, HH:mm" dengan locale id-ID.
        val parsed = parseDateInput("12 Jan 2026, 14:30", jakarta)
        assertEquals(Instant.parse("2026-01-12T07:30:00Z"), parsed)
    }

    @Test
    fun `tanggal tanpa jam dianggap tengah malam`() {
        val parsed = parseDateInput("12 Jan 2026", jakarta)
        assertEquals(Instant.parse("2026-01-11T17:00:00Z"), parsed) // 12 Jan 00:00 WIB
    }

    @Test
    fun `format angka juga diterima`() {
        assertEquals(
            parseDateInput("12/01/2026", jakarta),
            parseDateInput("12 Jan 2026", jakarta),
        )
    }

    @Test
    fun `format ISO juga diterima`() {
        assertEquals(
            parseDateInput("2026-01-12", jakarta),
            parseDateInput("12 Jan 2026", jakarta),
        )
    }

    @Test
    fun `input kosong tidak menghasilkan tanggal`() {
        assertNull(parseDateInput(""))
        assertNull(parseDateInput("   "))
    }

    @Test
    fun `ketikan setengah jadi tidak menghasilkan tanggal`() {
        // Ini yang melindungi tanggal valid dari ditimpa sampah saat user mengetik.
        assertNull(parseDateInput("12"))
        assertNull(parseDateInput("12 Jan"))
        assertNull(parseDateInput("abcdef"))
        assertNull(parseDateInput("32 Jan 2026"))
    }

    @Test
    fun `spasi berlebih tetap terbaca`() {
        assertEquals(
            parseDateInput("12 Jan 2026", jakarta),
            parseDateInput("  12   Jan   2026  ", jakarta),
        )
    }
}
