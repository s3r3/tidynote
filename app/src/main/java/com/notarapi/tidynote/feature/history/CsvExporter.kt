package com.notarapi.tidynote.feature.history

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.notarapi.tidynote.core.database.ReceiptRow
import com.notarapi.tidynote.core.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvExporter {

    suspend fun exportReceipts(
        receipts: List<ReceiptRow>,
        contentResolver: ContentResolver
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val csv = buildCsv(receipts)
            val fileName = "tidynote_export_${java.time.LocalDate.now()}.csv"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create file"))
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csv.toByteArray())
            }
            
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun buildCsv(receipts: List<ReceiptRow>): String {
        val headers = listOf(
            "Tanggal", "Merchant", "NPWP", "Kategori", "Subtotal", "Diskon", "PPN", "Total", "Status", "Teks OCR"
        )
        
        val rows = receipts.map { rwm ->
            val r = rwm.receipt
            val m = rwm.merchant
            listOf(
                escapeCsv(formatDate(r.transactionDate)),
                escapeCsv(m?.name ?: ""),
                "", // NPWP not in entity yet
                escapeCsv(r.categoryId?.toString() ?: ""),
                (r.subtotalMinor ?: 0).toString(),
                (r.discountMinor ?: 0).toString(),
                (r.taxMinor ?: 0).toString(),
                (r.totalMinor ?: 0).toString(),
                r.reviewStatus,
                escapeCsv(r.rawOcrText.replace("\n", " "))
            ).joinToString(",")
        }
        
        return (headers.joinToString(",") + "\n" + rows.joinToString("\n"))
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}