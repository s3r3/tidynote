# Aturan ProGuard/R8 untuk TidyNote.
#
# Dirujuk app/build.gradle.kts:57 pada buildType release. Sebelumnya file ini
# tidak ada di repo -> build release gagal di resolusi proguardFiles.
# Isinya menutup komponen yang memang bergantung pada refleksi.

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room membuat implementasi *_Impl lewat KSP dan memuatnya lewat refleksi.
# Tanpa ini, R8 membuang kelas generated dan aplikasi crash saat buka DB.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# ReceiptDraft / FieldConfidence / ExtractionSource diserialisasi ke JSON dan
# disimpan di DraftHolder + file backup. Serializer dicari lewat refleksi.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.notarapi.tidynote.core.model.** { *; }
-keep class com.notarapi.tidynote.core.model.**$$serializer { *; }
-keepclasseswithmembers class com.notarapi.tidynote.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# ML Kit
# ---------------------------------------------------------------------------
# ML Kit menarik kelas opsional yang tidak dipakai jalur teks.
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---------------------------------------------------------------------------
# HTTP client
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**

# ---------------------------------------------------------------------------
# Koin
# ---------------------------------------------------------------------------
# Koin memakai refleksi untuk resolusi constructor.
-keep class org.koin.** { *; }
