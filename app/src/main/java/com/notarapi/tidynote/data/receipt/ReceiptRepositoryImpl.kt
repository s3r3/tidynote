package com.notarapi.tidynote.data.receipt

import android.content.Context
import androidx.room.withTransaction
import com.notarapi.tidynote.core.database.AppDatabase
import com.notarapi.tidynote.core.database.ReceiptDao
import com.notarapi.tidynote.core.database.ReceiptRow
import com.notarapi.tidynote.core.model.*
import com.notarapi.tidynote.core.storage.ReceiptImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Jumlah merchant teratas di ringkasan dashboard. */
private const val TOP_MERCHANT_LIMIT = 5

class ReceiptRepositoryImpl(
    private val dao: ReceiptDao,
    private val context: Context,
    private val db: AppDatabase,
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
    private suspend fun resolveMerchantId(
        name: String?,
        npwp: String? = null,
    ): Long? {
        if (name.isNullOrBlank()) return null
        dao.getMerchantByName(name)?.let { existing ->
            // NPWP ikut disimpan kalau user mengisinya dan merchant belum punya.
            // Kolom taxId sudah ada sejak awal tapi tidak pernah ditulis siapa pun.
            if (!npwp.isNullOrBlank() && existing.taxId.isNullOrBlank()) {
                dao.updateMerchant(existing.copy(taxId = npwp))
            }
            return existing.id
        }
        val inserted = dao.insertMerchant(MerchantEntity(name = name, taxId = npwp?.takeIf { it.isNotBlank() }))
        if (inserted > 0) return inserted
        return dao.getMerchantByName(name)?.id
    }

    override suspend fun saveDraft(draft: ReceiptDraft): Long =
        withContext(Dispatchers.IO) {
            val merchantId = resolveMerchantId(draft.merchantName, draft.npwp)

            val receipt =
                ReceiptEntity(
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
                    reviewStatus = draft.reviewStatusForSave(),
                    invoiceNumber = draft.invoiceNumber,
                    imagePath = draft.imagePath,
                )

            val receiptId = dao.insertReceipt(receipt)
            if (receiptId > 0) {
                val items =
                    draft.items.mapIndexed { index, item ->
                        ReceiptItemEntity(
                            receiptId = receiptId,
                            name = item.name,
                            quantity = item.quantity,
                            unitPriceMinor = item.unitPriceMinor,
                            totalMinor = item.lineTotal,
                            confidence = item.confidence,
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

    override suspend fun updateReceipt(entity: ReceiptEntity) =
        withContext(Dispatchers.IO) {
            dao.updateReceipt(entity.copy(updatedAt = System.currentTimeMillis()))
        }

    override suspend fun getDraftForEdit(id: Long): ReceiptDraft? =
        withContext(Dispatchers.IO) {
            val full = getReceiptWithItems(id) ?: return@withContext null
            val r = full.receipt
            ReceiptDraft(
                merchantName = full.merchant?.name,
                npwp = full.merchant?.taxId,
                invoiceNumber = r.invoiceNumber,
                transactionDate = r.transactionDate?.let { java.time.Instant.ofEpochMilli(it) },
                currency = r.currency,
                items =
                    full.items.map { item ->
                        ReceiptItemDraft(
                            name = item.name,
                            quantity = item.quantity,
                            unitPriceMinor = item.unitPriceMinor,
                            totalMinor = item.totalMinor,
                            confidence = item.confidence,
                        )
                    },
                subtotalMinor = r.subtotalMinor,
                discountMinor = r.discountMinor,
                taxMinor = r.taxMinor,
                totalMinor = r.totalMinor,
                categoryId = r.categoryId,
                rawOcrText = r.rawOcrText,
                // Sumber asli dipertahankan supaya jejak asal ekstraksi tidak hilang
                // hanya karena user menyunting nominalnya.
                source =
                    runCatching { ExtractionSource.valueOf(r.source) }
                        .getOrDefault(ExtractionSource.MANUAL_INPUT),
                confidence = FieldConfidence(total = r.confidence),
                imagePath = r.imagePath,
            )
        }

    override suspend fun deleteReceipt(id: Long) =
        withContext(Dispatchers.IO) {
            dao.deleteReceipt(id)
        }

    override suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
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

    override suspend fun getReceiptById(id: Long): ReceiptEntity? =
        withContext(Dispatchers.IO) {
            dao.getReceiptById(id)
        }

    override suspend fun getReceiptWithItems(id: Long): ReceiptWithItems? =
        withContext(Dispatchers.IO) {
            val receipt = dao.getReceiptEntityById(id) ?: return@withContext null
            val items = dao.getItemsByReceiptId(id).first()
            val merchant =
                receipt.merchantId?.let {
                    dao.getMerchantById(it)
                }
            val images = dao.getImagesByReceiptId(id)
            ReceiptWithItems(receipt = receipt, items = items, merchant = merchant, images = images)
        }

    override fun getReceiptsPaged(
        limit: Int,
        offset: Int,
    ): Flow<List<ReceiptEntity>> = dao.getReceiptsPaged(limit, offset)

    override fun getPendingReceipts(): Flow<List<ReceiptEntity>> = dao.getPendingReceipts()

    override fun filterReceipts(
        search: String?,
        startDate: Long?,
        endDate: Long?,
        categoryId: Long?,
        reviewStatus: String?,
    ): Flow<List<ReceiptEntity>> = dao.filterReceipts(search, startDate, endDate, categoryId, reviewStatus)

    override suspend fun getPendingCount(): Int =
        withContext(Dispatchers.IO) {
            dao.getPendingCount()
        }

    override suspend fun getTotalThisMonth(startOfMonth: Long): Long =
        withContext(Dispatchers.IO) {
            dao.getTotalThisMonth(startOfMonth)
        }

    override suspend fun updateReviewStatus(
        id: Long,
        status: String,
    ) {
        dao.getReceiptById(id)?.let { receipt ->
            dao.updateReceipt(receipt.copy(reviewStatus = status, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun saveExisting(
        draft: ReceiptDraft,
        receiptId: Long,
    ): Long =
        withContext(Dispatchers.IO) {
            // Satu transaksi: update header + ganti item. Tanpa ini, crash di antara
            // deleteItemsByReceiptId dan insert membuat struk kehilangan seluruh item.
            db.withTransaction {
                val existing = dao.getReceiptById(receiptId) ?: return@withTransaction 0L
                val merchantId = resolveMerchantId(draft.merchantName, draft.npwp)

                // createdAt dipertahankan: barisnya memang sudah ada, yang berubah isinya.
                dao.updateReceipt(
                    existing.copy(
                        merchantId = merchantId,
                        // Draft dari getDraftForEdit sudah membawa tanggal tersimpan,
                        // jadi null di sini berarti user mengosongkan field-nya. Dulu
                        // ada fallback ke existing.transactionDate yang membuat
                        // penghapusan tanggal tidak pernah benar-benar tersimpan.
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
                        // Menyimpan ulang tanpa mengubah angka tidak boleh menurunkan
                        // status yang sudah REVIEWED. Sebelumnya status selalu dihitung
                        // dari confidence hasil rekonstruksi getDraftForEdit (yang hanya
                        // menyimpan rata-rata), jadi setiap edit membalik REVIEWED -> PENDING.
                        reviewStatus = draft.reviewStatusForSave(existing.reviewStatus),
                        invoiceNumber = draft.invoiceNumber,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                // Item diganti seluruhnya, bukan digabung: user mungkin menghapus baris
                // di layar Review, dan hasilnya harus persis seperti yang terlihat.
                dao.deleteItemsByReceiptId(receiptId)
                val items =
                    draft.items.map { item ->
                        ReceiptItemEntity(
                            receiptId = receiptId,
                            name = item.name,
                            quantity = item.quantity,
                            unitPriceMinor = item.unitPriceMinor,
                            totalMinor = item.lineTotal,
                            confidence = item.confidence,
                        )
                    }
                if (items.isNotEmpty()) dao.insertReceiptItems(items)
                receiptId
            }
        }

    override suspend fun duplicateReceipt(sourceId: Long): Long =
        withContext(Dispatchers.IO) {
            // Satu transaksi: header + item duplikat harus lahir bersamaan.
            // Versi lama tanpa transaksi bisa meninggalkan struk tanpa item.
            db.withTransaction {
                val source = dao.getReceiptById(sourceId) ?: return@withTransaction 0L
                val now = System.currentTimeMillis()

                // id = 0 supaya Room membuat primary key baru. createdAt/updatedAt
                // di-set ke sekarang karena ini memang baris baru bagi pengguna.
                val newId =
                    dao.insertReceipt(
                        source.copy(id = 0, createdAt = now, updatedAt = now),
                    )
                if (newId > 0) {
                    val items =
                        dao.getItemsByReceiptId(sourceId).first().map { item ->
                            item.copy(id = 0, receiptId = newId)
                        }
                    if (items.isNotEmpty()) dao.insertReceiptItems(items)
                }
                newId
            }
        }

    override suspend fun getReceiptsForExport(
        query: String?,
        startDate: Long?,
        endDate: Long?,
        categoryId: Long?,
        reviewStatus: String?,
    ): List<ReceiptRow> =
        withContext(Dispatchers.IO) {
            // Memakai query yang sama persis dengan daftar di layar. Dulu ekspor
            // lewat searchReceiptsSync yang hanya peduli kata kunci, sehingga
            // mengabaikan filter status/tanggal dan hasilnya beda dari yang dilihat user.
            dao.filterReceipts(query, startDate, endDate, categoryId, reviewStatus).first().map { receipt ->
                val merchant = receipt.merchantId?.let { dao.getMerchantById(it) }
                ReceiptRow(receipt = receipt, merchant = merchant, images = emptyList())
            }
        }

    override suspend fun getDashboardSummary(
        start: Long?,
        end: Long?,
    ): DashboardSummary =
        withContext(Dispatchers.IO) {
            DashboardSummary(
                totalRupiah = dao.getTotalInRange(start, end),
                count = dao.getCountInRange(start, end),
                pendingCount = dao.getPendingCount(),
                byCategory = dao.getTotalsByCategory(start, end),
                topMerchants = dao.getTopMerchants(start, end, TOP_MERCHANT_LIMIT),
            )
        }

    override fun categories(): Flow<List<com.notarapi.tidynote.core.model.CategoryEntity>> = dao.getAllCategories()

    override suspend fun restoreFromBackup(rows: List<Pair<ReceiptDraft, String>>): Int =
        withContext(Dispatchers.IO) {
            var count = 0
            for ((draft, status) in rows) {
                val merchantId = resolveMerchantId(draft.merchantName, draft.npwp)
                val entity =
                    ReceiptEntity(
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
                        invoiceNumber = draft.invoiceNumber,
                        imagePath = draft.imagePath,
                    )
                val id = dao.insertReceipt(entity)
                if (id > 0) count++
            }
            count
        }
}
