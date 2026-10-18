package com.notarapi.tidynote.core.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object ConfidenceColor {
    fun get(confidence: Float): Pair<Color, ImageVector> = when {
        confidence >= 0.8f -> Color(0xFF2E7D32) to Icons.Filled.CheckCircle   // Green
        confidence >= 0.5f -> Color(0xFFF57F17) to Icons.Filled.Warning        // Amber
        else -> Color(0xFFC62828) to Icons.Filled.Info                         // Red
    }
}