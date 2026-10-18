package com.notarapi.tidynote.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.notarapi.tidynote.core.model.ReceiptWithItems
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(receiptId: Long, onBack: () -> Unit, onShare: (String) -> Unit) {
    val repository: ReceiptRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ReceiptWithItems?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(receiptId) { data = repository.getReceiptWithItems(receiptId) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Detail struk") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
        }, actions = {
            IconButton(onClick = {
                val d = data ?: return@IconButton
                val text = "Struk ${d.merchant?.name ?: "-"} ${formatCurrency(d.receipt.totalMinor ?: 0)} ${formatDate(d.receipt.transactionDate)}"
                onShare(text)
            }) { Icon(Icons.Default.Share, contentDescription = "Bagikan") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
        })
    }) { padding ->
        val d = data
        if (d == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("Memuat...") }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val img = d.images.firstOrNull()?.imagePath ?: d.receipt.imagePath
                if (!img.isNullOrBlank() && File(img).exists()) {
                    AsyncImage(model = File(img), contentDescription = "Gambar struk", modifier = Modifier.fillMaxWidth().height(280.dp))
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.merchant?.name ?: "Tidak dikenal", fontWeight = FontWeight.Bold)
                        Text(formatDate(d.receipt.transactionDate))
                        Text("Status: ${d.receipt.reviewStatus} - Sumber: ${d.receipt.source}")
                        Text("Total: ${formatCurrency(d.receipt.totalMinor ?: 0)}", fontWeight = FontWeight.Bold)
                        Text("Subtotal ${formatCurrency(d.receipt.subtotalMinor ?: 0)} - PPN ${formatCurrency(d.receipt.taxMinor ?: 0)} - Diskon ${formatCurrency(d.receipt.discountMinor ?: 0)}")
                    }
                }
                Text("Item (${d.items.size})", fontWeight = FontWeight.Bold)
                d.items.forEach { it ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${it.quantity}x ${it.name}", modifier = Modifier.weight(1f))
                        Text(formatCurrency(it.totalMinor))
                    }
                }
                if (d.receipt.rawOcrText.isNotBlank()) {
                    Text("Teks OCR", fontWeight = FontWeight.Bold)
                    Card(Modifier.fillMaxWidth()) { Text(d.receipt.rawOcrText, modifier = Modifier.padding(12.dp)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, confirmButton = {
            Button(onClick = {
                confirmDelete = false
                scope.launch {
                    val paths = (data?.images?.map { it.imagePath } ?: emptyList()) + listOfNotNull(data?.receipt?.imagePath)
                    repository.deleteReceipt(receiptId)
                    paths.forEach { runCatching { File(it).delete() } }
                    onBack()
                }
            }) { Text("Hapus") }
        }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        text = { Text("Hapus struk ini beserta gambarnya?") })
    }
}
