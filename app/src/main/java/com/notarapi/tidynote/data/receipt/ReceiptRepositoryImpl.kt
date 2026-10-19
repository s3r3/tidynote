package com.notarapi.tidynote.data.receipt

import android.content.Context
import com.notarapi.tidynote.core.database.ReceiptDao
import com.notarapi.tidynote.core.database.ReceiptRow
import com.notarapi.tidynote.core.model.*
import com.notarapi.tidynote.core.storage.ReceiptImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReceiptRepositoryImpl(
    private val dao: ReceiptDao,
    private val context: Context,
) : ReceiptRepository {

    /**
     * Ambil id merchant, buat kalau belum ada.
     *
     * `insertMerchant` memakai OnConflictStrategy.IGNORE, yang mengembalikan **-1**
     * saat nama sudah ada (kolom `name` unik). -1 bukan id valid, dan karena Room
     * menyalakan `PRAGMA foreign_keys = ON`, menulis receipt dengan merchantId = -1
     * melempar SQLiteConstraintException. Terjadi kalau dua penyimpanan berjalan
     * bersamaan (mis. Save ditekan dua kali): dua-duanya melihat null, dua-duanya
     * menyisipkan, satu kalah dan dapat -1. Jadi kalau hasil insert <= 0, baca ulang.
     */
    private suspend fun resolveMerchantId(name: String?): Long? {
        if (name.isNullOrBlank()) return null
        dao.getMerchantByName(name)?.let { return it.id }
        val inserted = dao.insertMerchant(MerchantEntity(name = name))
        if (inserted > 0) return inserted
        return dao.getMerchantByName(name)?.id
    }

    override suspend fun saveDraft(draft: ReceiptDraft): Long = withContext(Dispatchers.IO) {
        val merchantId = resolveMerchantId(draft.merchantName)

        val receipt = ReceiptEntity(
            merchantId = merchantId,
            transactionDate = draft.transactionDate?.toEpochMilli(),
            currency = draft.currency,
            subtotalMinor = draft.subtotalMinor,
            discountMinor = draft.discountMinor,
            taxMinor = draft.taxMinor,
            totalMinor = draft.totalMinor?.takeIf { it > 0 } ?: draft.copyWithDefaults().totalMinor,
            categoryId = draft.categoryId,
            rawOcrText = draft.rawOcrText,
            source = draft.source.name,
            confidence = draft.confidence.average,
            reviewStatus = if (draft.needsReview) "PENDING" else "REVIEWED",
            imagePath = draft.imagePath
        )

        val receiptId = dao.insertReceipt(receipt)
        if (receiptId > 0) {
            val items = draft.items.mapIndexed { index, item ->
                ReceiptItemEntity(
                    receiptId = receiptId,
                    name = item.name,
                    quantity = item.quantity,
                    unitPriceMinor = item.unitPriceMinor,
                    totalMinor = item.lineTotal,
                    confidence = item.confidence
                )
            }
            dao.insertReceiptItems(items)
            // plan.md §3/§6: gambar + jejak ekstraksi tidak boleh hilang.
            draft.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
                dao.insertScanImage(
                    ScanImageEntity(receiptId = receiptId, imagePath = path),
                )
            }
            dao.insertExtractionAttempt(
                ExtractionAttemptEntity(
                    receiptId = receiptId,
                    method = draft.source.name,
                    status = "SUCCESS",
                    confidence = draft.confidence.average,
                ),
            )
        }
        receiptId
    }

    override suspend fun updateReceipt(entity: ReceiptEntity) = withContext(Dispatchers.IO) {
        dao.updateReceipt(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteReceipt(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteReceipt(id)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAllImages()
        dao.deleteAllItems()
        dao.deleteAllReceipts()
        // Baris dulu, file belakangan. Kalau penghapusan file gagal, yang tersisa
        // adalah file yatim yang tidak terlihat user; kebalikannya akan meninggalkan
        // baris yang menunjuk gambar yang sudah hilang. Gambar tidak pernah dihapus
        // di sini sebelumnya, jadi "Hapus semua data" selalu menyisakan sampah.
        // Unit: kegagalan hapus file tidak boleh menggagalkan hapus data itu sendiri.
        runCatching { ReceiptImages.deleteAll(context) }
        Unit
    }

    override suspend fun getReceiptById(id: Long): ReceiptEntity? = withContext(Dispatchers.IO) {
        dao.getReceiptById(id)
    }

    override suspend fun getReceiptWithItems(id: Long): ReceiptWithItems? = withContext(Dispatchers.IO) {
        val receipt = dao.getReceiptEntityById(id) ?: return@withContext null
        val items = dao.getItemsByReceiptId(id).first()
        val merchant = receipt.merchantId?.let { 
            dao.getMerchantById(it) 
        }
        val images = dao.getImagesByReceiptId(id)
        ReceiptWithItems(receipt = receipt, items = items, merchant = merchant, images = images)
    }

    override fun getReceiptsPaged(limit: Int, offset: Int): Flow<List<ReceiptEntity>> = dao.getReceiptsPaged(limit, offset)

    override fun getPendingReceipts(): Flow<List<ReceiptEntity>> = dao.getPendingReceipts()

    override fun filterReceipts(
        search: String?,
        startDate: Long?,
        endDate: Long?,
        categoryId: Long?,
        reviewStatus: String?,
    ): Flow<List<ReceiptEntity>> = dao.filterReceipts(search, startDate, endDate, categoryId, reviewStatus)

    override suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        dao.getPendingCount()
    }

    override suspend fun getTotalThisMonth(startOfMonth: Long): Long = withContext(Dispatchers.IO) {
        dao.getTotalThisMonth(startOfMonth)
    }

    override suspend fun updateReviewStatus(id: Long, status: String) {
        dao.getReceiptById(id)?.let { receipt ->
            dao.updateReceipt(receipt.copy(reviewStatus = status, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun getReceiptsForExport(
        query: String?,
        startDate: Long?,
        endDate: Long?,
        categoryId: Long?,
        reviewStatus: String?,
    ): List<ReceiptRow> = withContext(Dispatchers.IO) {
        // Memakai query yang sama persis dengan daftar di layar. Dulu ekspor
        // lewat searchReceiptsSync yang hanya peduli kata kunci, sehingga
        // mengabaikan filter status/tanggal dan hasilnya beda dari yang dilihat user.
        dao.filterReceipts(query, startDate, endDate, categoryId, reviewStatus).first().map { receipt ->
            val merchant = receipt.merchantId?.let { dao.getMerchantById(it) }
            ReceiptRow(receipt = receipt, merchant = merchant, images = emptyList())
        }
    }

    override suspend fun getDashboardSummary(start: Long?, end: Long?): DashboardSummary = withContext(Dispatchers.IO) {
        DashboardSummary(
            totalRupiah = dao.getTotalInRange(start, end),
            count = dao.getCountInRange(start, end),
            pendingCount = dao.getPendingCount(),
            byCategory = dao.getTotalsByCategory(start, end),
            topMerchants = dao.getTopMerchants(start, end, 5)
        )
    }

    override fun categories(): Flow<List<com.notarapi.tidynote.core.model.CategoryEntity>> =
        dao.getAllCategories()

    override suspend fun restoreFromBackup(rows: List<Pair<ReceiptDraft, String>>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for ((draft, status) in rows) {
            val merchantId = resolveMerchantId(draft.merchantName)
            val entity = ReceiptEntity(
                merchantId = merchantId,
                transactionDate = draft.transactionDate?.toEpochMilli(),
                currency = draft.currency,
                subtotalMinor = draft.subtotalMinor,
                discountMinor = draft.discountMinor,
                taxMinor = draft.taxMinor,
                totalMinor = draft.totalMinor?.takeIf { it > 0 } ?: draft.copyWithDefaults().totalMinor,
                categoryId = draft.categoryId,
                rawOcrText = draft.rawOcrText,
                source = draft.source.name,
                confidence = draft.confidence.average,
                reviewStatus = if (status == "REVIEWED") "REVIEWED" else "PENDING",
                imagePath = draft.imagePath,
            )
            val id = dao.insertReceipt(entity)
            if (id > 0) count++
        }
        count
    }
}