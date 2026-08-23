package com.example.data.drive

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class DriveFolderInfo(
    val id: String,
    val name: String,
    val webViewLink: String
)

data class DriveUploadItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long
)

data class UploadProgressState(
    val totalPhotos: Int,
    val currentPhotoIndex: Int,
    val currentPhotoName: String,
    val uploadedPhotosCount: Int,
    val progressFraction: Float, // 0.0 to 1.0
    val currentBytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val isDone: Boolean = false,
    val folderInfo: DriveFolderInfo? = null,
    val error: String? = null
)

class GoogleDriveService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GoogleDriveService"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }

    /**
     * Finds an existing folder by name or creates a new one.
     */
    suspend fun getOrCreateFolder(accessToken: String, folderName: String): DriveFolderInfo = withContext(Dispatchers.IO) {
        val sanitizedName = folderName.replace("'", "\\'")
        val searchUrl = "$DRIVE_API_BASE/files?q=name='${sanitizedName}' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id,name,webViewLink)&spaces=drive"

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        var response: Response? = null
        try {
            response = client.newCall(searchRequest).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val firstFolder = files.getJSONObject(0)
                    val id = firstFolder.getString("id")
                    val name = firstFolder.optString("name", folderName)
                    val link = firstFolder.optString("webViewLink", "https://drive.google.com/drive/folders/$id")
                    Log.d(TAG, "Found existing folder: $name (id: $id)")
                    return@withContext DriveFolderInfo(id = id, name = name, webViewLink = link)
                }
            } else {
                Log.w(TAG, "Search folder failed code: ${response.code}, creating new...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching folder: ${e.message}", e)
        } finally {
            response?.close()
        }

        // Folder not found, create new one
        val createPayload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }

        val createRequestBody = createPayload.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
        val createRequest = Request.Builder()
            .url("$DRIVE_API_BASE/files?fields=id,name,webViewLink")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(createRequestBody)
            .build()

        val createResponse = client.newCall(createRequest).execute()
        if (!createResponse.isSuccessful) {
            val errBody = createResponse.body?.string() ?: ""
            createResponse.close()
            throw IllegalStateException("Error al crear carpeta en Google Drive (${createResponse.code}): $errBody")
        }

        val createdJson = JSONObject(createResponse.body?.string() ?: "{}")
        createResponse.close()
        val id = createdJson.getString("id")
        val name = createdJson.optString("name", folderName)
        val link = createdJson.optString("webViewLink", "https://drive.google.com/drive/folders/$id")
        Log.d(TAG, "Created folder: $name (id: $id)")
        return@withContext DriveFolderInfo(id = id, name = name, webViewLink = link)
    }

    /**
     * Uploads a single file using multipart upload into the specified parent folder.
     */
    suspend fun uploadPhoto(
        accessToken: String,
        folderId: String,
        uri: Uri,
        customName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = customName ?: getFileName(resolver, uri) ?: "IMG_${System.currentTimeMillis()}.jpg"
        val mimeType = resolver.getType(uri) ?: "image/jpeg"

        // Read file bytes
        val bytes = readBytesFromUri(resolver, uri)
            ?: throw IllegalStateException("No se pudo leer el archivo: $fileName")

        val boundary = "====DriveMultipartBoundary_${System.currentTimeMillis()}===="

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }.toString()

        val mediaType = mimeType.toMediaType()
        val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

        val multipartBody = MultipartBody.Builder(boundary)
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody(jsonMediaType))
            .addPart(bytes.toRequestBody(mediaType))
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=id,name,webViewLink")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            throw IllegalStateException("Error al subir $fileName (${response.code}): $errorBody")
        }

        val respBody = response.body?.string() ?: "{}"
        response.close()
        val resultJson = JSONObject(respBody)
        val fileId = resultJson.getString("id")
        Log.d(TAG, "Successfully uploaded $fileName -> ID: $fileId")
        return@withContext fileId
    }

    private fun readBytesFromUri(resolver: ContentResolver, uri: Uri): ByteArray? {
        return try {
            resolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading URI bytes: ${e.message}", e)
            null
        }
    }

    fun getFileName(resolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = resolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return name
    }

    fun getFileSize(resolver: ContentResolver, uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            val cursor = resolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1) {
                        size = it.getLong(index)
                    }
                }
            }
        }
        return size
    }
}
