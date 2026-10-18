package com.notarapi.tidynote.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_keepsData() {
        var db = helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO receipts (merchantId, transactionDate, currency, totalMinor, reviewStatus) " +
                    "VALUES (NULL, 1700000000000, 'IDR', 25000, 'REVIEWED')",
            )
            close()
        }
        db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)
        db.query("SELECT totalMinor FROM receipts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getLong(0) == 25000L)
        }
    }
}
