package com.notarapi.tidynote.feature.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notarapi.tidynote.R
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository: ReceiptRepository = koinInject()
    val scope = rememberCoroutineScope()

    var useCloudAi by remember { mutableStateOf(getCloudAiEnabled(context)) }
    var llmApiKey by remember { mutableStateOf(getSavedBackendToken(context)) }
    var llmBackendUrl by remember { mutableStateOf(getSavedBackendUrl(context)) }
    var directApiKey by remember { mutableStateOf(getSavedDirectApiKey(context)) }
    var directBaseUrl by remember { mutableStateOf(getSavedDirectBaseUrl(context)) }
    var directModel by remember { mutableStateOf(getSavedDirectModel(context)) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var storageSize by remember { mutableStateOf("Menghitung...") }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    val backupLauncher =
        rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    backupMessage =
                        try {
                            val input =
                                context.contentResolver.openInputStream(uri)
                                    ?: error("Tidak bisa baca file")
                            val tmp =
                                java.io.File(
                                    context.cacheDir,
                                    "restore${com.notarapi.tidynote.core.backup.EncryptedBackup.EXTENSION}",
                                )
                            tmp.outputStream().use { out -> input.copyTo(out) }
                            val csv =
                                com.notarapi.tidynote.core.backup.EncryptedBackup
                                    .importEncrypted(tmp, backupPassphrase)
                            val rows =
                                com.notarapi.tidynote.core.backup.CsvBackupParser
                                    .parse(csv)
                            val n = repository.restoreFromBackup(rows)
                            "Berhasil memulihkan $n struk"
                        } catch (e: Exception) {
                            "Gagal pulihkan: ${e.message ?: "passphrase salah / file rusak"}"
                        }
                }
            }
        }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        storageSize = calculateStorageSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                        )
                        Text(
                            stringResource(R.string.cloud_ai),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(stringResource(R.string.cloud_ai), fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.cloud_ai_summary),
                                    fontSize = 12.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = useCloudAi,
                                onCheckedChange = {
                                    useCloudAi = it
                                    saveCloudAiEnabled(context, it)
                                },
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                    if (useCloudAi) {
                        androidx.compose.material3.TextField(
                            value = llmBackendUrl,
                            onValueChange = {
                                llmBackendUrl = it
                                saveBackendConfig(context, it, llmApiKey)
                            },
                            label = { Text("URL backend") },
                            placeholder = { Text("https://backend-anda.example.com") },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            singleLine = true,
                        )
                        androidx.compose.material3.TextField(
                            value = llmApiKey,
                            onValueChange = {
                                llmApiKey = it
                                saveBackendConfig(context, llmBackendUrl, it)
                            },
                            label = { Text("Token backend (sementara)") },
                            placeholder = { Text("Token dari backend Anda setelah login") },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation,
                        )
                        Text(
                            "Opsional. Default mati. Jika diaktifkan, hanya teks OCR " +
                                "(maks 8000 karakter, bukan foto) yang dikirim ke backend " +
                                "milik Anda setelah Anda menekan Perbaiki dengan AI.",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ATAU langsung pakai API key (tanpa backend)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Isi API key provider OpenAI-compatible (mis. OpenAI/Groq/OpenRouter). " +
                                "Key disimpan di HP ini saja, tidak ikut ke dalam APK. " +
                                "Hanya teks OCR yang dikirim setelah Anda menekan Perbaiki dengan AI.",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        androidx.compose.material3.TextField(
                            value = directApiKey,
                            onValueChange = {
                                directApiKey = it
                                saveDirectConfig(context, it, directBaseUrl, directModel)
                            },
                            label = { Text("API key") },
                            placeholder = { Text("sk-xxxxxxxx") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation,
                        )
                        androidx.compose.material3.TextField(
                            value = directBaseUrl,
                            onValueChange = {
                                directBaseUrl = it
                                saveDirectConfig(context, directApiKey, it, directModel)
                            },
                            label = { Text("Base URL provider") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                        )
                        androidx.compose.material3.TextField(
                            value = directModel,
                            onValueChange = {
                                directModel = it
                                saveDirectConfig(context, directApiKey, directBaseUrl, it)
                            },
                            label = { Text("Model") },
                            placeholder = { Text("gpt-4o-mini") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                        )
                        // Status sumber AI (plan §7.5/§9): transparan ke pengguna.
                        Text(
                            "Sumber AI: " +
                                com.notarapi.tidynote.extraction.llm.LlmConfig
                                    .sourceLabel(llmBackendUrl, llmApiKey, directApiKey),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (com.notarapi.tidynote.extraction.llm.LlmConfig.hasDevKey &&
                            !(llmBackendUrl.isNotBlank() && llmApiKey.isNotBlank())
                        ) {
                            Text(
                                "Peringatan: build ini memuat API key mode development di " +
                                    "dalam APK (dari local.properties). Bisa dibongkar orang lain " +
                                    "dan memakai kuota Anda. Hanya untuk internal/demo — untuk " +
                                    "publik, kosongkan LLM_API_KEY di local.properties lalu build ulang.",
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                        )
                        Text(
                            stringResource(R.string.privacy),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Text(stringResource(R.string.privacy), fontWeight = FontWeight.Bold)
                        Text(
                            "Data tersimpan lokal di perangkat. Tidak ada data yang dikirim ke server tanpa izin.",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Ukuran penyimpanan: $storageSize",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                        )
                        Text(
                            "Backup terenkripsi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Cadangkan semua struk jadi satu file .tnbak yang dienkripsi dengan passphrase Anda.",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.TextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase backup (min 4 karakter)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    backupMessage =
                                        try {
                                            val rows = repository.getReceiptsForExport(null)
                                            val csv =
                                                com.notarapi.tidynote.feature.history
                                                    .CsvExporter()
                                                    .buildCsv(rows)
                                            val file =
                                                com.notarapi.tidynote.core.backup.EncryptedBackup.exportEncrypted(
                                                    context,
                                                    csv,
                                                    backupPassphrase,
                                                )
                                            "Backup dibuat: ${file.name}"
                                        } catch (e: Exception) {
                                            "Gagal: ${e.message ?: "isi passphrase dulu"}"
                                        }
                                }
                            },
                            enabled = backupPassphrase.length >= 4,
                        ) { Text("Buat backup") }
                        OutlinedButton(
                            onClick = {
                                backupLauncher.launch(arrayOf("application/octet-stream"))
                            },
                        ) { Text("Pulihkan") }
                    }
                    backupMessage?.let { msg ->
                        Text(
                            msg,
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                        )
                        Text(
                            stringResource(R.string.delete_all_data),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.delete_all_data),
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(R.string.delete_all_data_confirm),
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isDeleting,
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                Text(stringResource(R.string.delete_all_data))
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                        )
                        Text(
                            stringResource(R.string.about),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Text(stringResource(R.string.app_name_full), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.version) + " 1.0.0",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Offline-first receipt scanner untuk UMKM Indonesia",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Dulu tombol "Hapus semua data" langsung menghapus tanpa konfirmasi:
    // showDeleteConfirm dideklarasikan tapi tidak pernah dibaca, dan
    // delete_all_data_confirm cuma dipajang sebagai teks statis. Data yang
    // terhapus tidak bisa dikembalikan, jadi konfirmasi eksplisit wajib.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_all_data)) },
            text = { Text(stringResource(R.string.delete_all_data_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        isDeleting = true
                        scope.launch {
                            try {
                                repository.deleteAll()
                                storageSize = calculateStorageSize(context)
                            } finally {
                                isDeleting = false
                            }
                        }
                    },
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        ),
                ) { Text(stringResource(R.string.delete_all_data)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private suspend fun calculateStorageSize(context: Context): String =
    withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("tidynote-db")
        // WAL + SHM ikut dihitung: Room berjalan dalam mode WAL, dan keduanya bisa
        // sebesar database itu sendiri. Dulu hanya file utama yang dihitung.
        val dbBytes =
            listOf(
                dbFile,
                java.io.File("${dbFile.path}-wal"),
                java.io.File("${dbFile.path}-shm"),
            ).sumOf { it.length() }

        // Dulu di sini memakai context.getDir("receipts") yang menghasilkan
        // files/app_receipts, sedangkan ScanScreen menulis ke files/receipts.
        // Direktorinya beda, jadi ukuran gambar struk tidak pernah ikut terhitung.
        val imageBytes =
            com.notarapi.tidynote.core.storage.ReceiptImages
                .sizeBytes(context)

        val totalSize = dbBytes + imageBytes
        when {
            totalSize < 1024 -> "$totalSize B"
            totalSize < 1024 * 1024 -> "${totalSize / 1024} KB"
            else -> "${totalSize / (1024 * 1024)} MB"
        }
    }

private fun getSavedBackendToken(context: Context): String {
    val prefs = context.getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
    // Migrasi: kunci lama llm_api_key dianggap token backend sementara.
    return prefs.getString("llm_backend_token", null)
        ?: prefs.getString("llm_api_key", "") ?: ""
}

private fun getSavedBackendUrl(context: Context): String =
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .getString("llm_backend_url", "") ?: ""

private fun saveBackendConfig(
    context: Context,
    baseUrl: String,
    token: String,
) {
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("llm_backend_url", baseUrl.trim())
        .putString("llm_backend_token", token)
        .remove("llm_api_key")
        .apply()
}

private fun getSavedDirectApiKey(context: Context): String =
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .getString("direct_api_key", "") ?: ""

private fun getSavedDirectBaseUrl(context: Context): String =
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .getString("direct_base_url", "") ?: ""

private fun getSavedDirectModel(context: Context): String =
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .getString("direct_model", "") ?: ""

private fun saveDirectConfig(
    context: Context,
    apiKey: String,
    baseUrl: String,
    model: String,
) {
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("direct_api_key", apiKey.trim())
        .putString("direct_base_url", baseUrl.trim())
        .putString("direct_model", model.trim())
        .apply()
}

private fun getCloudAiEnabled(context: Context): Boolean =
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .getBoolean("cloud_ai_enabled", false)

private fun saveCloudAiEnabled(
    context: Context,
    enabled: Boolean,
) {
    context
        .getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("cloud_ai_enabled", enabled)
        .apply()
}

private object PasswordVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val masked = text.text.map { '•' }.joinToString("")
        val annotated = AnnotatedString(masked, text.spanStyles)
        val offsetMapping =
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset

                override fun transformedToOriginal(offset: Int): Int = offset
            }
        return androidx.compose.ui.text.input
            .TransformedText(annotated, offsetMapping)
    }
}
