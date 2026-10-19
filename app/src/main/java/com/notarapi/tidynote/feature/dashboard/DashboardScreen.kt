package com.notarapi.tidynote.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notarapi.tidynote.R
import com.notarapi.tidynote.core.util.formatCurrency
import com.notarapi.tidynote.data.receipt.DashboardSummary
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.ZoneId

/** Struk yang diambil untuk menghitung ringkasan bulan ini. */
private const val RECENT_LIMIT = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigateBack: () -> Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monthly_summary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                    ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.total_this_month),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        formatCurrency(summary.totalRupiah),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${summary.count} struk • ${summary.pendingCount} perlu diperiksa",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (summary.byCategory.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Per kategori", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        CategoryDonut(
                            values = summary.byCategory.map { it.total },
                            modifier = Modifier.size(140.dp).align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(12.dp))
                        summary.byCategory.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Dulu menampilkan "Kategori ${row.categoryId}" — id
                                // mentah dari database, bukan nama yang bisa dibaca.
                                Text(row.categoryName ?: "Tanpa kategori")
                                Text(formatCurrency(row.total), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (summary.topMerchants.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Top merchant", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        summary.topMerchants.forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.merchantName ?: "Tidak dikenal", fontWeight = FontWeight.Medium)
                                    Text(
                                        "${m.count}x transaksi",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(formatCurrency(m.total), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val total = values.sum().coerceAtLeast(1L).toFloat()
    val colors =
        listOf(
            androidx.compose.ui.graphics
                .Color(0xFF4CAF50),
            androidx.compose.ui.graphics
                .Color(0xFF2196F3),
            androidx.compose.ui.graphics
                .Color(0xFFFF9800),
            androidx.compose.ui.graphics
                .Color(0xFF9C27B0),
            androidx.compose.ui.graphics
                .Color(0xFFF44336),
            androidx.compose.ui.graphics
                .Color(0xFF607D8B),
        )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v.toFloat() / total) * 360f
            drawArc(
                color = colors[i % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style =
                    androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 28f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt,
                    ),
            )
            startAngle += sweep
        }
    }
}
