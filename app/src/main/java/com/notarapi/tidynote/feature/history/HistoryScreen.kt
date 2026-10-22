package com.notarapi.tidynote.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.model.CategoryEntity
import com.notarapi.tidynote.core.model.ReceiptEntity
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.ui.design.EmptyState
import com.notarapi.tidynote.ui.design.StatusChip
import com.notarapi.tidynote.ui.design.TidyCard
import com.notarapi.tidynote.ui.design.TidyTab
import com.notarapi.tidynote.ui.design.TidyTabScaffold
import com.notarapi.tidynote.ui.design.staggerIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Jeda sebelum kata kunci pencarian dijalankan ke database. */
private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onScan: () -> Unit,
    onNavigateTab: (TidyTab) -> Unit,
    onOpenReceipt: (Long) -> Unit = {},
    // Tombol "Periksa N struk" di Home mengirim "PENDING" ke sini. Sebelumnya
    // layar ini selalu mulai dari "ALL", jadi tombol yang menjanjikan daftar
    // struk yang perlu diperiksa justru menampilkan semua struk.
    initialStatus: String = "ALL",
) {
    val repository: ReceiptRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    // Query yang benar-benar dipakai. Mengetik tidak langsung memicu LIKE '%x%'
    // ke seluruh rawOcrText; ditahan dulu supaya tiap huruf tidak jadi satu query.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = searchQuery
    }
    var selectedStatus by remember(initialStatus) { mutableStateOf(initialStatus) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val statusOptions = listOf("ALL", "PENDING", "REVIEWED")
    val categories by repository.categories().collectAsStateWithLifecycle(initialValue = emptyList())

    var dateRange by remember { mutableStateOf("ALL") }
    val startDate =
        remember(dateRange) {
            when (dateRange) {
                "30D" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                else -> null
            }
        }
    val receiptsFlow =
        remember(debouncedQuery, selectedStatus, startDate, selectedCategoryId) {
            repository.filterReceipts(
                search = debouncedQuery.ifBlank { null },
                startDate = startDate,
                endDate = null,
                categoryId = selectedCategoryId,
                reviewStatus = selectedStatus.takeIf { it != "ALL" },
            )
        }
    val receipts by receiptsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Dulu ada `filteredReceipts` yang memfilter ulang di memori hanya atas
    // rawOcrText + totalMinor. Itu sebabnya pencarian nama merchant tidak pernah
    // ketemu di layar, padahal ekspor menemukannya. Sekarang satu query saja.
    val filteredReceipts = receipts

    // Ekspor memakai filter yang sama dengan layar: dulu ia hanya menerima kata
    // kunci dan mengabaikan status/tanggal, jadi isi file tidak cocok dengan
    // yang dilihat user.
    fun export(isPdf: Boolean) {
        scope.launch {
            val rows =
                repository.getReceiptsForExport(
                    query = searchQuery.ifBlank { null },
                    startDate = startDate,
                    endDate = null,
                    categoryId = selectedCategoryId,
                    reviewStatus = selectedStatus.takeIf { it != "ALL" },
                )
            val result =
                if (isPdf) {
                    PdfExporter().exportReceipts(rows, context.contentResolver)
                } else {
                    CsvExporter().exportReceipts(rows, context.contentResolver)
                }
            exportMessage =
                if (result.isSuccess) {
                    if (isPdf) "PDF tersimpan di Downloads" else "CSV tersimpan di Downloads"
                } else {
                    "Gagal ekspor: ${result.exceptionOrNull()?.message}"
                }
        }
    }

    TidyTabScaffold(
        title = stringResource(R.string.view_history),
        selected = TidyTab.HISTORY,
        onNavigateTab = onNavigateTab,
        onScan = onScan,
        actions = {
            IconButton(onClick = { export(isPdf = true) }) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Ekspor PDF")
            }
            IconButton(onClick = { export(isPdf = false) }) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export_csv))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            exportMessage?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.search)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded },
                    modifier = Modifier.weight(1f),
                ) {
                    TextField(
                        value = selectedStatus,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.filter)) },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = "Buka filter status") },
                    )
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                    ) {
                        statusOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedStatus = option
                                    statusExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("ALL" to "Semua", "30D" to "30 hari").forEach { (v, label) ->
                    FilterChip(selected = dateRange == v, onClick = { dateRange = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))

            // Filter kategori. DAO dan repository sudah menerima categoryId sejak
            // awal, tapi UI-nya selalu mengirim null sehingga fitur ini tidak pernah
            // bisa dipakai. Sekarang datanya benar-benar bisa disaring per kategori.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Box {
                    OutlinedButton(onClick = { categoryExpanded = true }) {
                        Text(
                            categories.firstOrNull { it.id == selectedCategoryId }?.name
                                ?: "Semua kategori",
                        )
                    }
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Semua kategori") },
                            onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            },
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (filteredReceipts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = stringResource(R.string.no_receipts),
                        message = "Coba ubah kata kunci atau filter.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(filteredReceipts, key = { _, receipt -> receipt.id }) { index, receipt ->
                        ReceiptHistoryCard(
                            receipt = receipt,
                            onOpen = { onOpenReceipt(receipt.id) },
                            onDelete = {
                                scope.launch {
                                    val full = repository.getReceiptWithItems(receipt.id)
                                    repository.deleteReceipt(receipt.id)
                                    val paths =
                                        (full?.images?.map { it.imagePath } ?: emptyList()) + listOfNotNull(full?.receipt?.imagePath)
                                    paths.forEach { runCatching { java.io.File(it).delete() } }
                                }
                            },
                            modifier = Modifier.staggerIn(index),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptHistoryCard(
    receipt: ReceiptEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    TidyCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatCurrency(receipt.totalMinor ?: 0),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatDate(receipt.transactionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(status = receipt.reviewStatus)
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { Text(stringResource(R.string.delete_all_data_confirm)) },
        )
    }
}
