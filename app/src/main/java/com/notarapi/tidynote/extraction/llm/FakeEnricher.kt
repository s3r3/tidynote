package com.notarapi.tidynote.extraction.llm

import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.extraction.parser.ReceiptParser

/**
 * Fake untuk dev/Uji A-B corpus (plan Tahap 5): parser lokal + source MANUAL.
 * Dipakai saat backend belum diisi — agar flow "Perbaiki dengan AI" tetap
 * bisa dicoba tanpa mengirim apa pun ke jaringan.
 */
class FakeEnricher(
    private val parser: ReceiptParser = ReceiptParser(),
) : ReceiptEnricher {
    var calls = 0
        private set

    override suspend fun extractFromOcr(ocrText: String): ReceiptDraft {
        calls++
        return parser.parse(ocrText).copy(
            source = com.notarapi.tidynote.core.model.ExtractionSource.MANUAL_INPUT,
        )
    }
}
