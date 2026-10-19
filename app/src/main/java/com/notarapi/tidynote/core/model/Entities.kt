package com.notarapi.tidynote.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "receipts",
    indices = [
        Index("merchantId"),
        Index("categoryId"),
        Index("transactionDate"),
        Index("reviewStatus")
    ]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantId: Long? = null,
    val transactionDate: Long? = null,
    val currency: String = "IDR",
    val subtotalMinor: Long? = null,
    val discountMinor: Long? = null,
    val taxMinor: Long? = null,
    val totalMinor: Long? = null,
    val categoryId: Long? = null,
    val rawOcrText: String = "",
    val source: String = "LOCAL_PARSER",
    val confidence: Float = 0f,
    val reviewStatus: String = "PENDING",
    /** Nomor invoice/faktur. Ditambahkan di skema v3 (MIGRATION_2_3). */
    val invoiceNumber: String? = null,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isPending: Boolean get() = reviewStatus == "PENDING"
    val isReviewed: Boolean get() = reviewStatus == "REVIEWED"
}

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    val quantity: Int = 1,
    val unitPriceMinor: Long = 0,
    val totalMinor: Long = 0,
    val confidence: Float = 0f
)

@Entity(
    tableName = "merchants",
    indices = [Index(value = ["name"], unique = true)]
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val taxId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#6200EE",
    val icon: String = "category"
)

@Entity(
    tableName = "scan_images",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ScanImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "extraction_attempts",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ExtractionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val method: String = "LOCAL_PARSER",
    val status: String = "SUCCESS",
    val confidence: Float = 0f,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReceiptWithItems(
    val receipt: ReceiptEntity,
    val items: List<ReceiptItemEntity>,
    val merchant: MerchantEntity?,
    val images: List<ScanImageEntity>
)