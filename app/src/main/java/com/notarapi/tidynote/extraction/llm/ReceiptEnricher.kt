package com.notarapi.tidynote.extraction.llm

import com.notarapi.tidynote.core.model.ReceiptDraft

/**
 * plan.md §7.5: LLM hanya fallback opsional, kirim teks OCR saja (bukan foto),
 * hanya dengan persetujuan eksplisit pengguna, default mati.
 */
fun interface ReceiptEnricher {
    suspend fun extractFromOcr(ocrText: String): ReceiptDraft
}
