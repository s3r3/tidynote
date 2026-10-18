package com.notarapi.tidynote.extraction

import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ExtractionSource
import com.notarapi.tidynote.extraction.llm.ReceiptEnricher
import com.notarapi.tidynote.extraction.ocr.OcrHelper
import com.notarapi.tidynote.extraction.parser.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtractionOrchestrator(
    private val ocrHelper: OcrHelper,
    private val localParser: ReceiptParser,
    private val llmExtractor: ReceiptEnricher?
) {

    private val CONFIDENCE_THRESHOLD = 0.7f

    suspend fun extractFromImage(imagePath: String): ReceiptDraft = withContext(Dispatchers.IO) {
        val ocrText = ocrHelper.recognizeTextFromPath(imagePath)
        extractFromOcrText(ocrText, imagePath)
    }

    suspend fun extractFromUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver): ReceiptDraft = withContext(Dispatchers.IO) {
        val ocrText = ocrHelper.recognizeTextFromUri(contentResolver, uri)
        extractFromOcrText(ocrText, "")
    }

    private suspend fun extractFromOcrText(ocrText: String, imagePath: String): ReceiptDraft {
        // Try local parser first
        val localResult = localParser.parse(ocrText).copy(
            rawOcrText = ocrText,
            imagePath = imagePath
        )
        val validatedLocal = ReceiptValidator.validate(localResult)

        // If confidence is good and reconciled, return local result
        if (validatedLocal.confidence.average >= CONFIDENCE_THRESHOLD && validatedLocal.isReconciled) {
            return validatedLocal.copy(source = ExtractionSource.LOCAL_PARSER)
        }

        // Try LLM fallback if available (plan.md: hanya dengan persetujuan eksplisit,
        // kirim teks OCR saja, bukan foto).
        llmExtractor?.let { llm ->
            try {
                val llmRaw = llm.extractFromOcr(ocrText)
                val llmResult = ReceiptValidator.validate(
                    llmRaw.copy(rawOcrText = ocrText, imagePath = imagePath)
                )
                // Return whichever has higher confidence
                if (llmResult.confidence.average > validatedLocal.confidence.average) {
                    return llmResult.copy(source = ExtractionSource.LLM_FALLBACK)
                }
            } catch (e: Exception) {
                // LLM failed, fall through to local result
            }
        }

        // Return local result with low confidence flag (tetap LOCAL_PARSER agar
        // tidak dikira hasil AI; review manusia wajib sebelum simpan).
        return validatedLocal.copy(source = ExtractionSource.LOCAL_PARSER)
    }
}