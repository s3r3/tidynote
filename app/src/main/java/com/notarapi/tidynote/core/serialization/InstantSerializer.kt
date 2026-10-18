package com.notarapi.tidynote.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Serializer kotlinx untuk [java.time.Instant] (serialisasi draft antar layar).
 *
 * Menyimpan sebagai string ISO-8601 (mis. "2024-01-15T10:30:00Z"). Saat membaca,
 * toleran terhadap beberapa bentuk agar data lama tetap terbaca:
 *  - ISO-8601 instant ("2024-01-15T10:30:00Z")
 *  - angka epoch millis ("1705314600000")
 *  - tanggal-jam lokal tanpa zona ("2024-01-15T10:30:00") → dikonversi pakai zona sistem.
 */
object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString().trim()
        if (raw.isEmpty()) return Instant.now()

        // 1) ISO-8601 instant (bentuk normal).
        runCatching { return Instant.parse(raw) }

        // 2) Epoch millis (angka).
        raw.toLongOrNull()?.let { millis -> return Instant.ofEpochMilli(millis) }

        // 3) Tanggal-jam lokal tanpa zona → pakai zona default perangkat.
        runCatching {
            val ldt = LocalDateTime.parse(raw)
            return ldt.atZone(ZoneId.systemDefault()).toInstant()
        }

        // Gagal semua → jangan bikin crash; balik ke sekarang.
        return Instant.now()
    }
}
