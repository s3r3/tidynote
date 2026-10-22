package com.notarapi.tidynote

import android.app.Application
import com.notarapi.tidynote.core.database.AppDatabase
import com.notarapi.tidynote.core.database.ReceiptDao
import com.notarapi.tidynote.core.prefs.AppPreferences
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.data.receipt.ReceiptRepositoryImpl
import com.notarapi.tidynote.extraction.ExtractionOrchestrator
import com.notarapi.tidynote.extraction.ocr.OcrHelper
import com.notarapi.tidynote.extraction.parser.ReceiptParser
import com.notarapi.tidynote.extraction.scanner.ScannerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class TidyNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@TidyNoteApplication)
            modules(appModule)
        }
        // Pindahkan SharedPreferences lama ke DataStore sekali (plan §8).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.notarapi.tidynote.core.prefs
                .AppPreferences(this@TidyNoteApplication)
                .migrateFromSharedPrefs()
        }
    }

    companion object {
        val appModule =
            module {
                // AppDatabase harus terdaftar: ReceiptRepositoryImpl menerimanya
                // sebagai parameter ketiga, dan tanpa bean ini Koin melempar
                // NoBeanDefFoundException saat HomeScreen pertama kali resolve
                // repository — aplikasi crash tepat setelah onboarding.
                single<AppDatabase> { AppDatabase.get(get()) }
                single<ReceiptDao> { AppDatabase.get(get()).receiptDao() }
                // get() kedua = Context (Android). Diperlukan supaya deleteAll() bisa
                // ikut menghapus file gambar struk, bukan cuma baris database.
                // get() ketiga = AppDatabase, untuk transaksi multi-tabel.
                single<ReceiptRepository> { ReceiptRepositoryImpl(get(), get(), get()) }
                single {
                    com.notarapi.tidynote.core.prefs
                        .AppPreferences(get())
                }
                single { OcrHelper() }
                single { ReceiptParser() }
                single { ScannerHelper() }
                // plan.md §9: tidak ada secret di APK, AI default mati.
                // ReceiptEnricher TIDAK didaftarkan di sini karena Koin tidak boleh
                // mengembalikan null dari `single`, dan enricher harus dibangun saat
                // runtime (MainActivity via LlmConfig.build) sesuai opt-in + sumber AI
                // pengguna. Orchestrator menerima null lewat getOrNull().
                single { ExtractionOrchestrator(get(), get(), getOrNull()) }
            }
    }
}
