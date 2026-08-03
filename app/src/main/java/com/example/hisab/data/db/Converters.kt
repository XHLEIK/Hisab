package com.example.hisab.data.db

import androidx.room.TypeConverter
import com.example.hisab.data.model.TransactionType
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(name: String): TransactionType = TransactionType.valueOf(name)
}
