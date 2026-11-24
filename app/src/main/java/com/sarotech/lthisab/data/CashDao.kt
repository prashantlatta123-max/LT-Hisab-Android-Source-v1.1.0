package com.sarotech.lthisab.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<CashTransaction>>

    @Insert
    suspend fun insert(item: CashTransaction): Long

    @Update
    suspend fun update(item: CashTransaction)

    @Insert
    suspend fun insertAll(items: List<CashTransaction>)

    @Transaction
    suspend fun replaceAll(items: List<CashTransaction>) {
        deleteAll()
        insertAll(items)
    }

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
