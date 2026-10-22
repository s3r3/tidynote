package com.notarapi.tidynote.feature.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notarapi.tidynote.R
import com.notarapi.tidynote.ui.design.staggerIn

@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    var aiOptIn by remember {
        mutableStateOf(
            context
                .getSharedPreferences("tidynote_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("cloud_ai_enabled", false),
        )
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(28.dp),
                        )
                        .staggerIn(0),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = "Logo TidyNote",
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.staggerIn(1),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggerIn(2),
            )
            Spacer(Modifier.height(32.dp))
            // Indeks stagger fitur mengikuti urutan daftar supaya tidak ada
            // angka ajaib berderet di tiap baris.
            val features =
                listOf(
                    Icons.Default.Receipt to stringResource(R.string.onboarding_feature_1),
                    Icons.Default.Translate to stringResource(R.string.onboarding_feature_2),
                    Icons.Default.Lock to stringResource(R.string.onboarding_feature_3),
                )
            val firstFeatureIndex = 3
            features.forEachIndexed { i, (icon, text) ->
                FeatureRow(icon, text, Modifier.staggerIn(firstFeatureIndex + i))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().staggerIn(firstFeatureIndex + features.size),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Bantuan AI cloud (opsional, default mati)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Jika aktif, hanya teks OCR yang dikirim ke backend Anda " +
                            "setelah Anda menekan Perbaiki dengan AI. Foto tidak dikirim.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = aiOptIn, onCheckedChange = { aiOptIn = it })
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    context
                        .getSharedPreferences("tidynote_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("onboarding_done", true)
                        .putBoolean("cloud_ai_enabled", aiOptIn)
                        .apply()
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).staggerIn(firstFeatureIndex + features.size + 1),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    stringResource(R.string.get_started),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Baris fitur: ikon dalam kotak lembut + teks, menggantikan emoji di strings. */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
