package com.notarapi.tidynote.core.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.Instant

/**
 * Instance [Json] bersama untuk seluruh aplikasi.
 *
 * Mendaftarkan serializer contextual untuk [java.time.Instant] agar field bertanda
 * [kotlinx.serialization.Contextual] (mis. `ReceiptDraft.transactionDate`) bisa
 * di-encode/decode tanpa crash "Serializer for class 'Instant' is not found".
 */
val AppJson: Json =
    Json {
        ignoreUnknownKeys = true
        serializersModule =
            SerializersModule {
                contextual(Instant::class, InstantSerializer)
            }
    }
