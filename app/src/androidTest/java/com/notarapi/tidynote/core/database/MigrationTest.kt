package com.notarapi.tidynote.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Menguji jalur upgrade Room tanpa bergantung pada file schema v1.
 *
 * Versi sebelumnya memakai MigrationTestHelper.createDatabase(nama, 1), yang
 * mengharuskan app/schemas/<db>/1.json tersedia sebagai asset. File itu tidak
 * pernah ada di repo, dan room-testing bahkan tidak terdaftar sebagai dependensi
 * androidTest — jadi test ini tidak pernah bisa di-compile, apalagi dijalankan.
 * Justru itu sebabnya bug index di MIGRATION_1_2 lolos: tidak ada yang menguji.
 *
 * Di sini tabel versi lama dibuat manual, lalu migration dijalankan langsung.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    /** Bentuk tabel `receipts` di versi 1: tanpa kolom invoiceNumber. */
    private val createReceiptsV1 = """
        CREATE TABLE IF NOT EXISTS `receipts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `merchantId` INTEGER,
            `transactionDate` INTEGER,
            `currency` TEXT NOT NULL,
            `subtotalMinor` INTEGER,
            `discountMinor` INTEGER,
            `taxMinor` INTEGER,
            `totalMinor` INTEGER,
            `categoryId` INTEGER,
            `rawOcrText` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `confidence` REAL NOT NULL,
            `reviewStatus` TEXT NOT NULL,
            `imagePath` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
    """.trimIndent()

    /** INSERT harus mengisi SEMUA kolom NOT NULL — versi lama tidak, karena itu gagal. */
    private val insertRow = """
        INSERT INTO receipts
            (merchantId, transactionDate, currency, rawOcrText, source, confidence,
             reviewStatus, createdAt, updatedAt, totalMinor)
        VALUES (NULL, 1700000000000, 'IDR', 'INDOMARET', 'LOCAL_PARSER', 0.9,
                'REVIEWED', 1700000000000, 1700000000000, 25000)
    """.trimIndent()

    /** Helper yang membuka file DB mentah, tanpa campur tangan Room. */
    private fun openRaw(name: String, createSql: String): SupportSQLiteOpenHelper {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.cacheDir, name).apply { delete() }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(createSql)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
    }

    @Test
    fun migrate1To2_keepsData_andDoesNotAddIndex() {
        val helper = openRaw("mig-1-2.db", createReceiptsV1)
        helper.writableDatabase.use { db ->
            db.execSQL(insertRow)
            MIGRATION_1_2.migrate(db)

            db.query("SELECT totalMinor FROM receipts").use { c ->
                assertTrue("baris harus selamat melewati migrasi", c.moveToFirst())
                assertEquals(25000L, c.getLong(0))
            }

            // Migrasi v1->v2 TIDAK boleh menambah index apa pun. Index yang tidak
            // dideklarasikan di ReceiptEntity membuat Room menolak database ini
            // saat dibuka, dan aplikasi crash permanen setiap kali akses DB.
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'receipts'").use { c ->
                val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertTrue(
                    "index tak terdeklarasi bikin validasi Room gagal: $names",
                    names.none { it == "index_receipts_rawOcrText" },
                )
            }
        }
    }

    @Test
    fun migrate2To3_addsInvoiceNumberColumn_andKeepsData() {
        val helper = openRaw("mig-2-3.db", createReceiptsV1)
        helper.writableDatabase.use { db ->
            db.execSQL(insertRow)
            MIGRATION_2_3.migrate(db)

            // Data lama harus utuh, dan kolom barunya null (bukan gagal).
            db.query("SELECT totalMinor, invoiceNumber FROM receipts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(25000L, c.getLong(0))
                assertNull("baris lama tidak punya nomor invoice", c.getString(1))
            }

            // Kolom barunya harus benar-benar bisa diisi.
            db.execSQL("UPDATE receipts SET invoiceNumber = 'INV-001'")
            db.query("SELECT invoiceNumber FROM receipts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("INV-001", c.getString(0))
            }
        }
    }
}
