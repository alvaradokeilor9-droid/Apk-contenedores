package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.components.AccountCard
import com.example.ui.components.BatchPhotoPicker
import com.example.ui.components.HistoryScreen
import com.example.ui.components.UploadProgressDialog
import com.example.ui.theme.SleekBackground
import com.example.ui.theme.SleekInfoCardBg
import com.example.ui.theme.SleekOnPrimaryContainer
import com.example.ui.theme.SleekOutline
import com.example.ui.theme.SleekPrimary
import com.example.ui.theme.SleekPrimaryContainer
import com.example.ui.theme.SleekSurfaceVariant
import com.google.android.gms.auth.api.signin.GoogleSignIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: UploadViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val containerNumber by viewModel.containerNumber.collectAsStateWithLifecycle()
    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val poNumber by viewModel.poNumber.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val historyRecords by viewModel.historyRecords.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Google Sign In Launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                viewModel.handleSignInResult(account)
            } catch (e: Exception) {
                viewModel.handleSignInResult(null)
                Toast.makeText(context, "Error al conectar cuenta: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Recoverable Auth Launcher
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startUpload()
        }
    }

    // Listen for UI events (toasts, recoverable auth)
    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.LaunchRecoverableIntent -> {
                    recoverableAuthLauncher.launch(event.intent)
                }
                is UiEvent.UploadCompleted -> {
                    // Handled inside UploadProgressDialog
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBackground),
        containerColor = SleekBackground,
        topBar = {
            // Sleek Header Bar
            Surface(
                color = SleekBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Brand with circular primary badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SleekPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DriveSync Logistics",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.3).sp
                                )
                                Text(
                                    text = "Drive Contenedores",
                                    fontSize = 11.sp,
                                    color = SleekOutline,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Right Account Pill
                        if (currentUser != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SleekPrimaryContainer,
                                modifier = Modifier.clickable {
                                    signInLauncher.launch(viewModel.getSignInIntent())
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (currentUser?.photoUrl != null) {
                                        AsyncImage(
                                            model = currentUser?.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(SleekPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "G",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentUser?.email ?: "",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SleekOnPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 130.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = SleekOutline.copy(alpha = 0.12f),
                        thickness = 1.dp
                    )
                }
            }
        },
        bottomBar = {
            // Sleek Bottom Navigation Bar
            NavigationBar(
                containerColor = SleekBackground,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = SleekOutline.copy(alpha = 0.12f)
                )
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    modifier = Modifier.testTag("tab_upload"),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Nueva Carga"
                        )
                    },
                    label = {
                        Text(
                            text = "Carga",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekPrimary,
                        selectedTextColor = SleekPrimary,
                        indicatorColor = SleekPrimaryContainer,
                        unselectedIconColor = SleekOutline,
                        unselectedTextColor = SleekOutline
                    )
                )

                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    modifier = Modifier.testTag("tab_history"),
                    icon = {
                        BadgedBox(
                            badge = {
                                if (historyRecords.isNotEmpty()) {
                                    Badge(containerColor = SleekPrimary) {
                                        Text("${historyRecords.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historial"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "Historial",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekPrimary,
                        selectedTextColor = SleekPrimary,
                        indicatorColor = SleekPrimaryContainer,
                        unselectedIconColor = SleekOutline,
                        unselectedTextColor = SleekOutline
                    )
                )
            }
        }
    ) { innerPadding ->
        if (selectedTabIndex == 0) {
            // Upload Form Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Google Account Selector & Status
                AccountCard(
                    currentUser = currentUser,
                    onSignInClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                    onSwitchAccountClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                    onSignOutClick = { viewModel.signOut() }
                )

                // Sleek Container Form Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("container_form_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(SleekOutline.copy(alpha = 0.15f))
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SleekPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = SleekPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Datos de Envío & Contenedor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1. Número de Contenedor (Mono formatted, Sleek active border)
                        OutlinedTextField(
                            value = containerNumber,
                            onValueChange = { viewModel.onContainerNumberChange(it) },
                            label = { Text("Número de Contenedor", fontWeight = FontWeight.Medium) },
                            placeholder = { Text("Ej. MSKU-992834-0 / CMAU-123456") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = if (containerNumber.isNotBlank()) SleekPrimary else SleekOutline
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_container_number"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                focusedLabelColor = SleekPrimary,
                                unfocusedBorderColor = SleekOutline.copy(alpha = 0.35f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = SleekSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Nombre del Cliente
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { viewModel.onClientNameChange(it) },
                            label = { Text("Nombre del Cliente", fontWeight = FontWeight.Medium) },
                            placeholder = { Text("Ej. Global Freight Solutions Ltd.") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (clientName.isNotBlank()) SleekPrimary else SleekOutline
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_client_name"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                focusedLabelColor = SleekPrimary,
                                unfocusedBorderColor = SleekOutline.copy(alpha = 0.35f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = SleekSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Purchase Order (PO)
                        OutlinedTextField(
                            value = poNumber,
                            onValueChange = { viewModel.onPoNumberChange(it) },
                            label = { Text("Purchase Order (PO)", fontWeight = FontWeight.Medium) },
                            placeholder = { Text("Ej. PO-2026-8892-XT") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (poNumber.isNotBlank()) SleekPrimary else SleekOutline
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_po_number"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                focusedLabelColor = SleekPrimary,
                                unfocusedBorderColor = SleekOutline.copy(alpha = 0.35f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = SleekSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }
                }

                // Batch Photo Picker (>100 photos support with Sleek 28dp card)
                BatchPhotoPicker(
                    photos = selectedPhotos,
                    onPhotosSelected = { viewModel.addPhotos(it) },
                    onRemovePhoto = { viewModel.removePhoto(it) },
                    onClearAll = { viewModel.clearPhotos() }
                )

                // Sleek Destination Info Pill Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SleekInfoCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekOutline.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SleekOutline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Las imágenes se guardarán en Google Drive:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SleekOutline,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Google Drive > ${viewModel.getComputedFolderName()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = SleekOnPrimaryContainer
                            )
                        }
                    }
                }

                // Main SYNC TO DRIVE Hero Pill Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.startUpload()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50), ambientColor = SleekPrimary.copy(alpha = 0.3f), spotColor = SleekPrimary)
                        .testTag("upload_to_drive_button"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SleekPrimary,
                        contentColor = Color.White
                    ),
                    enabled = !isUploading && selectedPhotos.isNotEmpty() && containerNumber.isNotBlank() && clientName.isNotBlank() && poNumber.isNotBlank() && currentUser != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedPhotos.isNotEmpty()) "SYNC TO DRIVE (${selectedPhotos.size} FOTOS)" else "SELECCIONA FOTOS PARA SUBIR",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // History Tab
            HistoryScreen(
                records = historyRecords,
                onSelectRecord = {
                    viewModel.populateFromHistory(it)
                    selectedTabIndex = 0
                },
                onDeleteRecord = { viewModel.deleteHistoryRecord(it) },
                onClearAll = { viewModel.clearAllHistory() },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    // Upload in progress / finished dialog
    UploadProgressDialog(
        progressState = uploadProgress,
        onDismiss = { viewModel.dismissProgressDialog() }
    )
}

