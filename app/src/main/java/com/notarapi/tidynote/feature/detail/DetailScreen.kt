package com.notarapi.tidynote.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.notarapi.tidynote.core.model.ReceiptWithItems
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.ui.design.SectionHeader
import com.notarapi.tidynote.ui.design.StatusChip
import com.notarapi.tidynote.ui.design.TidyCard
import com.notarapi.tidynote.ui.design.staggerIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    receiptId: Long,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    /** Bagikan berkas gambarnya, bukan teks. */
    onShareImage: (String) -> Unit = {},
    /** Duplikat struk ini sebagai baris baru, lalu buka hasilnya. */
    onDuplicate: (Long) -> Unit = {},
    /** Buka struk ini di layar Review untuk disunting. */
    onEdit: (Long) -> Unit = {},
) {
    val repository: ReceiptRepository = koinInject()
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ReceiptWithItems?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showOcr by remember { mutableStateOf(false) }
    LaunchedEffect(receiptId) { data = repository.getReceiptWithItems(receiptId) }

    val imagePath = data?.let { d -> d.images.firstOrNull()?.imagePath ?: d.receipt.imagePath }
    val hasImage = !imagePath.isNullOrBlank() && File(imagePath).exists()
    val pending = data?.receipt?.reviewStatus == "PENDING"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail struk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    // Salah ketik nominal dulu berarti harus hapus lalu scan ulang —
                    // tidak ada jalan untuk memperbaiki struk yang sudah tersimpan.
                    IconButton(onClick = { onEdit(receiptId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Sunting struk")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Aksi lain")
                        }
                        // Enam ikon di top bar tidak muat di layar sempit. Yang
                        // sering dipakai (sunting) tetap terlihat; sisanya di sini.
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // updateReviewStatus sebelumnya tidak punya pemanggil sama sekali:
                            // begitu struk disimpan PENDING, tidak ada jalan keluar. Antrian
                            // "Periksa N struk" di Home karena itu tidak pernah selesai.
                            if (pending) {
                                DropdownMenuItem(
                                    text = { Text("Tandai sudah diperiksa") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        val d = data ?: return@DropdownMenuItem
                                        scope.launch {
                                            repository.updateReviewStatus(d.receipt.id, "REVIEWED")
                                            data = d.copy(receipt = d.receipt.copy(reviewStatus = "REVIEWED"))
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Bagikan teks") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val d = data ?: return@DropdownMenuItem
                                    val text = "Struk ${d.merchant?.name ?: "-"} ${formatCurrency(
                                        d.receipt.totalMinor ?: 0,
                                    )} ${formatDate(d.receipt.transactionDate)}"
                                    onShare(text)
                                },
                            )
                            if (hasImage) {
                                DropdownMenuItem(
                                    text = { Text("Bagikan gambar") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onShareImage(imagePath.orEmpty())
                                    },
                                )
                            }
                            // R.string.duplicate sudah lama ada di strings.xml tapi tombolnya
                            // tidak pernah dibuat. Berguna untuk struk berulang dari merchant sama.
                            DropdownMenuItem(
                                text = { Text("Duplikat struk") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val d = data ?: return@DropdownMenuItem
                                    scope.launch { onDuplicate(d.receipt.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val d = data
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                if (hasImage) {
                    AsyncImage(
                        model = File(imagePath.orEmpty()),
                        contentDescription = "Gambar struk",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .staggerIn(0),
                    )
                }
                TidyCard(Modifier.fillMaxWidth().staggerIn(1)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                d.merchant?.name ?: "Tidak dikenal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            StatusChip(d.receipt.reviewStatus)
                        }
                        Text(
                            formatDate(d.receipt.transactionDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            formatCurrency(d.receipt.totalMinor ?: 0),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Subtotal ${formatCurrency(d.receipt.subtotalMinor ?: 0)}  •  " +
                                "PPN ${formatCurrency(d.receipt.taxMinor ?: 0)}  •  " +
                                "Diskon ${formatCurrency(d.receipt.discountMinor ?: 0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // NPWP & nomor invoice sekarang benar-benar tersimpan,
                        // jadi keduanya perlu terlihat di sini. Dulu dua field ini
                        // bisa diketik di layar Review lalu hilang saat disimpan.
                        d.merchant?.taxId?.takeIf { it.isNotBlank() }?.let { npwp ->
                            Text("NPWP: $npwp", style = MaterialTheme.typography.bodySmall)
                        }
                        d.receipt.invoiceNumber?.takeIf { it.isNotBlank() }?.let { inv ->
                            Text("No. invoice: $inv", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Sumber: ${d.receipt.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SectionHeader("Item (${d.items.size})", Modifier.staggerIn(2))
                d.items.forEachIndexed { index, item ->
                    TidyCard(Modifier.fillMaxWidth().staggerIn(index + 3)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${item.quantity}x ${item.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatCurrency(item.totalMinor),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
                if (d.receipt.rawOcrText.isNotBlank()) {
                    TidyCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        onClick = { showOcr = !showOcr },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Teks OCR",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    if (showOcr) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showOcr) "Sembunyikan teks OCR" else "Tampilkan teks OCR",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (showOcr) {
                                Text(
                                    d.receipt.rawOcrText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val paths = (data?.images?.map { it.imagePath } ?: emptyList()) + listOfNotNull(data?.receipt?.imagePath)
                        repository.deleteReceipt(receiptId)
                        paths.forEach { runCatching { File(it).delete() } }
                        onBack()
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
            text = { Text("Hapus struk ini beserta gambarnya?") },
        )
    }
}
