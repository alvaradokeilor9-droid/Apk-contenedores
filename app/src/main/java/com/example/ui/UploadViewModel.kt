package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.GoogleUserInfo
import com.example.data.drive.DriveFolderInfo
import com.example.data.drive.DriveUploadItem
import com.example.data.drive.GoogleDriveService
import com.example.data.drive.UploadProgressState
import com.example.data.local.AppDatabase
import com.example.data.local.UploadRecord
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class LaunchRecoverableIntent(val intent: Intent) : UiEvent()
    data class UploadCompleted(val folderName: String, val folderUrl: String, val count: Int) : UiEvent()
}

class UploadViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = GoogleAuthManager(application)
    private val driveService = GoogleDriveService(application)
    private val database = AppDatabase.getInstance(application)
    private val dao = database.uploadRecordDao()

    private val _currentUser = MutableStateFlow<GoogleUserInfo?>(null)
    val currentUser: StateFlow<GoogleUserInfo?> = _currentUser.asStateFlow()

    private val _containerNumber = MutableStateFlow("")
    val containerNumber: StateFlow<String> = _containerNumber.asStateFlow()

    private val _clientName = MutableStateFlow("")
    val clientName: StateFlow<String> = _clientName.asStateFlow()

    private val _poNumber = MutableStateFlow("")
    val poNumber: StateFlow<String> = _poNumber.asStateFlow()

    private val _selectedPhotos = MutableStateFlow<List<DriveUploadItem>>(emptyList())
    val selectedPhotos: StateFlow<List<DriveUploadItem>> = _selectedPhotos.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<UploadProgressState?>(null)
    val uploadProgress: StateFlow<UploadProgressState?> = _uploadProgress.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    val historyRecords: StateFlow<List<UploadRecord>> = dao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkCurrentAccount()
    }

    fun checkCurrentAccount() {
        val acc = authManager.getCurrentAccount()
        _currentUser.value = acc
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            _currentUser.value = GoogleUserInfo(
                email = account.email.orEmpty(),
                displayName = account.displayName ?: account.givenName ?: account.email,
                photoUrl = account.photoUrl,
                account = account.account
            )
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Conectado como ${account.email}"))
            }
        } else {
            _currentUser.value = null
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _currentUser.value = null
            _uiEvents.emit(UiEvent.ShowToast("Cuenta desconectada"))
        }
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun onContainerNumberChange(value: String) {
        _containerNumber.value = value.uppercase()
    }

    fun onClientNameChange(value: String) {
        _clientName.value = value
    }

    fun onPoNumberChange(value: String) {
        _poNumber.value = value
    }

    fun getComputedFolderName(): String {
        val container = _containerNumber.value.trim().ifEmpty { "CONTENEDOR" }
        val client = _clientName.value.trim().ifEmpty { "CLIENTE" }
        val po = _poNumber.value.trim().ifEmpty { "PO" }
        return "$container - $client - $po"
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val existingUriSet = _selectedPhotos.value.map { it.uri }.toSet()
            val newItems = mutableListOf<DriveUploadItem>()

            var index = _selectedPhotos.value.size + 1
            for (uri in uris) {
                if (!existingUriSet.contains(uri)) {
                    val rawName = driveService.getFileName(resolver, uri)
                    val size = driveService.getFileSize(resolver, uri)
                    val formattedName = rawName ?: "FOTO_${String.format("%03d", index)}.jpg"
                    newItems.add(DriveUploadItem(uri = uri, name = formattedName, sizeBytes = size))
                    index++
                }
            }

            val updated = _selectedPhotos.value + newItems
            _selectedPhotos.value = updated
            withContext(Dispatchers.Main) {
                _uiEvents.emit(UiEvent.ShowToast("${newItems.size} fotos agregadas (Total: ${updated.size})"))
            }
        }
    }

    fun removePhoto(item: DriveUploadItem) {
        _selectedPhotos.value = _selectedPhotos.value.filter { it.uri != item.uri }
    }

    fun clearPhotos() {
        _selectedPhotos.value = emptyList()
    }

    fun populateFromHistory(record: UploadRecord) {
        _containerNumber.value = record.containerNumber
        _clientName.value = record.clientName
        _poNumber.value = record.poNumber
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Datos cargados: ${record.folderName}"))
        }
    }

    fun deleteHistoryRecord(record: UploadRecord) {
        viewModelScope.launch {
            dao.deleteRecord(record)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearAll()
            _uiEvents.emit(UiEvent.ShowToast("Historial eliminado"))
        }
    }

    fun startUpload() {
        val user = _currentUser.value
        if (user == null || user.email.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Por favor conecta una cuenta de Google primero"))
            }
            return
        }

        val container = _containerNumber.value.trim()
        val client = _clientName.value.trim()
        val po = _poNumber.value.trim()

        if (container.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Por favor ingresa el número de contenedor"))
            }
            return
        }
        if (client.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Por favor ingresa el nombre del cliente"))
            }
            return
        }
        if (po.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Por favor ingresa el PO"))
            }
            return
        }

        val photos = _selectedPhotos.value
        if (photos.isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Por favor selecciona al menos una foto para subir"))
            }
            return
        }

        val folderName = "$container - $client - $po"
        val totalBytes = photos.sumOf { it.sizeBytes }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = UploadProgressState(
                totalPhotos = photos.size,
                currentPhotoIndex = 0,
                currentPhotoName = "Conectando con Google Drive...",
                uploadedPhotosCount = 0,
                progressFraction = 0f,
                totalBytes = totalBytes
            )

            try {
                // 1. Fetch OAuth Access Token
                val tokenResult = authManager.getAccessToken(user.email)
                if (tokenResult.isFailure) {
                    val exception = tokenResult.exceptionOrNull()
                    if (exception is UserRecoverableAuthException) {
                        _uiEvents.emit(UiEvent.LaunchRecoverableIntent(exception.intent!!))
                        _isUploading.value = false
                        _uploadProgress.value = null
                        return@launch
                    }
                    throw exception ?: Exception("No se pudo obtener la autorización de Google Drive")
                }

                val token = tokenResult.getOrThrow()

                // 2. Create or find target folder in Google Drive
                _uploadProgress.value = _uploadProgress.value?.copy(
                    currentPhotoName = "Creando carpeta \"$folderName\"..."
                )
                val folderInfo = driveService.getOrCreateFolder(token, folderName)

                // 3. Upload each photo sequentially with progress
                var uploadedCount = 0
                for ((index, item) in photos.withIndex()) {
                    val currentIdx = index + 1
                    val photoDisplayName = if (item.name.isNotBlank()) item.name else "Foto_$currentIdx.jpg"

                    _uploadProgress.value = _uploadProgress.value?.copy(
                        currentPhotoIndex = currentIdx,
                        currentPhotoName = "Subiendo ($currentIdx/${photos.size}): $photoDisplayName",
                        progressFraction = (index.toFloat() / photos.size.toFloat())
                    )

                    try {
                        driveService.uploadPhoto(
                            accessToken = token,
                            folderId = folderInfo.id,
                            uri = item.uri,
                            customName = photoDisplayName
                        )
                        uploadedCount++
                        _uploadProgress.value = _uploadProgress.value?.copy(
                            uploadedPhotosCount = uploadedCount,
                            progressFraction = (uploadedCount.toFloat() / photos.size.toFloat())
                        )
                    } catch (e: Exception) {
                        Log.e("UploadViewModel", "Error uploading photo $currentIdx: ${e.message}", e)
                        // In case token expired during long batch (>100 photos), attempt to refresh token once
                        if (e.message?.contains("401") == true || e.message?.contains("Auth") == true) {
                            authManager.clearToken(token)
                            val newToken = authManager.getAccessToken(user.email).getOrNull()
                            if (newToken != null) {
                                driveService.uploadPhoto(
                                    accessToken = newToken,
                                    folderId = folderInfo.id,
                                    uri = item.uri,
                                    customName = photoDisplayName
                                )
                                uploadedCount++
                            }
                        }
                    }
                }

                // 4. Save to Room database
                val record = UploadRecord(
                    containerNumber = container,
                    clientName = client,
                    poNumber = po,
                    folderName = folderName,
                    driveFolderId = folderInfo.id,
                    driveFolderUrl = folderInfo.webViewLink,
                    accountEmail = user.email,
                    photoCount = uploadedCount,
                    status = if (uploadedCount == photos.size) "SUCCESS" else "PARTIAL",
                    errorMessage = if (uploadedCount < photos.size) "Se subieron $uploadedCount de ${photos.size} fotos" else null
                )
                dao.insertRecord(record)

                _uploadProgress.value = _uploadProgress.value?.copy(
                    isDone = true,
                    progressFraction = 1f,
                    uploadedPhotosCount = uploadedCount,
                    folderInfo = folderInfo
                )

                _uiEvents.emit(UiEvent.UploadCompleted(folderName, folderInfo.webViewLink, uploadedCount))
            } catch (e: Exception) {
                Log.e("UploadViewModel", "Upload batch failed: ${e.message}", e)
                _uploadProgress.value = _uploadProgress.value?.copy(
                    error = e.localizedMessage ?: "Error al subir a Google Drive"
                )
                _uiEvents.emit(UiEvent.ShowToast("Error: ${e.localizedMessage}"))
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun dismissProgressDialog() {
        _uploadProgress.value = null
    }
}
