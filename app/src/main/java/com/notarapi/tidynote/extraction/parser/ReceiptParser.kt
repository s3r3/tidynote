package com.notarapi.tidynote.extraction.parser

import com.notarapi.tidynote.core.model.ExtractionSource
import com.notarapi.tidynote.core.model.FieldConfidence
import com.notarapi.tidynote.core.model.ReceiptDraft
import com.notarapi.tidynote.core.model.ReceiptItemDraft
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class ReceiptParser {
    companion object {
        private const val IDR_LOCALE = "id-ID"
        private val PRICE_PATTERN =
            Regex(
                """(?:Rp\s*|IDR\s*)?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)\s*(?:/|$|;)""",
                RegexOption.IGNORE_CASE,
            )
    }

    fun parse(rawText: String): ReceiptDraft {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val merchant = extractMerchant(lines)
        val date = extractDate(lines)
        val items = extractItems(lines)
        val subtotal = extractSubtotal(lines)
        val discount = extractDiscount(lines)
        val tax = extractTax(lines)
        val total = extractTotal(lines)
        val categoryId = guessCategoryId(merchant, items)
        val confidence = buildConfidence(merchant, date, total, items, tax, discount)

        return ReceiptDraft(
            merchantName = merchant,
            transactionDate = date?.let { Instant.ofEpochMilli(it) },
            currency = "IDR",
            items = items,
            // NOTE: untuk IDR, minor unit == rupiah (tanpa sen).
            // plan.md: "25.000 disimpan sebagai 25000".
            subtotalMinor = subtotal,
            discountMinor = discount,
            taxMinor = tax,
            totalMinor = total,
            categoryId = categoryId,
            rawOcrText = rawText,
            source = ExtractionSource.LOCAL_PARSER,
            confidence = confidence,
        )
    }

    private fun buildConfidence(
        merchant: String?,
        date: Long?,
        total: Long?,
        items: List<ReceiptItemDraft>,
        tax: Long?,
        discount: Long?,
    ): FieldConfidence {
        val merchantConf = if (merchant != null && merchant != "Tidak Dikenal") 0.9f else 0.2f
        val dateConf = if (date != null && date > 946684800000L) 0.9f else 0.3f
        val totalConf = if (total != null && total > 0) 0.85f else 0.3f
        val itemsConf = if (items.isNotEmpty()) 0.8f else 0.2f
        val taxConf = if (tax != null && tax > 0) 0.7f else 0.4f
        val discountConf = if (discount != null && discount > 0) 0.7f else 0.5f

        return FieldConfidence(
            merchant = merchantConf,
            date = dateConf,
            total = totalConf,
            items = itemsConf,
            tax = taxConf,
            discount = discountConf,
        )
    }

    private fun extractMerchant(lines: List<String>): String {
        val skipWords =
            setOf(
                "struk",
                "nota",
                "kasir",
                "teller",
                "pos ",
                "cabang",
                "toko no",
                "toko#",
                "tanggal",
                "waktu",
                "telp",
                "telepon",
                "alamat",
                "pesanan",
                "meja",
                "terima",
                "kembalian",
                "total",
                "subtotal",
                "pajak",
                "ppn",
                "qty",
                "jumlah",
                "item",
                "deskripsi",
                "harga",
                "jumlah",
                "saldo",
                "tunai",
                "tender",
                "kartu",
                "visa",
                "mastercard",
                "debit",
                "kredit",
                "rekening",
                "referensi",
                "transaksi",
                "halaman",
                "penjualan",
                "register",
                "struk no",
                "serial",
                "or no",
                "pesanan no",
                "nota no",
                "tagihan no",
                "struk resmi",
                "invoice penjualan",
                "cetak",
                "online",
                "npwp",
                "npwp:",
                "nama",
                "alamat toko",
            )

        for (line in lines.take(10)) {
            val cleaned = line.replace(Regex("[^a-zA-Z0-9\\s&.,'()-]"), "").trim()
            if (cleaned.length < 3 || cleaned.length > 80) continue
            val lower = cleaned.lowercase()
            if (skipWords.any { lower.startsWith(it) }) continue
            if (skipWords.any { lower.contains(" $it") }) continue
            val letterCount = cleaned.count { it.isLetter() }
            val digitCount = cleaned.count { it.isDigit() }
            if (letterCount < 3) continue
            val digitRatio = digitCount.toFloat() / cleaned.length
            if (digitRatio < 0.35f) return cleaned
        }

        for (line in lines.take(3)) {
            val cleaned = line.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (cleaned.length >= 3) return cleaned.take(80)
        }
        return "Tidak Dikenal"
    }

    private fun extractDate(lines: List<String>): Long? {
        val datePatterns =
            listOf(
                Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})"""),
                Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})"""),
            )
        val formatsDDMM =
            listOf(
                "dd/MM/yyyy",
                "d/MM/yyyy",
                "dd/M/yyyy",
                "d/M/yyyy",
                "dd-MM-yyyy",
                "d-MM-yyyy",
                "dd.MM.yyyy",
                "d.MM.yyyy",
                "dd/MM/yy",
                "dd-MM-yy",
            )
        val formatsMMDD =
            listOf(
                "MM/dd/yyyy",
                "MM-dd-yyyy",
                "MM.dd.yyyy",
                "MM/dd/yy",
                "MM-dd-yy",
            )
        val formatsText =
            listOf(
                "dd MMM yyyy",
                "dd MMMM yyyy",
                "dd-MMM-yyyy",
                "yyyy MMM dd",
                "yyyy MMMM dd",
                "EEE, dd MMM yyyy",
                "EEEE, dd MMMM yyyy",
            )

        for (line in lines) {
            val cleaned = line.replace(Regex("[•\\-–—|_\"'&#*@!~`]"), " ").trim()
            for (pattern in datePatterns) {
                for (match in pattern.findAll(cleaned)) {
                    val groups = match.groupValues.drop(1)
                    for (formats in listOf(formatsDDMM, formatsMMDD)) {
                        for (format in formats) {
                            try {
                                val sdf = SimpleDateFormat(format, Locale.US)
                                sdf.isLenient = false
                                val parts =
                                    if (format.contains("yyyy")) {
                                        if (groups[2].length == 4) {
                                            groups
                                        } else {
                                            listOf(groups[0], groups[1], "20${groups[2]}")
                                        }
                                    } else {
                                        groups
                                    }
                                val d = sdf.parse(parts.joinToString("/"))
                                if (d != null && d.time > 946684800000L) return d.time
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        val indonesianMonths =
            mapOf(
                "jan" to 1,
                "januari" to 1,
                "feb" to 2,
                "februari" to 2,
                "mar" to 3,
                "maret" to 3,
                "apr" to 4,
                "april" to 4,
                "mei" to 5,
                "may" to 5,
                "jun" to 6,
                "juni" to 6,
                "jul" to 7,
                "juli" to 7,
                "agu" to 8,
                "agustus" to 8,
                "sep" to 9,
                "sept" to 9,
                "september" to 9,
                "okt" to 10,
                "oktober" to 10,
                "nov" to 11,
                "november" to 11,
                "des" to 12,
                "dec" to 12,
                "desember" to 12,
            )

        for (line in lines) {
            val cleaned = line.replace(Regex("[•\\-–—|_\"'&#*@!~`,]"), " ").trim().lowercase()
            for ((monthName, monthNum) in indonesianMonths) {
                val pattern = Regex("""(\d{1,2})\s+$monthName\s+(\d{4})""")
                val match = pattern.find(cleaned)
                if (match != null) {
                    val day = match.groupValues[1].toIntOrNull()
                    val year = match.groupValues[2].toIntOrNull()
                    if (day != null && year != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(year, monthNum - 1, day)
                        if (cal.timeInMillis > 946684800000L) return cal.timeInMillis
                    }
                }
            }
        }

        for (line in lines) {
            val cleaned = line.replace(Regex("[•\\-–—|_\"'&#*@!~`,]"), " ").trim()
            for (format in formatsText) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.isLenient = false
                    val d = sdf.parse(cleaned)
                    if (d != null && d.time > 946684800000L) return d.time
                } catch (_: Exception) {
                }
                val words = cleaned.split(" ")
                for (i in 0..words.size - 3) {
                    val chunk = words.subList(i, i + 3).joinToString(" ")
                    try {
                        val sdf = SimpleDateFormat(format, Locale.US)
                        sdf.isLenient = false
                        val d = sdf.parse(chunk)
                        if (d != null && d.time > 946684800000L) return d.time
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return null
    }

    private fun extractItems(lines: List<String>): List<ReceiptItemDraft> {
        val items = mutableListOf<ReceiptItemDraft>()
        val skipWords =
            setOf(
                "total",
                "subtotal",
                "pajak",
                "ppn",
                "ppnbm",
                "tunai",
                "kembalian",
                "dibayar",
                "utang",
                "diskon",
                "potongan",
                "biaya",
                "layanan",
                "service",
                "charge",
                "cash",
                "change",
                "due",
                "tender",
                "amount",
                "balance",
                "discount",
                "fee",
            )

        var startIndex = 0
        for ((i, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (lower.contains(Regex("qty|jumlah|deskripsi|harga|item|nama barang"))) {
                if (lower.contains("total")) break
                startIndex = i + 1
                break
            }
        }

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val lower = line.lowercase()
            if (skipWords.any { lower.contains(it) }) break
            if (line.length < 3) continue

            val priceMatch = PRICE_PATTERN.find(line)
            if (priceMatch != null) {
                val price = parseIdrToRupiah(priceMatch.groupValues[1])
                var name =
                    line
                        .replace(priceMatch.value, "")
                        .trim()
                        .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                        .trim()
                        .replace(Regex("\\s+"), " ")

                val qtyPattern = Regex("""(\d+)\s*x\s*""")
                val qtyMatch = qtyPattern.find(line)
                val quantity = qtyMatch?.groupValues[1]?.toIntOrNull() ?: 1

                if (name.isNotEmpty() && price > 0) {
                    items.add(
                        ReceiptItemDraft(
                            name = name.take(100),
                            quantity = quantity,
                            unitPriceMinor = price,
                            totalMinor = price * quantity,
                            confidence = 0.8f,
                        ),
                    )
                }
            }
        }
        return items
    }

    private fun extractSubtotal(lines: List<String>): Long? =
        extractValue(lines, "subtotal|sub total|total belanja|jumlah belanja|netto|net amount")

    private fun extractDiscount(lines: List<String>): Long? = extractValue(lines, "diskon|potongan|discount|promo|voucher")

    private fun extractTax(lines: List<String>): Long? = extractValue(lines, "pajak|ppn|ppnbm|pph|tax|vat|gst|service charge|biaya layanan")

    private fun extractValue(
        lines: List<String>,
        keyword: String,
    ): Long? {
        val regex =
            Regex(
                """(?:$keyword)[\s:;]*[Rr][Pp]?\s*([\d.,]+)""",
                RegexOption.IGNORE_CASE,
            )
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                return parseIdrToRupiah(match.groupValues[1])
            }
        }
        return null
    }

    private fun extractTotal(lines: List<String>): Long? {
        val excludeKeywords = listOf("ppn", "pajak", "ppnbm", "subtotal", "diskon", "potongan", "biaya layanan")
        val currencyPrefix = """[Rr][Pp]?\s*"""
        val decimalPrice = """([\d.,]+)"""

        val passes =
            listOf(
                listOf(
                    "total",
                    "total bayar",
                    "grand total",
                    "total amount",
                    "total tagihan",
                    "jumlah bayar",
                    "total pembayaran",
                    "net total",
                    "amount payable",
                    "total price",
                    "total harga",
                    "total:",
                ),
                listOf("bayar", "dibayar", "payable", "amt", "amount"),
                listOf("total"),
            )

        for (pass in passes) {
            val regex =
                Regex(
                    """(?:${pass.joinToString("|")})[\s:;]*$currencyPrefix\s*$decimalPrice""",
                    RegexOption.IGNORE_CASE,
                )
            for (line in lines.reversed()) {
                val lower = line.lowercase()
                if (excludeKeywords.any { lower.contains(it) }) continue
                val match = regex.find(line)
                if (match != null) {
                    val v = parseIdrToRupiah(match.groupValues[1])
                    if (v > 0) return v
                }
            }
        }

        val allPrices =
            PRICE_PATTERN
                .findAll(lines.joinToString(" "))
                .map { parseIdrToRupiah(it.groupValues[1]) }
                .filter { it > 0 && it < 1_000_000_000 }
                .toList()
        return if (allPrices.isNotEmpty()) allPrices.last() else null
    }

    private fun guessCategoryId(
        merchant: String,
        items: List<ReceiptItemDraft>,
    ): Long? {
        val merchantLower = merchant.lowercase()
        val itemText = items.joinToString(" ") { it.name.lowercase() }

        // Map ke seed AppDatabase: 1=Makanan & Minuman, 2=Bahan Baku,
        // 3=Peralatan, 4=Kebersihan, 5=Transport, 6=Lainnya.
        val categories =
            mapOf<Long, List<String>>(
                1L to
                    listOf(
                        "restaurant",
                        "warung",
                        "rumah makan",
                        "cafe",
                        "café",
                        "kopi",
                        "coffee",
                        "bakery",
                        "roti",
                        "kue",
                        "makanan",
                        "minuman",
                        "nasi",
                        "ayam",
                        "sate",
                        "soto",
                        "bakso",
                        "mie",
                        "kfc",
                        "mcd",
                        "pizza",
                        "burger",
                        "ramen",
                        "sushi",
                    ),
                5L to
                    listOf(
                        "pertamina",
                        "shell",
                        "bensin",
                        "solar",
                        "parkir",
                        "toll",
                        "tol ",
                        "grab",
                        "gojek",
                        "travel",
                        "kereta",
                        "bus ",
                        "taksi",
                        "ojek",
                        "angkot",
                        "mrt",
                        "lrt",
                        "commuter",
                    ),
                2L to
                    listOf(
                        "supermarket",
                        "hypermarket",
                        "minimarket",
                        "alfamart",
                        "indomaret",
                        "pasar",
                        "grosir",
                        "toko kelontong",
                        "bahan",
                        "beras",
                        "minyak",
                        "gula",
                        "tepung",
                        "telur",
                    ),
                3L to listOf("peralatan", "elektronik", "toko bangunan", "perkakas"),
                4L to
                    listOf("apotik", "apotek", "kimia farma", "klinik", "rumah sakit", "dokter", "obat", "sabun", "deterjen", "kebersihan"),
            )

        for ((catId, keywords) in categories) {
            if (keywords.any { merchantLower.contains(it) || itemText.contains(it) }) {
                return catId
            }
        }
        return null
    }

    private fun parseIdrToRupiah(str: String): Long {
        // IDR tidak punya sen: "25.000" -> 25000, "15.000,00" -> 15000
        val t = str.trim()
        if (t.isEmpty()) return 0
        val lastSep = t.lastIndexOf('.').coerceAtLeast(t.lastIndexOf(','))
        if (lastSep == -1) {
            return t.filter { it.isDigit() }.toLongOrNull() ?: 0
        }
        val intPart = t.substring(0, lastSep).filter { it.isDigit() }
        val fracPart = t.substring(lastSep + 1).filter { it.isDigit() }
        // Jika bagian desimal tepat 2 digit dan pemisah ',', anggap sen lalu buang.
        // Selain itu (kasus umum struk ID: titik ribuan), gabung digit.
        if (t[lastSep] == ',' && fracPart.length == 2 && intPart.isNotEmpty()) {
            return intPart.toLongOrNull() ?: 0
        }
        val allDigits = (intPart + fracPart).ifEmpty { "0" }
        // Hindari overflow aneh: batasi 12 digit (~triliun rupiah)
        return allDigits.take(12).toLongOrNull() ?: 0
    }

    fun formatCurrency(amountRupiah: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(IDR_LOCALE))
        format.maximumFractionDigits = 0
        format.minimumFractionDigits = 0
        return format.format(amountRupiah)
    }

    fun formatDate(date: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        return sdf.format(java.util.Date(date))
    }
}
