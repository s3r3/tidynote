package com.notarapi.tidynote.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Container standar: surfaceContainerLow + border tipis + radius besar.
 * Dipakai di semua layar supaya bobot visualnya seragam.
 */
@Composable
fun TidyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed && onClick != null) 0.985f else 1f,
            animationSpec = tween(Motion.FAST, easing = Motion.FastOutSlowIn),
            label = "cardPress",
        )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.scale(scale),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            interactionSource = interaction,
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            content = { content() },
        )
    }
}

/** Angka besar + label + ikon aksen. Dipakai di Home dan Ringkasan. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caption: String? = null,
    accent: Boolean = false,
) {
    val container =
        if (accent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val onContainer =
        if (accent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = container,
        border =
            if (accent) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = onContainer)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = onContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Chip status. PENDING sengaja amber (tertiary) — itu keadaan "perlu dilihat",
 * bukan kesalahan, jadi tidak boleh merah.
 */
@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    val pending = status.equals("PENDING", ignoreCase = true)
    val container =
        if (pending) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    val content =
        if (pending) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    val label =
        when {
            pending -> "Perlu diperiksa"
            status.equals("REVIEWED", ignoreCase = true) -> "Sudah dicek"
            else -> status
        }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = container,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Judul bagian + aksi opsional di kanan. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Keadaan kosong yang seragam: ikon, pesan, CTA opsional. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(percent = 50),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}
