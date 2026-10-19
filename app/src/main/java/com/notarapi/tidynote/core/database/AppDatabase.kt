package com.notarapi.tidynote.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notarapi.tidynote.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

// v1 -> v2 tidak mengubah skema. Index `rawOcrText` dibuang karena:
//  (1) tidak dideklarasikan di ReceiptEntity -> Room membandingkan TableInfo
//      hasil baca DB vs skema; index ekstra bikin validasi gagal dan SETIAP
//      akses DB melempar IllegalStateException (crash permanen), dan
//  (2) tidak berguna: query-nya LIKE '%x%' tidak pernah memakai B-tree.
// Migration tetap didaftarkan supaya Room mengizinkan jalur upgrade 1 -> 2;
// tanpa itu Room melempar "A migration from 1 to 2 was required but not found".
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

// v2 -> v3: kolom nomor invoice di tabel receipts.
//
// Sebelumnya field "Nomor invoice" di layar Review hanya state lokal: user bisa
// mengetiknya, tapi hilang begitu Save ditekan. ALTER TABLE dipakai (bukan bikin
// ulang tabel) karena kolomnya nullable dan tanpa default — cara termurah, dan
// tidak menyentuh baris yang sudah ada.
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `invoiceNumber` TEXT")
        }
    }

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        MerchantEntity::class,
        CategoryEntity::class,
        ScanImageEntity::class,
        ExtractionAttemptEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                val created =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "tidynote-db",
                        )
                        // plan.md §14: migration diuji, tidak destruktif.
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .setQueryExecutor(Dispatchers.IO.asExecutor())
                        .addCallback(
                            object : Callback() {
                                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        seedDefaults(get(context))
                                    }
                                }
                            },
                        ).build()
                instance = created
                created
            }

        private suspend fun seedDefaults(db: AppDatabase) {
            val categories =
                listOf(
                    CategoryEntity(name = "Makanan & Minuman", colorHex = "#4CAF50", icon = "restaurant"),
                    CategoryEntity(name = "Bahan Baku", colorHex = "#FF9800", icon = "inventory"),
                    CategoryEntity(name = "Peralatan", colorHex = "#2196F3", icon = "build"),
                    CategoryEntity(name = "Kebersihan", colorHex = "#9C27B0", icon = "cleaning_services"),
                    CategoryEntity(name = "Transport", colorHex = "#F44336", icon = "local_shipping"),
                    CategoryEntity(name = "Lainnya", colorHex = "#607D8B", icon = "category"),
                )
            categories.forEach { db.receiptDao().insertCategory(it) }
        }
    }
}
