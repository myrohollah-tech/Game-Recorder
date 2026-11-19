package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CaptureTelemetry
import com.example.data.StreamConfig
import com.example.data.StreamNote
import com.example.data.StreamRepository
import com.example.service.ScreenCaptureService
import com.example.ui.AuthScreen
import com.example.ui.FrontCameraOverlayBox
import com.example.ui.StreamViewModel
import com.example.ui.UiState
import com.example.ui.signOut
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val user = currentUser!!
        key(user.uid) {
            val streamViewModel: StreamViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        StreamViewModel(StreamRepository(db), user.uid)
                    }
                }
            )
            StreamerStudioScreen(
                userEmail = user.email ?: user.displayName ?: "YouTube Creator",
                viewModel = streamViewModel
            )
        }
    }
}

@Composable
fun StreamerStudioScreen(
    userEmail: String,
    viewModel: StreamViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val configsState by viewModel.configsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val selectedConfigId by viewModel.selectedConfigId.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        grants[Manifest.permission.CAMERA]?.let { hasCameraPerm = it }
        grants[Manifest.permission.RECORD_AUDIO]?.let { hasAudioPerm = it }
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasCameraPerm) needed.add(Manifest.permission.CAMERA)
        if (!hasAudioPerm) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START_CAPTURE
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
        }
        startCaptureServiceCompat(context, serviceIntent)
    }

    var showStreamDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showStreamDialog || showNoteDialog) {
        showStreamDialog = false
        showNoteDialog = false
    }

    val configs = (configsState as? UiState.Success)?.data.orEmpty()
    val selectedConfig = configs.find { it.id == selectedConfigId } ?: configs.firstOrNull()
    val notes = (notesState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTabletOrLandscape = maxWidth >= 720.dp

            if (isTabletOrLandscape) {
                // Canonical Tablet / Landscape 2-Pane Broadcast Control Deck
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudioTopHeader(
                            userEmail = userEmail,
                            telemetry = telemetry,
                            onSignOut = {
                                signOut(credentialManager, scope)
                            }
                        )
                        StatusIndicatorsBar(telemetry = telemetry)
                        LiveStageMonitorCard(
                            telemetry = telemetry,
                            selectedConfig = selectedConfig,
                            hasCameraPermission = hasCameraPerm,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            PrimaryBroadcastControlsCard(
                                telemetry = telemetry,
                                selectedConfig = selectedConfig,
                                onToggleScreenCapture = {
                                    triggerScreenCaptureToggle(
                                        context = context,
                                        isCapturing = telemetry.isScreenCapturing,
                                        onLaunchMediaProjection = { intent ->
                                            mediaProjectionLauncher.launch(intent)
                                        }
                                    )
                                },
                                onToggleYouTubeLive = {
                                    triggerYouTubeLiveToggle(
                                        context = context,
                                        isLive = telemetry.isYouTubeLive,
                                        selectedConfig = selectedConfig,
                                        onOpenStreamSelector = { showStreamDialog = true }
                                    )
                                },
                                onOpenStreamSelector = { showStreamDialog = true }
                            )
                        }
                        item {
                            BigTouchTogglesGrid(
                                telemetry = telemetry,
                                onToggleCamera = {
                                    if (!hasCameraPerm) {
                                        permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    }
                                    viewModel.toggleCamera()
                                },
                                onToggleMic = {
                                    if (!hasAudioPerm) {
                                        permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                    viewModel.toggleMicrophone()
                                },
                                onToggleGameAudio = {
                                    viewModel.toggleGameAudio()
                                }
                            )
                        }
                        item {
                            StreamerQuickNotesCard(
                                notes = notes,
                                onAddNoteClick = { showNoteDialog = true },
                                onDeleteNote = { viewModel.deleteQuickNote(it) }
                            )
                        }
                    }
                }
            } else {
                // Handheld / Compact Single-Column Broadcast Deck
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        StudioTopHeader(
                            userEmail = userEmail,
                            telemetry = telemetry,
                            onSignOut = {
                                signOut(credentialManager, scope)
                            }
                        )
                    }
                    item {
                        StatusIndicatorsBar(telemetry = telemetry)
                    }
                    item {
                        LiveStageMonitorCard(
                            telemetry = telemetry,
                            selectedConfig = selectedConfig,
                            hasCameraPermission = hasCameraPerm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(310.dp)
                        )
                    }
                    item {
                        PrimaryBroadcastControlsCard(
                            telemetry = telemetry,
                            selectedConfig = selectedConfig,
                            onToggleScreenCapture = {
                                triggerScreenCaptureToggle(
                                    context = context,
                                    isCapturing = telemetry.isScreenCapturing,
                                    onLaunchMediaProjection = { intent ->
                                        mediaProjectionLauncher.launch(intent)
                                    }
                                )
                            },
                            onToggleYouTubeLive = {
                                triggerYouTubeLiveToggle(
                                    context = context,
                                    isLive = telemetry.isYouTubeLive,
                                    selectedConfig = selectedConfig,
                                    onOpenStreamSelector = { showStreamDialog = true }
                                )
                            },
                            onOpenStreamSelector = { showStreamDialog = true }
                        )
                    }
                    item {
                        BigTouchTogglesGrid(
                            telemetry = telemetry,
                            onToggleCamera = {
                                if (!hasCameraPerm) {
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }
                                viewModel.toggleCamera()
                            },
                            onToggleMic = {
                                if (!hasAudioPerm) {
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                                viewModel.toggleMicrophone()
                            },
                            onToggleGameAudio = {
                                viewModel.toggleGameAudio()
                            }
                        )
                    }
                    item {
                        StreamerQuickNotesCard(
                            notes = notes,
                            onAddNoteClick = { showNoteDialog = true },
                            onDeleteNote = { viewModel.deleteQuickNote(it) }
                        )
                    }
                }
            }
        }
    }

    if (showStreamDialog) {
        YouTubeStreamSelectorDialog(
            configs = configs,
            selectedConfigId = selectedConfig?.id,
            onSelectConfig = {
                viewModel.selectStreamConfig(it)
                showStreamDialog = false
            },
            onCreateConfig = { title, desc, rtmpUrl, key, privacy ->
                viewModel.createYouTubeStreamProfile(title, desc, rtmpUrl, key, privacy)
                showStreamDialog = false
            },
            onDeleteConfig = { viewModel.deleteStreamProfile(it) },
            onDismiss = { showStreamDialog = false }
        )
    }

    if (showNoteDialog) {
        AddNoteDialog(
            onSave = { title, content ->
                viewModel.addQuickNote(title, content)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }
}

private fun startCaptureServiceCompat(context: Context, intent: Intent) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (_: Exception) {
        context.startService(intent)
    }
}

private fun triggerScreenCaptureToggle(
    context: Context,
    isCapturing: Boolean,
    onLaunchMediaProjection: (Intent) -> Unit
) {
    if (isCapturing) {
        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAPTURE
        }
        context.startService(stopIntent)
    } else {
        try {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            val captureIntent = mpManager?.createScreenCaptureIntent()
            if (captureIntent != null && context is Activity) {
                onLaunchMediaProjection(captureIntent)
            } else {
                val fallbackIntent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START_CAPTURE
                }
                startCaptureServiceCompat(context, fallbackIntent)
            }
        } catch (_: Exception) {
            val fallbackIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
            }
            startCaptureServiceCompat(context, fallbackIntent)
        }
    }
}

private fun triggerYouTubeLiveToggle(
    context: Context,
    isLive: Boolean,
    selectedConfig: StreamConfig?,
    onOpenStreamSelector: () -> Unit
) {
    if (isLive) {
        val stopLiveIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_YOUTUBE_LIVE
        }
        context.startService(stopLiveIntent)
    } else {
        if (selectedConfig == null) {
            onOpenStreamSelector()
        } else {
            val startLiveIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_YOUTUBE_LIVE
                putExtra(ScreenCaptureService.EXTRA_RTMP_URL, selectedConfig.rtmpUrl)
                putExtra(ScreenCaptureService.EXTRA_STREAM_KEY, selectedConfig.streamKey)
            }
            startCaptureServiceCompat(context, startLiveIntent)
        }
    }
}

@Composable
fun StudioTopHeader(
    userEmail: String,
    telemetry: CaptureTelemetry,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (telemetry.isYouTubeLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = "StreamCast Live",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "StreamCast Live",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "YouTube Account: $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = formatDuration(telemetry.elapsedSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (telemetry.isScreenCapturing || telemetry.isYouTubeLive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(id = R.string.sign_out),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusIndicatorsBar(telemetry: CaptureTelemetry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_indicators_bar"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    label = "Screen",
                    stateText = if (telemetry.isScreenCapturing) "REC 720p" else "STOPPED",
                    isActive = telemetry.isScreenCapturing,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = "Camera",
                    stateText = if (telemetry.isCameraOn) "ON 300×250" else "OFF",
                    isActive = telemetry.isCameraOn,
                    activeColor = EmeraldActive,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = "Mic",
                    stateText = if (telemetry.isMicOn) "ON" else "OFF",
                    isActive = telemetry.isMicOn,
                    activeColor = EmeraldActive,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = "Game Audio",
                    stateText = if (telemetry.isGameAudioOn) "ON" else "OFF",
                    isActive = telemetry.isGameAudioOn,
                    activeColor = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = telemetry.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    stateText: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LiveStageMonitorCard(
    telemetry: CaptureTelemetry,
    selectedConfig: StreamConfig?,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier
) {
    // Requirement: Camera is ONLY shown on the stream when Camera is ON AND user is active in stream/capture
    val isUserActiveInStream = telemetry.isScreenCapturing || telemetry.isYouTubeLive
    val showCameraOnStream = telemetry.isCameraOn && isUserActiveInStream

    Card(
        modifier = modifier.testTag("live_stage_monitor"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_stream_banner_1794062372323),
                contentDescription = "Game Stream Stage",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x990B0F19),
                                Color(0xCC0B0F19)
                            )
                        )
                    )
            )

            // Top telemetry bar inside the monitor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (telemetry.isYouTubeLive) MaterialTheme.colorScheme.primary else Color(0xCC1E293B),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (telemetry.isYouTubeLive) "YOUTUBE LIVE" else "OFFLINE PREVIEW",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        color = Color(0xCC1E293B),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "720p • ${if (isUserActiveInStream) telemetry.currentFps else 30} FPS • 6 Mbps",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Center stage info
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = selectedConfig?.title ?: "استریم بازی YouTube (آماده شروع)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = if (isUserActiveInStream) {
                        "در حال ارسال همزمان تصویر بازی (1280×720) + صدا به YouTube"
                    } else {
                        "برای نمایش تصویر و دوربین روی استریم، دکمه Play ضبط صفحه یا GO LIVE را لمس کنید"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )
            }

            // Simultaneous 300x250 Front Camera overlay — shown ONLY when Camera is ON and user is active in stream/capture
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showCameraOnStream,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FrontCameraOverlayBox(hasCameraPermission = hasCameraPermission)
                }
            }

            // Bottom audio meters bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioMeterChip(
                    label = "MIC",
                    isEnabled = telemetry.isMicOn,
                    level = telemetry.micLevelDb
                )
                AudioMeterChip(
                    label = "GAME",
                    isEnabled = telemetry.isGameAudioOn,
                    level = telemetry.gameAudioLevelDb
                )
            }
        }
    }
}

@Composable
private fun AudioMeterChip(
    label: String,
    isEnabled: Boolean,
    level: Float
) {
    Surface(
        color = Color(0xCC0F172A),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isEnabled) EmeraldActive else Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { if (isEnabled) level.coerceIn(0.08f, 1f) else 0f },
                modifier = Modifier
                    .width(56.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isEnabled) EmeraldActive else Color.DarkGray,
                trackColor = Color(0xFF334155)
            )
        }
    }
}

@Composable
fun PrimaryBroadcastControlsCard(
    telemetry: CaptureTelemetry,
    selectedConfig: StreamConfig?,
    onToggleScreenCapture: () -> Unit,
    onToggleYouTubeLive: () -> Unit,
    onOpenStreamSelector: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.youtube_live_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedConfig?.let { "${it.title} (${it.privacyStatus.uppercase(Locale.US)})" }
                            ?: "هیچ استریمی انتخاب نشده است • برای تنظیم ضربه بزنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenStreamSelector,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("select_youtube_stream_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RadioButtonChecked,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "انتخاب / ایجاد استریم")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Big Touch Screen Capture Play / Stop Button
                Button(
                    onClick = onToggleScreenCapture,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("screen_capture_toggle_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.isScreenCapturing) {
                            Color(0xFFDC2626)
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        contentColor = if (telemetry.isScreenCapturing) Color.White else Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (telemetry.isScreenCapturing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (telemetry.isScreenCapturing) "STOP CAPTURE" else "PLAY CAPTURE",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "720p • 30fps • 6Mbps",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Big Touch YouTube Live Start / Stop Button
                Button(
                    onClick = onToggleYouTubeLive,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("youtube_live_toggle_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.isYouTubeLive) {
                            Color(0xFFB91C1C)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (telemetry.isYouTubeLive) Icons.Filled.Stop else Icons.Filled.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (telemetry.isYouTubeLive) "END YOUTUBE LIVE" else "GO LIVE YOUTUBE",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = if (telemetry.isYouTubeLive) "در حال ارسال استریم" else "شروع پخش زنده",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BigTouchTogglesGrid(
    telemetry: CaptureTelemetry,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleGameAudio: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Camera ON/OFF Button
        HardwareToggleCard(
            title = "Camera",
            subtitle = "300×250 px",
            isEnabled = telemetry.isCameraOn,
            iconOn = Icons.Filled.Videocam,
            iconOff = Icons.Filled.VideocamOff,
            activeColor = EmeraldActive,
            onClick = onToggleCamera,
            testTag = "camera_toggle_button",
            modifier = Modifier.weight(1f)
        )

        // 2. Independent Microphone ON/OFF Button
        HardwareToggleCard(
            title = "Microphone",
            subtitle = if (telemetry.isMicOn) "صدای میکروفون فعال" else "میکروفون بی‌صدا",
            isEnabled = telemetry.isMicOn,
            iconOn = Icons.Filled.Mic,
            iconOff = Icons.Filled.MicOff,
            activeColor = EmeraldActive,
            onClick = onToggleMic,
            testTag = "mic_toggle_button",
            modifier = Modifier.weight(1f)
        )

        // 3. Independent Game Audio ON/OFF Button
        HardwareToggleCard(
            title = "Game Audio",
            subtitle = if (telemetry.isGameAudioOn) "صدای بازی فعال" else "صدای بازی قطع",
            isEnabled = telemetry.isGameAudioOn,
            iconOn = Icons.AutoMirrored.Filled.VolumeUp,
            iconOff = Icons.AutoMirrored.Filled.VolumeOff,
            activeColor = CyanAccent,
            onClick = onToggleGameAudio,
            testTag = "game_audio_toggle_button",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HardwareToggleCard(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    iconOn: ImageVector,
    iconOff: ImageVector,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isEnabled) 2.dp else 1.dp,
                color = if (isEnabled) activeColor else Color(0xFF334155),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                activeColor.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEnabled) iconOn else iconOff,
                    contentDescription = "$title ${if (isEnabled) "ON" else "OFF"}",
                    tint = if (isEnabled) activeColor else Color(0xFFEF4444),
                    modifier = Modifier.size(30.dp)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isEnabled) activeColor else Color(0xFF334155)
                ) {
                    Text(
                        text = if (isEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isEnabled) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StreamerQuickNotesCard(
    notes: List<StreamNote>,
    onAddNoteClick: () -> Unit,
    onDeleteNote: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.streamer_notes_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onAddNoteClick,
                    modifier = Modifier.testTag("add_streamer_note_button")
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(id = R.string.add_note))
                }
            }

            if (notes.isEmpty()) {
                Text(
                    text = "یادداشت یا چک‌لیست استریم خود را اینجا ثبت کنید تا هنگام بازی جلوی چشمتان باشد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notes.take(4).forEach { note ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteNote(note.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "حذف یادداشت",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeStreamSelectorDialog(
    configs: List<StreamConfig>,
    selectedConfigId: String?,
    onSelectConfig: (StreamConfig) -> Unit,
    onCreateConfig: (String, String, String, String, String) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("استریم زنده بازی • 720p 30fps") }
    var description by remember { mutableStateOf("پخش زنده مستقیم از تبلت اندروید") }
    var rtmpUrl by remember { mutableStateOf("rtmp://a.rtmp.youtube.com/live2") }
    var streamKey by remember { mutableStateOf("") }
    var privacyStatus by remember { mutableStateOf("public") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "انتخاب یا ایجاد YouTube Live Stream",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (configs.isNotEmpty()) {
                    Text(
                        text = "استریم‌های ذخیره‌شده در حساب شما:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(configs, key = { it.id }) { cfg ->
                            val isSelected = cfg.id == selectedConfigId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectConfig(cfg) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cfg.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${cfg.resolution} • ${cfg.fps}fps • ${cfg.privacyStatus}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteConfig(cfg.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete stream config"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "ایجاد استریم جدید YouTube Live:",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان استریم (Broadcast Title)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_title_input")
                )
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key یوتیوب (اختیاری یا کلید RTMP)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_key_input")
                )
                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("آدرس RTMP یوتیوب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("public", "unlisted", "private").forEach { mode ->
                        FilterChip(
                            selected = privacyStatus == mode,
                            onClick = { privacyStatus = mode },
                            label = { Text(mode.uppercase(Locale.US)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateConfig(title, description, rtmpUrl, streamKey, privacyStatus)
                },
                modifier = Modifier.testTag("confirm_create_stream_button")
            ) {
                Text("ایجاد و انتخاب استریم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@Composable
fun AddNoteDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یادداشت جدید استریم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان یادداشت") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("متن یادداشت") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content) },
                modifier = Modifier.testTag("save_note_button")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}
