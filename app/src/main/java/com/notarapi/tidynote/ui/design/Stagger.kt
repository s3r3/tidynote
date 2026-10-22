package com.notarapi.tidynote.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

/** Jarak naik (piksel) saat item muncul; cukup halus untuk tidak terasa "melompat". */
private const val RISE_PX = 16f

/**
 * Muncul sekali per item: fade + naik sedikit, dengan jeda bertingkat.
 * Satu kali saja per komposisi supaya daftar tidak berkedip saat datanya
 * dipancarkan ulang oleh Room setiap tabel berubah.
 */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = Motion.MEDIUM,
                    delayMillis = Motion.staggerDelay(index),
                    easing = Motion.EaseOutCubic,
                ),
            label = "staggerIn",
        )
    return this
        .alpha(progress)
        .graphicsLayer { translationY = (1f - progress) * RISE_PX }
}
