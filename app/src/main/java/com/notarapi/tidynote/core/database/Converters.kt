package com.notarapi.tidynote.core.database

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toInstant(value: Instant?): Long? = value?.toEpochMilli()
}
