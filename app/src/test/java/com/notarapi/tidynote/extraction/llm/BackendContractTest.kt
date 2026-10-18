package com.notarapi.tidynote.extraction.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackendContractTest {

    @Test
    fun `url memakai path baru dan legacy`() {
        assertEquals(
            "https://x.example.com/v1/receipts/parse",
            BackendContract.url("https://x.example.com/"),
        )
        assertEquals(
            "https://x.example.com/v1/receipt/extract",
            BackendContract.url("https://x.example.com/", legacy = true),
        )
    }

    @Test
    fun `request menolak ocr kosong`() {
        assertThrows(IllegalArgumentException::class.java) { BackendContract.request("   ") }
    }

    @Test
    fun `request memotong ocr tepat batas`() {
        val ocr = "x".repeat(BackendContract.MAX_OCR_CHARS + 100)
        val req = BackendContract.request(ocr)
        assertEquals(BackendContract.MAX_OCR_CHARS, req.ocrText.length)
        assertEquals("IDR", req.currency)
        assertEquals(1, req.schemaVersion)
    }

    @Test
    fun `extractProviderContent membaca content choices pertama`() {
        val provider = """
            {"choices":[{"message":{"role":"assistant","content":"{\"totalMinor\":25000}"}}]}
        """.trimIndent()
        assertEquals("{\"totalMinor\":25000}", BackendContract.extractProviderContent(provider))
    }

    @Test
    fun `extractProviderContent kosong bila format tak dikenal`() {
        assertEquals("", BackendContract.extractProviderContent("{}"))
        assertEquals("", BackendContract.extractProviderContent("not json"))
        assertEquals("", BackendContract.extractProviderContent("{\"choices\":[]}"))
    }

    @Test
    fun `devSystemPrompt memuat instruksi IDR dan json`() {
        val p = BackendContract.devSystemPrompt()
        assertTrue(p.contains("IDR"))
        assertTrue(p.contains("JSON", ignoreCase = true))
        assertTrue(p.contains("minor", ignoreCase = true))
    }
}
