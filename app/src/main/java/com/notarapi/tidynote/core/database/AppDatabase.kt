package com.notarapi.tidynote.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notarapi.tidynote.core.model.*
import com.notarapi.tidynote.core.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v2: indeks tambahan untuk pencarian cepat (§6), tanpa hapus data.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_rawOcrText` ON `receipts` (`rawOcrText`)")
    }
}

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        MerchantEntity::class,
        CategoryEntity::class,
        ScanImageEntity::class,
        ExtractionAttemptEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tidynote-db"
                )
                    // plan.md §14: migration diuji, tidak destruktif.
                    .addMigrations(MIGRATION_1_2)
                    .setQueryExecutor(Dispatchers.IO.asExecutor())
                    .addCallback(object : Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedDefaults(get(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaults(db: AppDatabase) {
            val categories = listOf(
                CategoryEntity(name = "Makanan & Minuman", colorHex = "#4CAF50", icon = "restaurant"),
                CategoryEntity(name = "Bahan Baku", colorHex = "#FF9800", icon = "inventory"),
                CategoryEntity(name = "Peralatan", colorHex = "#2196F3", icon = "build"),
                CategoryEntity(name = "Kebersihan", colorHex = "#9C27B0", icon = "cleaning_services"),
                CategoryEntity(name = "Transport", colorHex = "#F44336", icon = "local_shipping"),
                CategoryEntity(name = "Lainnya", colorHex = "#607D8B", icon = "category")
            )
            categories.forEach { db.receiptDao().insertCategory(it) }
        }
    }
}