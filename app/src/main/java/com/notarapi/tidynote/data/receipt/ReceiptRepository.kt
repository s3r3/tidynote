package com.notarapi.tidynote.data.receipt

import com.notarapi.tidynote.core.database.CategoryTotal
import com.notarapi.tidynote.core.database.MerchantTotal
import com.notarapi.tidynote.core.database.ReceiptRow
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ReceiptEntity
import com.notarapi.tidynote.core.model.ReceiptItemEntity
import com.notarapi.tidynote.core.model.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    suspend fun saveDraft(draft: ReceiptDraft): Long
    suspend fun updateReceipt(entity: ReceiptEntity)
    suspend fun deleteReceipt(id: Long)
    suspend fun deleteAll()
    suspend fun getReceiptById(id: Long): ReceiptEntity?
    suspend fun getReceiptWithItems(id: Long): ReceiptWithItems?
    fun getReceiptsPaged(limit: Int, offset: Int): Flow<List<ReceiptEntity>>
    fun getPendingReceipts(): Flow<List<ReceiptEntity>>

    /**
     * Pencarian + filter dalam satu query. Dipakai bersama oleh daftar di layar
     * dan oleh ekspor, supaya keduanya tidak bisa lagi memberi hasil berbeda.
     */
    fun filterReceipts(
        search: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        categoryId: Long? = null,
        reviewStatus: String? = null,
    ): Flow<List<ReceiptEntity>>

    suspend fun getPendingCount(): Int
    suspend fun getTotalThisMonth(startOfMonth: Long): Long
    suspend fun updateReviewStatus(id: Long, status: String)

    /** Ekspor memakai filter yang sama dengan layar; default menjaga pemanggil lama tetap jalan. */
    suspend fun getReceiptsForExport(
        query: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        categoryId: Long? = null,
        reviewStatus: String? = null,
    ): List<ReceiptRow>
    suspend fun getDashboardSummary(start: Long?, end: Long?): DashboardSummary
    fun categories(): Flow<List<com.notarapi.tidynote.core.model.CategoryEntity>>
    suspend fun restoreFromBackup(rows: List<Pair<ReceiptDraft, String>>): Int
}

data class DashboardSummary(
    val totalRupiah: Long = 0L,
    val count: Int = 0,
    val pendingCount: Int = 0,
    val byCategory: List<CategoryTotal> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList()
)