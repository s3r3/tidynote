package com.notarapi.tidynote.feature.scan

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.extraction.ExtractionOrchestrator
import com.notarapi.tidynote.extraction.scanner.ScannerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    scannerHelper: ScannerHelper,
    orchestrator: ExtractionOrchestrator,
    onNavigateBack: () -> Unit,
    onNavigateToReview: (ReceiptDraft) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scannedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var capturedImagePath by remember { mutableStateOf("") }
    var galleryError by remember { mutableStateOf<String?>(null) }

    suspend fun processUri(uri: android.net.Uri) {
        isProcessing = true
        galleryError = null
        // 1) Decode gambar (kritis). Kalau ini gagal, memang tak ada yang bisa ditampilkan.
        val bitmap =
            try {
                withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri, 1024) }
            } catch (_: Exception) {
                null
            }
        if (bitmap == null) {
            galleryError = "Gagal membaca gambar. Coba gambar lain atau potret struk."
            isProcessing = false
            return
        }
        val savedPath = withContext(Dispatchers.IO) { saveBitmapToFile(context, bitmap) }
        capturedImagePath = savedPath
        capturedBitmap?.recycle()
        capturedBitmap = bitmap

        // 2) OCR (best-effort). Kalau gagal, tetap tampilkan gambar agar user bisa
        //    isi manual — jangan hilangkan gambar yang sudah dipilih.
        val draft =
            try {
                orchestrator.extractFromUri(uri, context.contentResolver)
            } catch (_: Exception) {
                ReceiptDraft(rawOcrText = "", imagePath = savedPath)
            }
        scannedText = draft.rawOcrText
        isProcessing = false
        onNavigateToReview(draft.copy(imagePath = savedPath))
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri == null) {
                galleryError = "Tidak ada gambar yang dipilih."
                return@rememberLauncherForActivityResult
            }
            scope.launch { processUri(uri) }
        }

    val scannerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                        .fromActivityResultIntent(result.data)
                val uri = scanResult?.pages?.firstOrNull()?.imageUri
                if (uri != null) {
                    scope.launch { processUri(uri) }
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (capturedBitmap != null) stringResource(R.string.review_scan) else stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        if (capturedBitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(128.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(percent = 50),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.place_receipt),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.auto_detect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            activity?.let { act ->
                                scope.launch {
                                    try {
                                        val intentSender = scannerHelper.getScanIntentSender(act)
                                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                    } catch (e: Exception) {
                                        scannedText = "Scanner unavailable: ${e.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.CameraAlt, contentDescription = "Buka scanner kamera")
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.open_scanner))
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            galleryError = null
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = "Pilih dari galeri")
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pick_from_gallery))
                    }
                    galleryError?.let { msg ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.scanned_receipt),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.edit_values),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (capturedImagePath.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.receipt_image), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            // Coil: decode di background + cache. Sebelumnya BitmapFactory
                            // dipanggil langsung di body composable, jadi gambar full-res
                            // di-decode ulang setiap recomposition.
                            coil.compose.AsyncImage(
                                model = java.io.File(capturedImagePath),
                                contentDescription = stringResource(R.string.receipt_image),
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedBitmap = null
                            scannedText = ""
                            capturedImagePath = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan ulang")
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rescan))
                    }
                    Button(
                        onClick = {
                            val draft =
                                ReceiptDraft(
                                    merchantName = "Tidak Dikenal",
                                    rawOcrText = scannedText,
                                    imagePath = capturedImagePath,
                                )
                            onNavigateToReview(draft)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit manual")
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.edit_manually))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun saveBitmapToFile(
    context: Context,
    bitmap: Bitmap,
): String {
    // Path lewat ReceiptImages supaya penulis & pembaca (Settings, deleteAll)
    // tidak bisa lagi memakai direktori berbeda seperti sebelumnya.
    val file =
        com.notarapi.tidynote.core.storage.ReceiptImages
            .newFile(context)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return file.absolutePath
}

private fun decodeSampledBitmap(
    context: Context,
    uri: android.net.Uri,
    maxDim: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    }
}
