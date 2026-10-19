package com.notarapi.tidynote.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.notarapi.tidynote.R
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.core.model.ReceiptEntity
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
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
    var selectedStatus by remember(initialStatus) { mutableStateOf(initialStatus) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val statusOptions = listOf("ALL", "PENDING", "REVIEWED")
    val categories by repository.categories().collectAsStateWithLifecycle(initialValue = emptyList())

    var dateRange by remember { mutableStateOf("ALL") }
    val startDate = remember(dateRange) {
        when (dateRange) {
            "30D" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            else -> null
        }
    }
    val receiptsFlow = remember(searchQuery, selectedStatus, startDate, selectedCategoryId) {
        repository.filterReceipts(
            search = searchQuery.ifBlank { null },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.view_history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                // Filter yang sama dengan layar: dulu ekspor hanya
                                // menerima kata kunci dan mengabaikan status/tanggal,
                                // jadi isi file tidak cocok dengan yang dilihat user.
                                val rows = repository.getReceiptsForExport(
                                    query = searchQuery.ifBlank { null },
                                    startDate = startDate,
                                    endDate = null,
                                    categoryId = selectedCategoryId,
                                    reviewStatus = selectedStatus.takeIf { it != "ALL" },
                                )
                                val result = PdfExporter().exportReceipts(rows, context.contentResolver)
                                exportMessage = if (result.isSuccess) "PDF tersimpan di Downloads"
                                else "Gagal ekspor PDF: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Ekspor PDF")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val rows = repository.getReceiptsForExport(
                                    query = searchQuery.ifBlank { null },
                                    startDate = startDate,
                                    endDate = null,
                                    categoryId = selectedCategoryId,
                                    reviewStatus = selectedStatus.takeIf { it != "ALL" },
                                )
                                val result = CsvExporter().exportReceipts(rows, context.contentResolver)
                                exportMessage = if (result.isSuccess) "CSV tersimpan di Downloads"
                                else "Gagal ekspor: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export_csv))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            exportMessage?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    TextField(
                        value = selectedStatus,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.filter)) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = "Buka filter status") }
                    )
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statusOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedStatus = option
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Semua", "30D" to "30 hari").forEach { (v, label) ->
                    androidx.compose.material3.FilterChip(selected = dateRange == v, onClick = { dateRange = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))

            // Filter kategori. DAO dan repository sudah menerima categoryId sejak
            // awal, tapi UI-nya selalu mengirim null sehingga fitur ini tidak pernah
            // bisa dipakai. Sekarang datanya benar-benar bisa disaring per kategori.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Box {
                    androidx.compose.material3.OutlinedButton(onClick = { categoryExpanded = true }) {
                        Text(
                            categories.firstOrNull { it.id == selectedCategoryId }?.name
                                ?: "Semua kategori",
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Semua kategori") },
                            onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            },
                        )
                        categories.forEach { category ->
                            androidx.compose.material3.DropdownMenuItem(
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
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Belum ada struk", modifier = Modifier.size(64.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                        Text(stringResource(R.string.no_receipts), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredReceipts, key = { it.id }) { receipt ->
                        ReceiptHistoryCard(
                            receipt = receipt,
                            onOpen = { onOpenReceipt(receipt.id) },
                            onDelete = { scope.launch {
                                val full = repository.getReceiptWithItems(receipt.id)
                                repository.deleteReceipt(receipt.id)
                                val paths = (full?.images?.map { it.imagePath } ?: emptyList()) + listOfNotNull(full?.receipt?.imagePath)
                                paths.forEach { runCatching { java.io.File(it).delete() } }
                            } }
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
    onOpen: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatCurrency(receipt.totalMinor ?: 0),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${formatDate(receipt.transactionDate)} • ${receipt.reviewStatus}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { Text(stringResource(R.string.delete_all_data_confirm)) }
        )
    }
}