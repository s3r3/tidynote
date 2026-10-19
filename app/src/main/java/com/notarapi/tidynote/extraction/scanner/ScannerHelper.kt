package com.notarapi.tidynote.extraction.scanner

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ScannerHelper {
    private val scannerOptions: GmsDocumentScannerOptions =
        GmsDocumentScannerOptions
            .Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setGalleryImportAllowed(true)
            .build()

    private val scannerClient: GmsDocumentScanner = GmsDocumentScanning.getClient(scannerOptions)

    suspend fun getScanIntentSender(activity: Activity): IntentSender =
        withContext(Dispatchers.IO) {
            scannerClient.getStartScanIntent(activity).await()
        }
}
