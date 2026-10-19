package com.notarapi.tidynote.feature.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notarapi.tidynote.core.model.ReceiptEntity
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.core.util.formatDate
import com.notarapi.tidynote.data.receipt.DashboardSummary
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onDashboard: () -> Unit,
    onSettings: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
) {
    val repository: ReceiptRepository = koinInject()
    val zone = ZoneId.systemDefault()
    val startOfMonth = remember {
        LocalDate.now().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    // Dulu ringkasan dimuat SEKALI di dalam LaunchedEffect(startOfMonth).
    // `startOfMonth` di-remember dan tidak pernah berubah, jadi kembali dari
    // Simpan/Hapus lewat popBackStack tidak memicu pemuatan ulang: "Total bulan
    // ini" dan "N struk" menampilkan data sebelum perubahan.
    //
    // Room sudah memancarkan ulang Flow setiap tabel berubah, jadi tidak perlu
    // refresh manual. Ringkasan dihitung ulang di dalam collectLatest yang sama
    // supaya angka dan daftar tidak pernah tidak sinkron.
    var summary by remember { mutableStateOf(DashboardSummary()) }
    var recent by remember { mutableStateOf<List<ReceiptEntity>>(emptyList()) }
    LaunchedEffect(startOfMonth) {
        repository.getReceiptsPaged(5, 0).collectLatest { list ->
            recent = list
            summary = try {
                repository.getDashboardSummary(startOfMonth, null)
            } catch (_: Exception) {
                // Jangan biarkan kegagalan query mematikan layar.
                DashboardSummary()
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TidyNote") },
                actions = {
                    IconButton(onClick = onDashboard) { Icon(Icons.Default.BarChart, contentDescription = "Ringkasan") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.DocumentScanner, contentDescription = "Pindai struk")
                Spacer(Modifier.size(8.dp))
                Text("Pindai struk", fontWeight = FontWeight.Bold)
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Total bulan ini")
                    Text(formatCurrency(summary.totalRupiah), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${summary.count} struk")
                }
            }
            if (summary.pendingCount > 0) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Periksa ${summary.pendingCount} struk") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Transaksi terbaru", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = "Riwayat")
                    Spacer(Modifier.size(4.dp))
                    Text("Riwayat")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.id }) { r ->
                    Card(onClick = { onOpenReceipt(r.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(formatCurrency(r.totalMinor ?: 0), fontWeight = FontWeight.Bold)
                            Text("${formatDate(r.transactionDate)} - ${r.reviewStatus}")
                        }
                    }
                }
            }
        }
    }
}
