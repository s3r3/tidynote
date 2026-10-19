package com.notarapi.tidynote.core.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun formatCurrency(amountRupiah: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    return format.format(amountRupiah)
}

fun formatCurrencyOrDash(amountRupiah: Long?): String {
    if (amountRupiah == null) return "-"
    return formatCurrency(amountRupiah)
}

/** Batas digit agar input asal-asalan tidak melimpahi Long dan melempar NumberFormatException. */
private const val MAX_INPUT_DIGITS = 12

fun parseRupiahInput(str: String): Long {
    // Input field review: "15000", "15.000", "Rp 15.000" -> 15000
    val t = str.trim()
    if (t.isEmpty()) return 0L
    val digits = t.filter { it.isDigit() }
    if (digits.isEmpty()) return 0L
    return digits.take(MAX_INPUT_DIGITS).toLongOrNull() ?: 0L
}

fun formatDate(epochMilli: Long?): String {
    if (epochMilli == null) return "-"
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return sdf.format(java.util.Date(epochMilli))
}

fun formatDate(instant: java.time.Instant): String {
    val epochMilli = instant.toEpochMilli()
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return sdf.format(java.util.Date(epochMilli))
}
