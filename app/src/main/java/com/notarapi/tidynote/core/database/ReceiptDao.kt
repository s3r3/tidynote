package com.notarapi.tidynote.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.notarapi.tidynote.core.model.*

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<ReceiptEntity>)

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptById(id: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts ORDER BY transactionDate DESC LIMIT :limit OFFSET :offset")
    fun getReceiptsPaged(limit: Int, offset: Int): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE reviewStatus = :status ORDER BY transactionDate DESC")
    fun getReceiptsByStatus(status: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE reviewStatus = 'PENDING' ORDER BY transactionDate DESC")
    fun getPendingReceipts(): Flow<List<ReceiptEntity>>

    @Query("""
        SELECT r.* FROM receipts r
        LEFT JOIN merchants m ON r.merchantId = m.id
        WHERE (:search IS NULL OR :search = '' OR m.name LIKE '%' || :search || '%'
            OR r.rawOcrText LIKE '%' || :search || '%')
        ORDER BY r.transactionDate DESC
    """)
    fun searchReceipts(search: String?): Flow<List<ReceiptEntity>>

    @Query("""
        SELECT r.* FROM receipts r
        LEFT JOIN merchants m ON r.merchantId = m.id
        WHERE (:search IS NULL OR :search = '' OR m.name LIKE '%' || :search || '%'
            OR r.rawOcrText LIKE '%' || :search || '%')
        ORDER BY r.transactionDate DESC
    """)
    suspend fun searchReceiptsSync(search: String?): List<ReceiptEntity>

    @Query("""
        SELECT * FROM receipts
        WHERE (:startDate IS NULL OR transactionDate >= :startDate)
          AND (:endDate IS NULL OR transactionDate <= :endDate)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:reviewStatus IS NULL OR reviewStatus = :reviewStatus)
        ORDER BY transactionDate DESC
    """)
    fun filterReceipts(
        startDate: Long?, endDate: Long?, categoryId: Long?, reviewStatus: String?
    ): Flow<List<ReceiptEntity>>

    @Transaction
    @Query("""
        SELECT r.* FROM receipts r
        ORDER BY r.transactionDate DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getReceiptsWithMerchant(limit: Int, offset: Int): Flow<List<ReceiptRow>>

    @Transaction
    @Query("""
        SELECT r.* FROM receipts r
        WHERE r.id = :id
    """)
    suspend fun getReceiptWithMerchantById(id: Long): ReceiptRow?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceiptItem(item: ReceiptItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId")
    fun getItemsByReceiptId(receiptId: Long): Flow<List<ReceiptItemEntity>>

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsByReceiptId(receiptId: Long)

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptEntityById(receiptId: Long): ReceiptEntity?

    // Merchant queries
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMerchant(merchant: MerchantEntity): Long

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getMerchantById(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE name = :name")
    suspend fun getMerchantByName(name: String): MerchantEntity?

    @Query("SELECT * FROM merchants ORDER BY name")
    fun getAllMerchants(): Flow<List<MerchantEntity>>

    // Category queries
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    // Scan images
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanImage(image: ScanImageEntity): Long

    @Query("SELECT * FROM scan_images WHERE receiptId = :receiptId")
    suspend fun getImagesByReceiptId(receiptId: Long): List<ScanImageEntity>

    @Query("DELETE FROM scan_images WHERE receiptId = :receiptId")
    suspend fun deleteImagesByReceiptId(receiptId: Long)

    // Extraction attempts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtractionAttempt(attempt: ExtractionAttemptEntity): Long

    @Query("SELECT * FROM extraction_attempts WHERE receiptId = :receiptId ORDER BY createdAt DESC")
    suspend fun getExtractionAttempts(receiptId: Long): List<ExtractionAttemptEntity>

    // Stats
    @Query("SELECT COUNT(*) FROM receipts WHERE reviewStatus = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COALESCE(SUM(totalMinor), 0) FROM receipts WHERE transactionDate >= :startOfMonth")
    suspend fun getTotalThisMonth(startOfMonth: Long): Long

    @Query("""
        SELECT COALESCE(SUM(totalMinor), 0) FROM receipts
        WHERE (:start IS NULL OR transactionDate >= :start)
          AND (:end IS NULL OR transactionDate <= :end)
    """)
    suspend fun getTotalInRange(start: Long?, end: Long?): Long

    @Query("""
        SELECT COUNT(*) FROM receipts
        WHERE (:start IS NULL OR transactionDate >= :start)
          AND (:end IS NULL OR transactionDate <= :end)
    """)
    suspend fun getCountInRange(start: Long?, end: Long?): Int

    @Query("""
        SELECT r.categoryId as categoryId, COALESCE(SUM(r.totalMinor), 0) as total
        FROM receipts r
        WHERE (:start IS NULL OR r.transactionDate >= :start)
          AND (:end IS NULL OR r.transactionDate <= :end)
        GROUP BY r.categoryId
        ORDER BY total DESC
    """)
    suspend fun getTotalsByCategory(start: Long?, end: Long?): List<CategoryTotal>

    @Query("""
        SELECT m.name as merchantName, COALESCE(SUM(r.totalMinor), 0) as total, COUNT(*) as count
        FROM receipts r LEFT JOIN merchants m ON r.merchantId = m.id
        WHERE (:start IS NULL OR r.transactionDate >= :start)
          AND (:end IS NULL OR r.transactionDate <= :end)
        GROUP BY m.name
        ORDER BY total DESC
        LIMIT :limit
    """)
    suspend fun getTopMerchants(start: Long?, end: Long?, limit: Int = 5): List<MerchantTotal>

    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Query("DELETE FROM receipt_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM scan_images")
    suspend fun deleteAllImages()
}

data class CategoryTotal(
    val categoryId: Long?,
    val total: Long
)

data class MerchantTotal(
    val merchantName: String?,
    val total: Long,
    val count: Int
)

data class ReceiptRow(
    @androidx.room.Embedded val receipt: ReceiptEntity,
    @androidx.room.Relation(
        parentColumn = "merchantId",
        entityColumn = "id"
    )
    val merchant: MerchantEntity? = null,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val images: List<ScanImageEntity> = emptyList()
)