package com.notarapi.tidynote.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.data.receipt.DashboardSummary
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.ui.design.EmptyState
import com.notarapi.tidynote.ui.design.Motion
import com.notarapi.tidynote.ui.design.SectionHeader
import com.notarapi.tidynote.ui.design.StatCard
import com.notarapi.tidynote.ui.design.TidyCard
import com.notarapi.tidynote.ui.design.TidyTab
import com.notarapi.tidynote.ui.design.TidyTabScaffold
import com.notarapi.tidynote.ui.design.staggerIn
import com.notarapi.tidynote.ui.theme.ChartPalette
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.ZoneId

/** Struk yang diambil untuk menghitung ringkasan bulan ini. */
private const val RECENT_LIMIT = 50

@Composable
fun DashboardScreen(
    onScan: () -> Unit,
    onNavigateTab: (TidyTab) -> Unit,
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
    var summary by remember { mutableStateOf(DashboardSummary()) }

    // Dulu: LaunchedEffect(startOfMonth) dengan `try { ... } finally { isLoading =
    // false }` TANPA catch. Exception dari database naik ke coroutine yang tidak
    // ada penangkapnya -> aplikasi mati. Selain itu, karena kuncinya tidak pernah
    // berubah, ringkasan tetap basi setelah user menyimpan atau menghapus struk.
    // Sekarang ringkasan dihitung ulang setiap isi tabel berubah, dan kegagalan
    // query tidak lagi menjatuhkan layar.
    LaunchedEffect(startOfMonth) {
        repository.getReceiptsPaged(RECENT_LIMIT, 0).collectLatest {
            summary =
                try {
                    repository.getDashboardSummary(startOfMonth, null)
                } catch (_: Exception) {
                    DashboardSummary()
                }
        }
    }

    TidyTabScaffold(
        title = stringResource(R.string.monthly_summary),
        selected = TidyTab.DASHBOARD,
        onNavigateTab = onNavigateTab,
        onScan = onScan,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatCard(
                    label = stringResource(R.string.total_this_month),
                    value = formatCurrency(summary.totalRupiah),
                    caption = "${summary.count} struk • ${summary.pendingCount} perlu diperiksa",
                    icon = Icons.Default.Insights,
                    accent = true,
                    modifier = Modifier.fillMaxWidth().staggerIn(0),
                )
            }

            if (summary.count == 0) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Belum ada data bulan ini",
                        message = "Scan struk pertamamu, ringkasan akan muncul di sini.",
                        actionLabel = "Scan sekarang",
                        onAction = onScan,
                    )
                }
            }

            if (summary.byCategory.isNotEmpty()) {
                item {
                    TidyCard(modifier = Modifier.fillMaxWidth().staggerIn(1)) {
                        Column(Modifier.padding(20.dp)) {
                            SectionHeader(title = "Per kategori")
                            Spacer(Modifier.height(16.dp))
                            CategoryDonut(
                                values = summary.byCategory.map { it.total },
                                modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.height(16.dp))
                            summary.byCategory.forEachIndexed { index, row ->
                                CategoryLegendRow(
                                    color = ChartPalette[index % ChartPalette.size],
                                    // Dulu menampilkan "Kategori ${row.categoryId}" —
                                    // id mentah dari database, bukan nama yang bisa dibaca.
                                    label = row.categoryName ?: "Tanpa kategori",
                                    amount = row.total,
                                )
                            }
                        }
                    }
                }
            }

            if (summary.topMerchants.isNotEmpty()) {
                item {
                    TidyCard(modifier = Modifier.fillMaxWidth().staggerIn(2)) {
                        Column(Modifier.padding(20.dp)) {
                            SectionHeader(title = "Top merchant")
                            Spacer(Modifier.height(8.dp))
                            summary.topMerchants.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            m.merchantName ?: "Tidak dikenal",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "${m.count}x transaksi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        formatCurrency(m.total),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(
    color: Color,
    label: String,
    amount: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            formatCurrency(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryDonut(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val total = values.sum().coerceAtLeast(1L).toFloat()
    // Sweep dianimasikan sekali saat data tiba: donut tumbuh dari nol, bukan
    // muncul mendadak. Animatable (bukan animateFloatAsState) supaya nilai
    // awalnya benar-benar 0 sebelum frame pertama.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Motion.SLOW, easing = Motion.EaseOutCubic))
    }

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.2f, cap = StrokeCap.Butt)
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v.toFloat() / total) * 360f * progress.value
            drawArc(
                color = ChartPalette[i % ChartPalette.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
            startAngle += sweep
        }
    }
}
