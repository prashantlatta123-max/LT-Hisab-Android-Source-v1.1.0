package com.sarotech.lthisab.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "IN" or "OUT"
    val amountPaise: Long,
    val note: String,
    val timestamp: Long
)
