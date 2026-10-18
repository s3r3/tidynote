# Catatan Integrasi (butuh koneksi online)

Beberapa item plan.md sengaja **tidak diaktifkan di build offline** agar
`assembleDebug` tetap hijau tanpa internet. Berikut cara menyalakannya.

## 1. Backend Ktor (`:backend`)
Modul sudah lengkap (auth bearer, rate-limit, JSON-only, tanpa log isi struk).
Cara nyalakan (setelah online):
1. Edit `settings.gradle.kts`: buka `include(":backend")`.
2. Isi kredensial lewat env (jangan commit):
   ```
   PROVIDER_API_KEY=sk-xxxx
   APP_TOKENS=token-rahasia-1,token-rahasia-2
   PROVIDER_URL=https://api.openai.com/v1/chat/completions   # opsional
   PROVIDER_MODEL=gpt-4o-mini                                # opsional
   ```
3. Jalankan: `./gradlew :backend:run`  (default port 8080).
4. Di aplikasi → Settings → AI: URL backend = `http://IP:8080`,
   Token backend = salah satu `APP_TOKENS`.
   App otomatis memakai `POST /v1/receipts/parse` (fallback path lama
   `/v1/receipt/extract` bila 404).

## 2. Kover (coverage)
Plugin belum di-cache offline. Setelah online:
- Buka `alias(libs.plugins.kover) apply false` di `build.gradle.kts` root
  dan `alias(libs.plugins.kover)` di `app/build.gradle.kts`.
- Jalankan `./gradlew :app:koverHtmlReport`.

## 3. DataStore (pengganti SharedPreferences)
`core/prefs/AppPreferences.kt` sudah jadi lapisan tengah. Setelah online
tambahkan `implementation(libs.datastore.preferences)` lalu ganti isi
`AppPreferences` ke `preferencesDataStore` — API (Flow + `snapshot()`)
tidak berubah, jadi pemanggil (Onboarding/Settings) tak perlu diubah.

## 4. WorkManager (retry OCR/AI latar)
Belum dipakai (belum ada dependency offline). Polang bila diperlukan:
`CoroutineWorker` di modul `:app` dengan `enqueueUniqueWork` untuk retry
ekstraksi yang gagal karena jaringan — parser lokal sudah jalan sinkron,
jadi retry hanya perlu untuk jalur LLM.

## 5. Ktor-client di app (pengganti OkHttp)
Saat ini `LlmExtractor` memakai OkHttp (sudah aman: HTTPS-only, timeout,
schema ketat). Migrasi ke Ktor-client opsional dan tidak mengubah
`BackendContract`/`ReceiptEnricher`.
