package com.notarapi.tidynote.feature.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.extraction.ExtractionOrchestrator
import com.notarapi.tidynote.extraction.ocr.OcrHelper
import com.notarapi.tidynote.extraction.parser.ReceiptParser
import com.notarapi.tidynote.extraction.scanner.ScannerHelper
import com.notarapi.tidynote.feature.dashboard.DashboardScreen
import com.notarapi.tidynote.feature.detail.DetailScreen
import com.notarapi.tidynote.feature.home.HomeScreen
import com.notarapi.tidynote.feature.history.HistoryScreen
import com.notarapi.tidynote.feature.onboarding.OnboardingScreen
import com.notarapi.tidynote.feature.review.ReviewScreen
import com.notarapi.tidynote.feature.scan.ScanScreen
import com.notarapi.tidynote.feature.settings.SettingsScreen
import com.notarapi.tidynote.ui.theme.TidyNoteTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Simple holder for passing draft between screens
object DraftHolder {
    var currentDraft: String? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TidyNoteTheme {
                Surface {
                    val navController = rememberNavController()
                    val json = com.notarapi.tidynote.core.serialization.AppJson
                    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                    val onboardingDone = remember {
                        appContext.getSharedPreferences("tidynote_prefs", android.content.Context.MODE_PRIVATE)
                            .getBoolean("onboarding_done", false)
                    }
                    NavHost(navController, startDestination = if (onboardingDone) "home" else "onboarding") {
                        composable("home") {
                            HomeScreen(
                                onScan = { navController.navigate("scan") },
                                onHistory = { navController.navigate("history") },
                                // Tombol "Periksa N struk" harus membuka Riwayat
                                // yang benar-benar tersaring ke PENDING, bukan
                                // semua struk seperti sebelumnya.
                                onReviewPending = { navController.navigate("history?status=PENDING") },
                                onDashboard = { navController.navigate("dashboard") },
                                onSettings = { navController.navigate("settings") },
                                onOpenReceipt = { id -> navController.navigate("detail/$id") },
                            )
                        }
                        composable("onboarding") {
                            OnboardingScreen(navController = navController)
                        }
                        composable("scan") {
                            val scannerHelper: ScannerHelper = org.koin.compose.koinInject()
                            val orchestrator: ExtractionOrchestrator = org.koin.compose.koinInject()
                            ScanScreen(
                                scannerHelper = scannerHelper,
                                orchestrator = orchestrator,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToReview = { draft ->
                                    DraftHolder.currentDraft = json.encodeToString(ReceiptDraft.serializer(), draft)
                                    navController.navigate("review")
                                },
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToDashboard = { navController.navigate("dashboard") }
                            )
                        }
                        // Mode edit: buka struk yang SUDAH tersimpan di layar Review.
                        // Sebelumnya struk tersimpan hanya bisa dilihat dan dihapus —
                        // salah ketik nominal berarti harus hapus lalu scan ulang.
                        composable("review/{id}") { backStackEntry ->
                            val editId = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            val repository: ReceiptRepository = org.koin.compose.koinInject()
                            val scope = rememberCoroutineScope()
                            var loaded by remember(editId) {
                                mutableStateOf<com.notarapi.tidynote.core.model.ReceiptDraft?>(null)
                            }
                            var missing by remember(editId) { mutableStateOf(false) }
                            LaunchedEffect(editId) {
                                val d = repository.getDraftForEdit(editId)
                                if (d == null) missing = true else loaded = d
                            }
                            val current = loaded
                            if (current == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (missing) "Struk tidak ditemukan." else "Memuat...")
                                }
                            } else {
                                ReviewScreen(
                                    draft = current,
                                    onSave = { edited ->
                                        scope.launch {
                                            repository.saveExisting(edited, editId)
                                            navController.popBackStack()
                                        }
                                    },
                                    onCancel = { navController.popBackStack() },
                                    // AI tidak ditawarkan di mode edit: teks OCR
                                    // sudah tersimpan, dan menimpanya bisa menghapus
                                    // koreksi manual yang justru jadi alasan user
                                    // membuka layar ini.
                                    onFixWithAi = null,
                                )
                            }
                        }
                        composable("review") {
                            val draftJson = DraftHolder.currentDraft ?: ""
                            // DraftHolder cuma `var` global berisi string JSON: dia bisa
                            // bertahan melewati process death sementara nav back stack
                            // tidak, dan skema ReceiptDraft bisa bergeser antar versi.
                            // Deserialisasi tanpa guard melempar SerializationException
                            // DI DALAM composition -> aplikasi crash. Fallback ke draft
                            // kosong: user isi manual, lebih baik daripada mati.
                            val draft = if (draftJson.isNotEmpty()) {
                                runCatching { json.decodeFromString<ReceiptDraft>(draftJson) }
                                    .getOrNull() ?: ReceiptDraft()
                            } else {
                                ReceiptDraft()
                            }
                            val repository: ReceiptRepository = org.koin.compose.koinInject()
                            val scope = rememberCoroutineScope()
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val prefs = ctx.getSharedPreferences("tidynote_prefs", android.content.Context.MODE_PRIVATE)
                            val aiEnabled = prefs.getBoolean("cloud_ai_enabled", false)
                            val backendUrl = prefs.getString("llm_backend_url", "").orEmpty()
                            val backendToken = prefs.getString("llm_backend_token", null)
                                ?: prefs.getString("llm_api_key", "").orEmpty()
                            val directApiKey = prefs.getString("direct_api_key", "").orEmpty()
                            val directBaseUrl = prefs.getString("direct_base_url", "").orEmpty()
                            val directModel = prefs.getString("direct_model", "").orEmpty()
                            // Resolver AI terpusat: backend → API key di aplikasi → key dev → null.
                            val enricher = com.notarapi.tidynote.extraction.llm.LlmConfig
                                .build(aiEnabled, backendUrl, backendToken, directApiKey, directBaseUrl, directModel)
                            ReviewScreen(
                                draft = draft,
                                onSave = { edited ->
                                    scope.launch {
                                        repository.saveDraft(edited)
                                        DraftHolder.currentDraft = null
                                        navController.popBackStack("scan", inclusive = false)
                                    }
                                },
                                onCancel = {
                                    DraftHolder.currentDraft = null
                                    navController.popBackStack()
                                },
                                // plan.md §7.5: hanya jika user menekan tombol + sudah opt-in
                                // dan ada sumber AI (backend pengguna atau key mode development).
                                onFixWithAi = if (enricher != null) {
                                    { ocr ->
                                        scope.launch {
                                            val fixed = try {
                                                enricher.extractFromOcr(ocr)
                                            } catch (_: Exception) {
                                                draft
                                            }
                                            val validated = com.notarapi.tidynote.extraction.ReceiptValidator.validate(fixed)
                                            DraftHolder.currentDraft = json.encodeToString(
                                                ReceiptDraft.serializer(),
                                                validated.copy(rawOcrText = ocr),
                                            )
                                            navController.popBackStack()
                                            navController.navigate("review")
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        composable(
                            route = "history?status={status}",
                            arguments = listOf(
                                androidx.navigation.navArgument("status") {
                                    type = androidx.navigation.NavType.StringType
                                    defaultValue = "ALL"
                                },
                            ),
                        ) { entry ->
                            HistoryScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenReceipt = { id -> navController.navigate("detail/$id") },
                                initialStatus = entry.arguments?.getString("status") ?: "ALL",
                            )
                        }
                        composable("detail/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            val detailScope = rememberCoroutineScope()
                            val repository: ReceiptRepository = org.koin.compose.koinInject()
                            DetailScreen(
                                receiptId = id,
                                onBack = { navController.popBackStack() },
                                onShare = { text ->
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    }
                                    this@MainActivity.startActivity(android.content.Intent.createChooser(intent, "Bagikan struk"))
                                },
                                // FileProvider sudah terpasang di manifest dan
                                // res/xml/file_paths.xml sudah mendeklarasikan
                                // direktori backups; tinggal dipakai. Tanpa
                                // FLAG_GRANT_READ_URI_PERMISSION aplikasi penerima
                                // tidak punya izin membaca file kita.
                                onShareImage = { path ->
                                    runCatching {
                                        val file = java.io.File(path)
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${packageName}.fileprovider",
                                            file,
                                        )
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        this@MainActivity.startActivity(
                                            android.content.Intent.createChooser(intent, "Bagikan gambar struk"),
                                        )
                                    }
                                },
                                onDuplicate = { sourceId ->
                                    detailScope.launch {
                                        val newId = repository.duplicateReceipt(sourceId)
                                        if (newId > 0) {
                                            navController.popBackStack()
                                            navController.navigate("detail/$newId")
                                        }
                                    }
                                },
                                onEdit = { editTargetId -> navController.navigate("review/$editTargetId") },
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}