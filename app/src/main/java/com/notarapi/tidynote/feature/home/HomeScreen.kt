package com.notarapi.tidynote.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.notarapi.tidynote.ui.design.EmptyState
import com.notarapi.tidynote.ui.design.SectionHeader
import com.notarapi.tidynote.ui.design.StatCard
import com.notarapi.tidynote.ui.design.StatusChip
import com.notarapi.tidynote.ui.design.TidyCard
import com.notarapi.tidynote.ui.design.TidyTab
import com.notarapi.tidynote.ui.design.TidyTabScaffold
import com.notarapi.tidynote.ui.design.staggerIn
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.ZoneId

/** Struk terbaru yang ditampilkan di beranda. */
private const val RECENT_LIMIT = 5

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    /** Membuka Riwayat yang sudah tersaring ke PENDING. */
    onReviewPending: () -> Unit,
    onNavigateTab: (TidyTab) -> Unit,
    onOpenReceipt: (Long) -> Unit,
) {
    val repository: ReceiptRepository = koinInject()
    val zone = ZoneId.systemDefault()
    val startOfMonth =
        remember {
            LocalDate
                .now()
                .withDayOfMonth(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }

    // Ringkasan dihitung ulang di dalam collectLatest yang sama dengan daftar
    // supaya angka dan baris tidak pernah tidak sinkron. Room sudah memancarkan
    // ulang setiap tabel berubah, jadi tidak perlu refresh manual.
    var summary by remember { mutableStateOf(DashboardSummary()) }
    var recent by remember { mutableStateOf<List<ReceiptEntity>>(emptyList()) }
    LaunchedEffect(startOfMonth) {
        repository.getReceiptsPaged(RECENT_LIMIT, 0).collectLatest { list ->
            recent = list
            summary =
                try {
                    repository.getDashboardSummary(startOfMonth, null)
                } catch (_: Exception) {
                    // Kegagalan query tidak boleh mematikan layar.
                    DashboardSummary()
                }
        }
    }

    TidyTabScaffold(
        title = "TidyNote",
        selected = TidyTab.HOME,
        onNavigateTab = onNavigateTab,
        onScan = onScan,
    ) { padding ->
        // Seluruh layar satu LazyColumn: daftar panjang di dalam Column yang
        // di-scroll akan membuat scroll bersarang.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatCard(
                    label = "Total bulan ini",
                    value = formatCurrency(summary.totalRupiah),
                    caption = "${summary.count} struk",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accent = true,
                    modifier = Modifier.fillMaxWidth().staggerIn(0),
                )
            }

            if (summary.pendingCount > 0) {
                item {
                    TidyCard(
                        onClick = onReviewPending,
                        modifier = Modifier.fillMaxWidth().staggerIn(1),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Periksa ${summary.pendingCount} struk",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Ada struk yang belum kamu cek",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusChip(status = "PENDING")
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Transaksi terbaru",
                    actionLabel = "Lihat semua",
                    onAction = { onNavigateTab(TidyTab.HISTORY) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Belum ada struk",
                        message = "Scan struk pertamamu lewat tombol di bawah.",
                    )
                }
            } else {
                itemsIndexed(recent, key = { _, r -> r.id }) { index, r ->
                    RecentReceiptRow(
                        receipt = r,
                        onClick = { onOpenReceipt(r.id) },
                        modifier = Modifier.staggerIn(index + 2),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentReceiptRow(
    receipt: ReceiptEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TidyCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatCurrency(receipt.totalMinor ?: 0),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatDate(receipt.transactionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = receipt.reviewStatus)
        }
    }
}
