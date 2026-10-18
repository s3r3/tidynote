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
    fun searchReceipts(query: String?): Flow<List<ReceiptEntity>>
    fun filterReceipts(
        startDate: Long?, endDate: Long?, categoryId: Long?, reviewStatus: String?
    ): Flow<List<ReceiptEntity>>
    suspend fun getPendingCount(): Int
    suspend fun getTotalThisMonth(startOfMonth: Long): Long
    suspend fun updateReviewStatus(id: Long, status: String)
    suspend fun getReceiptsForExport(query: String?): List<ReceiptRow>
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