package com.notarapi.tidynote.feature.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.prefs.AppPreferences
import com.notarapi.tidynote.core.prefs.ThemeMode
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.ui.design.TidyCard
import com.notarapi.tidynote.ui.design.TidyTab
import com.notarapi.tidynote.ui.design.TidyTabScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onScan: () -> Unit,
    onNavigateTab: (TidyTab) -> Unit,
) {
    val context = LocalContext.current
    val repository: ReceiptRepository = koinInject()
    val prefs: AppPreferences = koinInject()
    val scope = rememberCoroutineScope()

    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

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
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
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
                            val rows = com.notarapi.tidynote.core.backup.CsvBackupParser.parse(csv)
                            val n = repository.restoreFromBackup(rows)
                            "Berhasil memulihkan $n struk"
                        } catch (e: Exception) {
                            "Gagal pulihkan: ${e.message ?: "passphrase salah / file rusak"}"
                        }
                }
            }
        }

    LaunchedEffect(Unit) {
        storageSize = calculateStorageSize(context)
    }

    TidyTabScaffold(
        title = stringResource(R.string.settings),
        selected = TidyTab.SETTINGS,
        onNavigateTab = onNavigateTab,
        onScan = onScan,
    ) { padding ->
        // Dulu Column ini tanpa scroll: kartu-kartunya melebihi tinggi layar dan
        // bagian bawah (backup, hapus data) tidak bisa dijangkau sama sekali.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------------------------------------------------------
            // Tampilan — pilihan tema terang/gelap/sistem
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(icon = Icons.Default.Palette, title = "Tampilan")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Mode tema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pilihan disimpan dan dipakai lagi saat aplikasi dibuka.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    val options =
                        listOf(
                            ThemeMode.SYSTEM to "Sistem",
                            ThemeMode.LIGHT to "Terang",
                            ThemeMode.DARK to "Gelap",
                        )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { prefs.setThemeMode(mode) },
                                shape =
                                    SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = options.size,
                                    ),
                                icon = {
                                    if (mode == ThemeMode.DARK) {
                                        Icon(
                                            Icons.Default.DarkMode,
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                        )
                                    }
                                },
                            ) { Text(label) }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // AI
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(icon = Icons.Default.Cloud, title = stringResource(R.string.cloud_ai))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.cloud_ai),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.cloud_ai_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (useCloudAi) {
                        TextField(
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
                        TextField(
                            value = llmApiKey,
                            onValueChange = {
                                llmApiKey = it
                                saveBackendConfig(context, llmBackendUrl, it)
                            },
                            label = { Text("Token backend (sementara)") },
                            placeholder = { Text("Token dari backend Anda setelah login") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation,
                        )
                        Hint(
                            "Opsional. Default mati. Jika diaktifkan, hanya teks OCR " +
                                "(maks 8000 karakter, bukan foto) yang dikirim ke backend " +
                                "milik Anda setelah Anda menekan Perbaiki dengan AI.",
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ATAU langsung pakai API key (tanpa backend)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Hint(
                            "Isi API key provider OpenAI-compatible (mis. OpenAI/Groq/OpenRouter). " +
                                "Key disimpan di HP ini saja, tidak ikut ke dalam APK. " +
                                "Hanya teks OCR yang dikirim setelah Anda menekan Perbaiki dengan AI.",
                        )
                        TextField(
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
                        TextField(
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
                        TextField(
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
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (com.notarapi.tidynote.extraction.llm.LlmConfig.hasDevKey &&
                            !(llmBackendUrl.isNotBlank() && llmApiKey.isNotBlank())
                        ) {
                            Text(
                                "Peringatan: build ini memuat API key mode development di " +
                                    "dalam APK (dari local.properties). Bisa dibongkar orang lain " +
                                    "dan memakai kuota Anda. Hanya untuk internal/demo — untuk " +
                                    "publik, kosongkan LLM_API_KEY di local.properties lalu build ulang.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Privasi
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(
                        icon = Icons.Default.PrivacyTip,
                        title = stringResource(R.string.privacy),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Data tersimpan lokal di perangkat. Tidak ada data yang dikirim ke server tanpa izin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Ukuran penyimpanan: $storageSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // ---------------------------------------------------------------
            // Backup
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(icon = Icons.Default.Lock, title = "Backup terenkripsi")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Cadangkan semua struk jadi satu file .tnbak yang dienkripsi dengan passphrase Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase backup (min 4 karakter)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                            onClick = { backupLauncher.launch(arrayOf("application/octet-stream")) },
                        ) { Text("Pulihkan") }
                    }
                    backupMessage?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // Hapus data
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(
                        icon = Icons.Default.DeleteForever,
                        title = stringResource(R.string.delete_all_data),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.delete_all_data_confirm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isDeleting,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                Text(stringResource(R.string.delete_all_data))
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Tentang
            // ---------------------------------------------------------------
            TidyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(icon = Icons.Default.Info, title = stringResource(R.string.about))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.app_name_full),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.version) + " 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Offline-first receipt scanner untuk UMKM Indonesia",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
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

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).padding(end = 10.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
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
        // Direktoriya beda, jadi ukuran gambar struk tidak pernah ikut terhitung.
        val imageBytes = com.notarapi.tidynote.core.storage.ReceiptImages.sizeBytes(context)

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
    override fun filter(text: AnnotatedString): TransformedText {
        val masked = text.text.map { '•' }.joinToString("")
        val annotated = AnnotatedString(masked, text.spanStyles)
        val offsetMapping =
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset

                override fun transformedToOriginal(offset: Int): Int = offset
            }
        return TransformedText(annotated, offsetMapping)
    }
}
