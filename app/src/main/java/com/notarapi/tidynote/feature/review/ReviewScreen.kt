package com.notarapi.tidynote.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ReceiptItemDraft
import com.notarapi.tidynote.core.util.ConfidenceColor
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import com.notarapi.tidynote.core.util.parseRupiahInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    draft: ReceiptDraft,
    onSave: (ReceiptDraft) -> Unit,
    onCancel: () -> Unit,
    onFixWithAi: ((String) -> Unit)? = null,
) {
    var editableDraft by remember(draft) { mutableStateOf(draft) }
    var showItemEditor by remember { mutableStateOf<ReceiptItemDraft?>(null) }
    var isNewItem by remember { mutableStateOf(false) }
    var showAiConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.review_title)) },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (onFixWithAi != null && editableDraft.confidence.needsReview) {
                    TextButton(onClick = { showAiConfirm = true }) {
                        Text(stringResource(R.string.fix_with_ai))
                    }
                }
                IconButton(onClick = { onSave(editableDraft.copyWithDefaults()) }) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MerchantSection(draft = editableDraft, onEdit = { editableDraft = it }) }
            item { DateSection(draft = editableDraft, onEdit = { editableDraft = it }) }
            item { CategorySection(draft = editableDraft, onEdit = { editableDraft = it }) }
            if (!editableDraft.isReconciled && editableDraft.totalMinor != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            "Selisih hitung: item + pajak - diskon tidak sama dengan total. Periksa kembali sebelum simpan.",
                            modifier = Modifier.padding(12.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { ItemsSection(
                items = editableDraft.items,
                onItemEdit = {
                    isNewItem = false
                    showItemEditor = it
                },
                onItemDelete = { editableDraft = editableDraft.copy(items = editableDraft.items - it) },
                onAddItem = {
                    isNewItem = true
                    showItemEditor = ReceiptItemDraft()
                }
            ) }
            item { TotalsSection(draft = editableDraft, onEdit = { editableDraft = it }) }
        }
    }

    showItemEditor?.let { editing ->
        ItemEditorDialog(
            item = editing,
            onDismiss = { showItemEditor = null },
            onConfirm = { updated ->
                editableDraft = if (isNewItem) {
                    editableDraft.copy(items = editableDraft.items + updated)
                } else {
                    editableDraft.copy(items = editableDraft.items.map { if (it == editing) updated else it })
                }
                showItemEditor = null
            }
        )
    }

    if (showAiConfirm) {
        AlertDialog(
            onDismissRequest = { showAiConfirm = false },
            title = { Text(stringResource(R.string.fix_with_ai)) },
            text = {
                Text(
                    "Hanya teks OCR (maks 8000 karakter, bukan foto) yang dikirim " +
                        "ke backend Anda. Lanjutkan?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiConfirm = false
                        onFixWithAi?.invoke(editableDraft.rawOcrText)
                    },
                ) { Text("Kirim") }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun MerchantSection(draft: ReceiptDraft, onEdit: (ReceiptDraft) -> Unit) {
    var name by remember(draft.merchantName) { mutableStateOf(draft.merchantName.orEmpty()) }
    var npwp by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.merchant), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ConfidenceBadge(confidence = draft.confidence.merchant)
            }
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = name,
                onValueChange = {
                    name = it
                    onEdit(draft.copy(merchantName = it.ifBlank { null }))
                },
                label = { Text(stringResource(R.string.merchant_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = npwp,
                onValueChange = { npwp = it },
                label = { Text(stringResource(R.string.npwp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun DateSection(draft: ReceiptDraft, onEdit: (ReceiptDraft) -> Unit) {
    var dateStr by remember(draft.transactionDate) { mutableStateOf(draft.transactionDate?.let { formatDate(it) } ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.date), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ConfidenceBadge(confidence = draft.confidence.date)
            }
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = dateStr,
                onValueChange = { dateStr = it },
                label = { Text(stringResource(R.string.date_format_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySection(draft: ReceiptDraft, onEdit: (ReceiptDraft) -> Unit) {
    val repository: com.notarapi.tidynote.data.receipt.ReceiptRepository =
        org.koin.compose.koinInject()
    var categories by remember { mutableStateOf<List<com.notarapi.tidynote.core.model.CategoryEntity>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repository.categories().collect { categories = it }
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == draft.categoryId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.category), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ConfidenceBadge(confidence = draft.confidence.merchant)
            }
            Spacer(modifier = Modifier.size(8.dp))
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selected?.name ?: "Pilih kategori",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.category)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    singleLine = true,
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = "Buka pilihan kategori") }
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    content = {
                        categories.forEach { cat ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    expanded = false
                                    onEdit(draft.copy(categoryId = cat.id))
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ItemsSection(
    items: List<ReceiptItemDraft>,
    onItemEdit: (ReceiptItemDraft) -> Unit,
    onItemDelete: (ReceiptItemDraft) -> Unit,
    onAddItem: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.items), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onAddItem) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.add_item))
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        Text("${item.quantity} x ${formatCurrency(item.unitPriceMinor)}", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { onItemEdit(item) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { onItemDelete(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (index < items.lastIndex) {
                    Divider()
                }
            }
        }
    }
}

@Composable
fun TotalsSection(draft: ReceiptDraft, onEdit: (ReceiptDraft) -> Unit) {
    var subtotal by remember(draft.subtotalMinor) { mutableStateOf(draft.subtotalMinor ?: 0L) }
    var tax by remember(draft.taxMinor) { mutableStateOf(draft.taxMinor ?: 0L) }
    var total by remember(draft.totalMinor) { mutableStateOf(draft.totalMinor ?: 0L) }
    var discount by remember(draft.discountMinor) { mutableStateOf(draft.discountMinor ?: 0L) }
    var invoice by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.totals), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.subtotal))
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    value = formatCurrency(subtotal),
                    onValueChange = {
                        subtotal = parseRupiahInput(it)
                        onEdit(draft.copy(subtotalMinor = subtotal))
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.discount))
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    value = formatCurrency(discount),
                    onValueChange = {
                        discount = parseRupiahInput(it)
                        onEdit(draft.copy(discountMinor = discount))
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.tax))
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    value = formatCurrency(tax),
                    onValueChange = {
                        tax = parseRupiahInput(it)
                        onEdit(draft.copy(taxMinor = tax))
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.total), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    value = formatCurrency(total),
                    onValueChange = {
                        total = parseRupiahInput(it)
                        onEdit(draft.copy(totalMinor = total))
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.invoice_number))
                Spacer(modifier = Modifier.weight(1f))
                TextField(
                    value = invoice,
                    onValueChange = { invoice = it },
                    modifier = Modifier.width(160.dp),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: Float) {
    val (color, icon) = ConfidenceColor.get(confidence)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = "Confidence", tint = color, modifier = Modifier.size(16.dp))
        Text("${(confidence * 100).toInt()}%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ItemEditorDialog(
    item: ReceiptItemDraft,
    onDismiss: () -> Unit,
    onConfirm: (ReceiptItemDraft) -> Unit
) {
    var name by remember(item) { mutableStateOf(item.name) }
    var qtyText by remember(item) { mutableStateOf(item.quantity.toString()) }
    var priceText by remember(item) { mutableStateOf(item.unitPriceMinor.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.items)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.items)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Qty") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.total)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toIntOrNull()?.coerceIn(1, 999) ?: 1
                    val price = priceText.toLongOrNull() ?: 0L
                    onConfirm(
                        item.copy(
                            name = name.ifBlank { "Item" },
                            quantity = qty,
                            unitPriceMinor = price,
                            totalMinor = price * qty
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}