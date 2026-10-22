package com.notarapi.tidynote.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Empat tujuan tingkat atas. Detail/Review/Scan sengaja di luar ini. */
enum class TidyTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Beranda", Icons.Default.Home),
    HISTORY("history", "Riwayat", Icons.Default.History),
    DASHBOARD("dashboard", "Ringkasan", Icons.Default.BarChart),
    SETTINGS("settings", "Pengaturan", Icons.Default.Settings),
}

/**
 * Kerangka bersama untuk tab tingkat atas: top bar, bottom nav, dan FAB scan.
 * Layar cukup menyediakan isi; inset & padding sudah ditangani di sini supaya
 * tiap tab tidak perlu mengulang konfigurasi yang sama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidyTabScaffold(
    title: String,
    selected: TidyTab,
    onNavigateTab: (TidyTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = { actions() },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TidyTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { if (tab != selected) onNavigateTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        // Hanya inset atas/bawah yang diteruskan: padding FAB tidak perlu
        // karena FAB mengambang di atas konten.
        content(
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        )
    }
}
