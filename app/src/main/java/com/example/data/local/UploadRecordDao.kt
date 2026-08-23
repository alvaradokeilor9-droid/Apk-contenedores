package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRecordDao {
    @Query("SELECT * FROM upload_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<UploadRecord>>

    @Query("SELECT * FROM upload_records WHERE id = :id")
    suspend fun getRecordById(id: Long): UploadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: UploadRecord): Long

    @Update
    suspend fun updateRecord(record: UploadRecord)

    @Delete
    suspend fun deleteRecord(record: UploadRecord)

    @Query("DELETE FROM upload_records")
    suspend fun clearAll()
}
