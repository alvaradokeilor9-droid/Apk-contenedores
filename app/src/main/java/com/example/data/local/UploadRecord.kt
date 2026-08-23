package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_records")
data class UploadRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val containerNumber: String,
    val clientName: String,
    val poNumber: String,
    val folderName: String,
    val driveFolderId: String? = null,
    val driveFolderUrl: String? = null,
    val accountEmail: String? = null,
    val photoCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS", // SUCCESS, FAILED, PENDING
    val errorMessage: String? = null
)
