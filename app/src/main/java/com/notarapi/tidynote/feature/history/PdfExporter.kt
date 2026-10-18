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
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfExporter {
    suspend fun exportReceipts(
        receipts: List<ReceiptRow>,
        contentResolver: ContentResolver
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40
            var pageNumber = 1

            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = margin

            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
            }
            val headerPaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11f
            }
            val bodyPaint = Paint().apply { textSize = 11f }

            fun newPageIfNeeded(needed: Float) {
                if (y + needed > pageHeight - margin) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = margin
                }
            }

            canvas.drawText("NotaRapi - Rekap Struk", margin.toFloat(), y.toFloat(), titlePaint)
            y += 26
            canvas.drawText("Total: ${receipts.size} struk", margin.toFloat(), y.toFloat(), bodyPaint)
            y += 24

            for (row in receipts) {
                newPageIfNeeded(72f)
                val r = row.receipt
                canvas.drawText(formatDate(r.transactionDate), margin.toFloat(), y.toFloat(), headerPaint)
                y += 16
                canvas.drawText(
                    "${row.merchant?.name ?: "Tidak dikenal"} - ${formatCurrency(r.totalMinor ?: 0)}",
                    margin.toFloat(), y.toFloat(), bodyPaint
                )
                y += 16
                canvas.drawText(
                    "Status: ${r.reviewStatus}",
                    margin.toFloat(), y.toFloat(), bodyPaint
                )
                y += 22
            }
            document.finishPage(page)

            val fileName = "tidynote_rekap_${java.time.LocalDate.now()}.pdf"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Gagal membuat file"))
            contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
