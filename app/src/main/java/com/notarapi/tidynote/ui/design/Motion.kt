package com.notarapi.tidynote.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Kurva & durasi tunggal untuk seluruh aplikasi.
 * Semua berasal dari compose-animation yang sudah ada — nol dependency baru.
 */
object Motion {
    val FastOutSlowIn = FastOutSlowInEasing
    val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 450

    /** Jeda muncul per item daftar; dibatasi agar item ke-100 tidak telat 4 detik. */
    const val STAGGER_STEP = 40
    const val STAGGER_CAP = 240

    fun staggerDelay(index: Int): Int = (index * STAGGER_STEP).coerceAtMost(STAGGER_CAP)
}
