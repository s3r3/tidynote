package com.notarapi.tidynote.core.backup

import com.notarapi.tidynote.core.model.ExtractionSource
import com.notarapi.tidynote.core.model.ReceiptDraft
import java.time.ZoneId
import java.util.Locale

/**
 * Parser CSV ekspor TidyNote -> [ReceiptDraft] untuk restore (tulis balik ke DB).
 * Kolom (lihat CsvExporter):
 * Tanggal,Merchant,NPWP,Kategori,Subtotal,Diskon,PPN,Total,Status,Teks OCR,No Invoice
 *
 * Kolom NPWP dan No Invoice ditambahkan belakangan, jadi baris lama yang hanya
 * punya 10 kolom tetap harus bisa dibaca — karena itu semua akses kolom opsional
 * memakai getOrNull.
 */
object CsvBackupParser {
    /** Kolom minimum agar sebuah baris dianggap punya data yang bisa dipulihkan. */
    private const val MIN_COLUMNS = 9

    private val DATE_FMT =
        java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

    fun parse(csv: String): List<Pair<ReceiptDraft, String>> {
        val lines = csv.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()
        return lines.drop(1).mapNotNull { parseRow(it) }
    }

    private fun parseRow(row: String): Pair<ReceiptDraft, String>? {
        val cols = splitCsvRow(row)
        if (cols.size < MIN_COLUMNS) return null
        val instant =
            runCatching {
                java.time.LocalDateTime
                    .parse(cols[0].trim(), DATE_FMT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }.getOrNull()
        val status = cols[8].trim().takeIf { it.isNotBlank() } ?: "PENDING"
        val draft =
            ReceiptDraft(
                merchantName = cols[1].trim().ifBlank { null },
                npwp = cols.getOrNull(2)?.trim()?.ifBlank { null },
                transactionDate = instant,
                currency = "IDR",
                items = emptyList(),
                subtotalMinor = cols[4].trim().toLongOrNull()?.takeIf { it > 0 },
                discountMinor = cols[5].trim().toLongOrNull(),
                taxMinor = cols[6].trim().toLongOrNull(),
                totalMinor = cols[7].trim().toLongOrNull(),
                rawOcrText = cols.getOrNull(9).orEmpty(),
                invoiceNumber = cols.getOrNull(10)?.trim()?.ifBlank { null },
                source = ExtractionSource.MANUAL_INPUT,
            )
        return draft to status
    }

    /** Pemisah CSV yang menghormati tanda kutip ganda. */
    private fun splitCsvRow(row: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < row.length && row[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
