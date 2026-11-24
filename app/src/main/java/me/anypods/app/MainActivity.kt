package me.anypods.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode
import me.anypods.app.audio.AncIntensity
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.audio.FrequencyTuningMode
import me.anypods.app.audio.SpatialAudioMode
import me.anypods.app.audio.ToneIntensity
import me.anypods.app.audio.ToneProfile
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.bluetooth.ProximityTracker
import me.anypods.app.model.EarbudState
import me.anypods.app.model.PodImageManager
import me.anypods.app.model.PodModelManager
import me.anypods.app.model.PodModelInfo
import me.anypods.app.ui.DynamicIslandOverlay
import me.anypods.app.ui.OverlayPopup
import me.anypods.app.ui.PrecisionFindMyCard
import me.anypods.app.ui.M3LoadingIndicator
import me.anypods.app.ui.M3LinearProgressIndicator
import me.anypods.app.ui.M3CircularProgressIndicator
import me.anypods.app.ui.AudioBalanceCard
import me.anypods.app.ui.SpatialAudioCard
import me.anypods.app.ui.HeadphoneAccommodationsCard
import me.anypods.app.ui.AirPodsFrequencyTuningCard
import me.anypods.app.ui.DiagnosticSettingsCard
import me.anypods.app.ui.VoiceMicCard
import me.anypods.app.ui.SignalBoostCard
import me.anypods.app.ui.DiagnosticConsentDialog
import me.anypods.app.ui.DiagnosticReportDialog
import me.anypods.app.ui.SoftwareUpdateDialog
import me.anypods.app.ui.WebImageSearchDialog
import me.anypods.app.ui.BalancePermissionDialog
import me.anypods.app.ui.theme.AnyPodsTheme
import me.anypods.app.diagnostics.DiagnosticManager
import me.anypods.app.updater.UpdateInfo
import me.anypods.app.updater.UpdateManager
import me.anypods.app.audio.EarTipFitTester
import me.anypods.app.audio.HearingHealthManager
import me.anypods.app.audio.FitTestResult
import me.anypods.app.service.EarbudService
import me.anypods.app.widget.AnyPodsWidgetProvider

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        EarbudService.startServiceSafely(this)
        BluetoothTracker.refreshConnectedDevice()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAudioSessionIntent(intent)
        AudioEnhancer.scanActiveSessions(this)
    }

    private fun handleAudioSessionIntent(intent: Intent?) {
        val sessionId = intent?.getIntExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: 0
        if (sessionId > 0) {
            AudioEnhancer.registerSession(sessionId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioEnhancer.initAudioEffects(this)
        AudioEnhancer.initBalance(this)
        UpdateManager.init(this)
        UpdateManager.checkForUpdates(this, isManual = false)
        handleAudioSessionIntent(intent)
        checkAndRequestPermissions()

        setContent {
            AnyPodsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val earbudState by BluetoothTracker.earbudState.collectAsState()
            val noiseMode by NoiseControlManager.currentMode.collectAsState()
            val convAwareness by NoiseControlManager.conversationAwarenessEnabled.collectAsState()
            val personalizedVol by NoiseControlManager.personalizedVolumeEnabled.collectAsState()
            val loudReduction by NoiseControlManager.loudSoundReductionEnabled.collectAsState()
            val ambientDb by NoiseControlManager.ambientDecibels.collectAsState()

            val spatialMode by AudioEnhancer.currentSpatialMode.collectAsState()
            val toneProfile by AudioEnhancer.currentToneProfile.collectAsState()
            val toneIntensity by AudioEnhancer.currentToneIntensity.collectAsState()
            val tuningMode by AudioEnhancer.currentTuningMode.collectAsState()
            val lrBalance by AudioEnhancer.currentBalance.collectAsState()
            val ancIntensity by AudioEnhancer.currentAncIntensity.collectAsState()
            val antiHiss by AudioEnhancer.antiHissWindReduction.collectAsState()

            val updateInfo by UpdateManager.updateAvailable.collectAsState()
            val isCheckingUpdate by UpdateManager.isChecking.collectAsState()
            val downloadProgress by UpdateManager.downloadProgress.collectAsState()
            val autoUpdateEnabled by UpdateManager.autoUpdateEnabled.collectAsState()

            val fitTesting by EarTipFitTester.isTesting.collectAsState()
            val fitProgress by EarTipFitTester.testProgress.collectAsState()
            val fitResult by EarTipFitTester.lastResult.collectAsState()

            val volumeLimiter by HearingHealthManager.isLimiterEnabled.collectAsState()
            val estimatedDb by HearingHealthManager.estimatedDecibels.collectAsState()
            var isDynamicIslandActive by remember { mutableStateOf(DynamicIslandOverlay.isEnabled()) }

            AnyPodsScreen(
                state = earbudState,
                currentNoiseMode = noiseMode,
                ancIntensity = ancIntensity,
                antiHiss = antiHiss,
                conversationAwareness = convAwareness,
                personalizedVolume = personalizedVol,
                loudSoundReduction = loudReduction,
                ambientDecibels = ambientDb,
                spatialAudioMode = spatialMode,
                toneProfile = toneProfile,
                toneIntensity = toneIntensity,
                tuningMode = tuningMode,
                onSetTuningMode = {
                    AudioEnhancer.setFrequencyTuningMode(it)
                },
                lrBalance = lrBalance,
                updateInfo = updateInfo,
                isCheckingUpdate = isCheckingUpdate,
                downloadProgress = downloadProgress,
                fitTesting = fitTesting,
                fitProgress = fitProgress,
                fitResult = fitResult,
                onStartFitTest = {
                    EarTipFitTester.startFitTest(this) {}
                },
                onStopFitTest = {
                    EarTipFitTester.stopTest()
                },
                volumeLimiterEnabled = volumeLimiter,
                estimatedDb = estimatedDb,
                onToggleVolumeLimiter = {
                    HearingHealthManager.toggleLimiter(this, it)
                },
                dynamicIslandEnabled = isDynamicIslandActive,
                onToggleDynamicIsland = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_SHORT).show()
                        } else {
                            DynamicIslandOverlay.show(this)
                            isDynamicIslandActive = true
                        }
                    } else {
                        DynamicIslandOverlay.hide()
                        isDynamicIslandActive = false
                    }
                },
                onCheckForUpdates = { UpdateManager.checkForUpdates(this, isManual = true) },
                autoUpdateEnabled = autoUpdateEnabled,
                onToggleAutoUpdate = { UpdateManager.setAutoUpdateEnabled(this, it) },
                onConfirmUpdate = { info -> UpdateManager.downloadAndInstall(this, info) },
                onDismissUpdate = { UpdateManager.dismissUpdate() },
                onRefresh = { BluetoothTracker.refreshConnectedDevice() },
                onShowPopup = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
                    } else {
                        OverlayPopup.show(this, earbudState)
                    }
                },
                onSelectNoiseMode = { mode ->
                    NoiseControlManager.setMode(this, mode)
                },
                onToggleConversationAwareness = {
                    NoiseControlManager.toggleConversationAwareness(this, it)
                },
                onTogglePersonalizedVolume = {
                    NoiseControlManager.togglePersonalizedVolume(this, it)
                },
                onToggleLoudReduction = {
                    NoiseControlManager.loudSoundReductionEnabled.value = it
                },
                onSetSpatialAudio = {
                    AudioEnhancer.setSpatialAudio(it)
                },
                onSetHeadphoneAccommodations = { prof, intens ->
                    AudioEnhancer.setHeadphoneAccommodations(prof, intens)
                },
                onSetBalance = {
                    AudioEnhancer.setAudioBalance(this, it)
                },
                onFindMy = { left, right ->
                    AudioEnhancer.startFindMyTone(this, left, right)
                },
                onStopFindMy = {
                    AudioEnhancer.stopFindMyTone()
                },
                onSetLeftPod = { level, isCharging ->
                    BluetoothTracker.setLeftPod(level, isCharging)
                },
                onSetRightPod = { level, isCharging ->
                    BluetoothTracker.setRightPod(level, isCharging)
                },
                onSetCase = { level, isCharging ->
                    BluetoothTracker.setCase(level, isCharging)
                }
            )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        EarbudService.startServiceSafely(this)
        AudioEnhancer.scanActiveSessions(this)
        BluetoothTracker.refreshConnectedDevice()
        HearingHealthManager.updateEstimatedDb(this)
        AnyPodsWidgetProvider.updateAllWidgets(this)
    }

    override fun onDestroy() {
        AudioEnhancer.stopFindMyTone()
        ProximityTracker.stopTracking()
        super.onDestroy()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnyPodsScreen(
    state: EarbudState,
    currentNoiseMode: NoiseMode,
    ancIntensity: AncIntensity,
    antiHiss: Boolean,
    conversationAwareness: Boolean,
    personalizedVolume: Boolean,
    loudSoundReduction: Boolean,
    ambientDecibels: Int,
    spatialAudioMode: SpatialAudioMode,
    toneProfile: ToneProfile,
    toneIntensity: ToneIntensity,
    tuningMode: FrequencyTuningMode,
    onSetTuningMode: (FrequencyTuningMode) -> Unit,
    lrBalance: Float,
    updateInfo: UpdateInfo?,
    isCheckingUpdate: Boolean,
    downloadProgress: Int,
    fitTesting: Boolean,
    fitProgress: Float,
    fitResult: FitTestResult?,
    onStartFitTest: () -> Unit,
    onStopFitTest: () -> Unit,
    volumeLimiterEnabled: Boolean,
    estimatedDb: Int,
    onToggleVolumeLimiter: (Boolean) -> Unit,
    dynamicIslandEnabled: Boolean,
    onToggleDynamicIsland: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    autoUpdateEnabled: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onConfirmUpdate: (UpdateInfo) -> Unit,
    onDismissUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onShowPopup: () -> Unit,
    onSelectNoiseMode: (NoiseMode) -> Unit,
    onToggleConversationAwareness: (Boolean) -> Unit,
    onTogglePersonalizedVolume: (Boolean) -> Unit,
    onToggleLoudReduction: (Boolean) -> Unit,
    onSetSpatialAudio: (SpatialAudioMode) -> Unit,
    onSetHeadphoneAccommodations: (ToneProfile, ToneIntensity) -> Unit,
    onSetBalance: (Float) -> Unit,
    onFindMy: (Boolean, Boolean) -> Unit,
    onStopFindMy: () -> Unit,
    onSetLeftPod: (Int, Boolean) -> Unit,
    onSetRightPod: (Int, Boolean) -> Unit,
    onSetCase: (Int, Boolean) -> Unit
) {
    var isBeeping by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageVersion by PodImageManager.imageUpdateFlow.collectAsState()
    val isFetchingImage by PodImageManager.isFetchingFlow.collectAsState()
    val hasBalancePermission by AudioEnhancer.hasBalancePermission.collectAsState()
    var showWebSearchDialog by remember { mutableStateOf(false) }
    var showBalanceDialog by remember { mutableStateOf(false) }

    val isDiagnosticConsentGranted by DiagnosticManager.consentGranted.collectAsState()
    var showReportDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var reportPreviewText by remember { mutableStateOf("") }

    val micSelection by me.anypods.app.audio.VoiceMicManager.micSelection.collectAsState()
    val hdVoiceEnabled by me.anypods.app.audio.VoiceMicManager.hdVoiceEnabled.collectAsState()
    val voiceClarityDsp by me.anypods.app.audio.VoiceMicManager.voiceClarityDspEnabled.collectAsState()
    val micBoostLevel by me.anypods.app.audio.VoiceMicManager.micBoostLevel.collectAsState()
    val signalBoostEnabled by BluetoothTracker.signalBoostEnabled.collectAsState()

    LaunchedEffect(Unit) {
        if (DiagnosticManager.shouldShowConsentPrompt(context)) {
            showConsentDialog = true
        }
    }

    val customBitmap = remember(state.name, imageVersion) {
        PodImageManager.getDeviceBitmap(context, state.name)
    }

    var selectedBrand by remember { mutableStateOf("All") }
    var selectedDeviceModel by remember {
        mutableStateOf(PodModelManager.getSelectedModel(context, state.name).name)
    }

    // Auto-detect matching pod model when Bluetooth connects, and auto-fetch web image
    LaunchedEffect(state.name, state.isConnected, state.modelId) {
        if (state.modelId.isNotEmpty()) {
            val modelInfo = PodModelManager.getModelById(state.modelId)
            selectedDeviceModel = modelInfo.name
            if (state.name.isNotEmpty() && state.name != "Wireless Earbuds") {
                PodImageManager.autoFetchIfMissing(context, state.name)
            }
        } else if (state.isConnected && state.name.isNotEmpty() && state.name != "Wireless Earbuds") {
            val detected = PodModelManager.detectModel(state.name)
            selectedDeviceModel = detected.name
            BluetoothTracker.setSelectedModel(detected.id)
            PodImageManager.autoFetchIfMissing(context, state.name)
        }
    }

    val currentModelInfo = PodModelManager.getModelByName(selectedDeviceModel)
    val modelImageRes = currentModelInfo.imageRes

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = "Pods",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = modelImageRes),
                                contentDescription = "Pods",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AnyPods Pro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    if (isCheckingUpdate) {
                        M3LoadingIndicator(size = 22.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = onCheckForUpdates) {
                            Text("⬆", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Refresh", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = if (state.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isConnected) "Connected (${state.name})" else "Waiting for Earbuds",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = selectedDeviceModel,
                                modifier = Modifier
                                    .size(190.dp)
                                    .padding(vertical = 4.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Image(
                                painter = painterResource(id = modelImageRes),
                                contentDescription = selectedDeviceModel,
                                modifier = Modifier
                                    .size(185.dp)
                                    .padding(vertical = 4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        if (isFetchingImage) {
                            M3LoadingIndicator(
                                size = 46.dp,
                                isContained = true,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Text(
                        text = if (customBitmap != null) "${state.name} • Web Auto-Matched" else "${currentModelInfo.brand} • ${currentModelInfo.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Web Photo Control Pill / Button
                    Surface(
                        color = if (customBitmap != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { showWebSearchDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (customBitmap != null) "🌐 Web Photo Active • Tap to Change" else if (isFetchingImage) "🌐 Fetching photo from web..." else "🌐 Fetch Real Photo from Web",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (customBitmap != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Brand Category Selector Filter Chips
                    val brands = listOf("All", "Apple", "Samsung", "Google", "Sony", "Nothing", "Beats", "OnePlus", "Universal")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        brands.forEach { brand ->
                            val isBrandSel = selectedBrand == brand
                            Surface(
                                color = if (isBrandSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clickable { selectedBrand = brand }
                            ) {
                                Text(
                                    text = brand,
                                    fontSize = 12.sp,
                                    fontWeight = if (isBrandSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBrandSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // All Earbud Models Horizontal Picker
                    val filteredModels = if (selectedBrand == "All") {
                        PodModelManager.ALL_MODELS
                    } else {
                        PodModelManager.ALL_MODELS.filter { it.brand.equals(selectedBrand, ignoreCase = true) }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredModels.forEach { model ->
                            val isSel = selectedDeviceModel == model.name
                            Surface(
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp),
                                border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .clickable {
                                        selectedDeviceModel = model.name
                                        BluetoothTracker.setSelectedModel(model.id)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = model.imageRes),
                                        contentDescription = model.name,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = model.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (state.address.isNotEmpty()) {
                        Text(
                            text = "MAC: ${state.address}  •  ${state.codec}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3 Distinct Battery Gauges (Left, Case, Right) with live ⚡ indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BatteryDial(
                            label = "Left Bud",
                            battery = state.leftBattery,
                            color = MaterialTheme.colorScheme.primary,
                            isCharging = state.isLeftCharging
                        )
                        BatteryDial(
                            label = "Case",
                            battery = state.caseBattery,
                            color = MaterialTheme.colorScheme.tertiary,
                            isCharging = state.isCaseCharging
                        )
                        BatteryDial(
                            label = "Right Bud",
                            battery = state.rightBattery,
                            color = MaterialTheme.colorScheme.secondary,
                            isCharging = state.isRightCharging
                        )
                    }
                }
            }

            // Quick Actions: Pop-up Trigger
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "AirPods Pop-up Card",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Show the floating popup card with live battery gauges and instant status.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onShowPopup,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Show AirPods Pop-up Card", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 1. Live Hardware Battery & Charging Dashboard (100% Automated)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Battery & Power",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-synced with connected Bluetooth hardware",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("⚡ Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Left Pod
                        BatteryIndicatorPill(
                            label = "Left Pod",
                            level = state.leftBattery,
                            isCharging = state.isLeftCharging,
                            statusText = if (state.isLeftCharging) "⚡ Charging" else "In Ear • Active"
                        )

                        // Case
                        BatteryIndicatorPill(
                            label = "Case",
                            level = state.caseBattery,
                            isCharging = state.isCaseCharging,
                            statusText = if (state.isCaseCharging) "⚡ Charging" else "Ready • In Case"
                        )

                        // Right Pod
                        BatteryIndicatorPill(
                            label = "Right Pod",
                            level = state.rightBattery,
                            isCharging = state.isRightCharging,
                            statusText = if (state.isRightCharging) "⚡ Charging" else "In Ear • Active"
                        )
                    }
                }
            }

            // 2. Noise Control Modes
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Noise Control",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentNoiseMode == NoiseMode.ADAPTIVE) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "Ambient: ${ambientDecibels} dB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Seamless DSP filtering without song stuttering or Bluetooth drops.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NoiseControlSegment(
                            title = "Noise Cancel",
                            iconRes = R.drawable.noise_cancellation,
                            isSelected = currentNoiseMode == NoiseMode.NOISE_CANCELLATION,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectNoiseMode(NoiseMode.NOISE_CANCELLATION) }
                        )

                        NoiseControlSegment(
                            title = "Off",
                            iconRes = R.drawable.close,
                            isSelected = currentNoiseMode == NoiseMode.OFF,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectNoiseMode(NoiseMode.OFF) }
                        )

                        NoiseControlSegment(
                            title = "Transparency",
                            iconRes = R.drawable.transparency,
                            isSelected = currentNoiseMode == NoiseMode.TRANSPARENCY,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectNoiseMode(NoiseMode.TRANSPARENCY) }
                        )

                        NoiseControlSegment(
                            title = "Adaptive",
                            iconRes = R.drawable.adaptive,
                            isSelected = currentNoiseMode == NoiseMode.ADAPTIVE,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectNoiseMode(NoiseMode.ADAPTIVE) }
                        )
                    }

                    // --- Advanced ANC Intensity & Acoustic Seal Tuning ---
                    if (currentNoiseMode == NoiseMode.NOISE_CANCELLATION) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cancellation Strength",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = when (ancIntensity) {
                                        AncIntensity.ULTRA -> "⚡ 98% Studio Seal"
                                        AncIntensity.BALANCED -> "⚡ 85% Commute"
                                        AncIntensity.COMFORT -> "⚡ 68% Gentle"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Intensity level selector chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Ultra Studio", "Max isolation (-30 dB)", AncIntensity.ULTRA),
                                Triple("Balanced", "Commute (-22 dB)", AncIntensity.BALANCED),
                                Triple("Comfort", "No pressure (-15 dB)", AncIntensity.COMFORT)
                            ).forEach { (title, subtitle, intensity) ->
                                val isSel = ancIntensity == intensity
                                Surface(
                                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { AudioEnhancer.setAncIntensity(intensity) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 9.sp,
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Anti-Hiss & Wind Shield Filter Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Anti-Hiss & Wind Guard", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "Filters out background white noise, DAC hiss, and air friction.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = antiHiss,
                                onCheckedChange = { AudioEnhancer.setAntiHissWindReduction(it) }
                            )
                        }
                    }

                    // --- Live Transparency Pass-Through Controls ---
                    if (currentNoiseMode == NoiseMode.TRANSPARENCY) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val passThroughActive by me.anypods.app.anc.LiveTransparencyManager.isLiveTransparencyActive.collectAsState()
                        val currentAmbientGain by me.anypods.app.anc.LiveTransparencyManager.ambientGain.collectAsState()
                        val speechFocus by me.anypods.app.anc.LiveTransparencyManager.speechFocusEnabled.collectAsState()
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ambient Sound Boost",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = if (passThroughActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (passThroughActive) "⚡ Live Pass-Through Active" else if (!hasMicPermission) "⚠️ Mic Permission Needed" else "⚡ Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (passThroughActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (!hasMicPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    (context as? androidx.activity.ComponentActivity)?.let { act ->
                                        androidx.core.app.ActivityCompat.requestPermissions(
                                            act,
                                            arrayOf(Manifest.permission.RECORD_AUDIO),
                                            200
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Grant Mic Permission for Live Transparency", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Ambient Boost Selector Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Natural", "1.2x Ambient", 1.2f),
                                Triple("Balanced", "1.6x Clear", 1.6f),
                                Triple("Amplified", "2.2x High", 2.2f)
                            ).forEach { (title, subtitle, gain) ->
                                val isSel = kotlin.math.abs(currentAmbientGain - gain) < 0.2f
                                Surface(
                                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { me.anypods.app.anc.LiveTransparencyManager.setGain(gain) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 9.sp,
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Speech Focus / Voice Isolation Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Speech Intelligibility Focus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "Enhances vocal presence so human conversation is crisp and clear.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = speechFocus,
                                onCheckedChange = { me.anypods.app.anc.LiveTransparencyManager.toggleSpeechFocus(it) }
                            )
                        }
                    }
                }
            }

            // 3. Audio Intelligence
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Audio Intelligence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conversation Awareness", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Auto-ducks music and turns on Transparency when you speak.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = conversationAwareness,
                            onCheckedChange = onToggleConversationAwareness
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    val ambientNoiseDb by NoiseControlManager.ambientDecibels.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Personalized Volume", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                if (personalizedVolume) "⚡ Live Ambient: $ambientNoiseDb dB • Automatically fine-tuning volume"
                                else "Automatically fine-tunes media volume based on ambient noise.",
                                fontSize = 12.sp,
                                color = if (personalizedVolume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (personalizedVolume) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = personalizedVolume,
                            onCheckedChange = onTogglePersonalizedVolume
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Loud Sound Reduction", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Protects hearing by smoothing harsh peaks.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = loudSoundReduction,
                            onCheckedChange = onToggleLoudReduction
                        )
                    }
                }
            }

            // 4. Spatial Audio (Apple 3D Binaural Soundstage)
            SpatialAudioCard(
                spatialAudioMode = spatialAudioMode,
                onSetSpatialAudio = onSetSpatialAudio
            )

            // 5. Headphone Accommodations (Apple Audiophile Tuning)
            HeadphoneAccommodationsCard(
                toneProfile = toneProfile,
                toneIntensity = toneIntensity,
                onSetHeadphoneAccommodations = onSetHeadphoneAccommodations
            )

            // 6. Dedicated AirPods Frequency Tuning (Hardware-calibrated curves)
            AirPodsFrequencyTuningCard(
                currentModelName = selectedDeviceModel,
                tuningMode = tuningMode,
                onSetTuningMode = onSetTuningMode
            )

            // 7. Left / Right Audio Balance
            AudioBalanceCard(
                lrBalance = lrBalance,
                hasBalancePermission = hasBalancePermission,
                onSetBalance = onSetBalance,
                onShowPermissionDialog = { showBalanceDialog = true }
            )

            // 8. Microphone & Voice Clarity Card (HD 16kHz Wideband Voice, Vocal Gain Boost & Pod Mic Selection)
            VoiceMicCard(
                micSelection = micSelection,
                onSetMicSelection = { me.anypods.app.audio.VoiceMicManager.setMicSelection(context, it) },
                hdVoiceEnabled = hdVoiceEnabled,
                onToggleHdVoice = { me.anypods.app.audio.VoiceMicManager.setHdVoiceEnabled(context, it) },
                voiceClarityDsp = voiceClarityDsp,
                onToggleVoiceClarityDsp = { me.anypods.app.audio.VoiceMicManager.setVoiceClarityDspEnabled(it) },
                micBoostLevel = micBoostLevel,
                onSetMicBoostLevel = { me.anypods.app.audio.VoiceMicManager.setMicBoostLevel(it) }
            )

            // 9. Long-Range Signal Boost Card (LE Coded PHY S=8 & High-Priority RF)
            SignalBoostCard(
                signalBoostEnabled = signalBoostEnabled,
                onToggleSignalBoost = { BluetoothTracker.toggleSignalBoost(context, it) }
            )

            // 7. Find My Earbuds (Precision Distance Radar & Audio Siren)
            PrecisionFindMyCard(
                state = state,
                isBeeping = isBeeping,
                onFindMy = { left, right ->
                    isBeeping = true
                    onFindMy(left, right)
                },
                onStopFindMy = {
                    isBeeping = false
                    onStopFindMy()
                }
            )

            // 8. Apple Ear Tip Fit Test (Acoustic Reflection)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ear Tip Fit Test",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Acoustic Sweep",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tests if your ear tips seal the ear canal properly for deep bass and optimal noise cancellation.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (fitTesting) {
                        Text(
                            text = "Playing Apple acoustic test chime... ${(fitProgress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        M3LinearProgressIndicator(
                            progress = { fitProgress },
                            modifier = Modifier.fillMaxWidth(),
                            strokeHeight = 10.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onStopFitTest,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Cancel Test")
                        }
                    } else if (fitResult != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Left: ",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (fitResult.isLeftGood) "Good Seal ✓" else "Adjust Tip",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (fitResult.isLeftGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Right: ",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (fitResult.isRightGood) "Good Seal ✓" else "Adjust Tip",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (fitResult.isRightGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = "Acoustic Seal Quality: ${fitResult.sealScorePercent}%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onStartFitTest,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Test Again")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartFitTest,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Run Ear Tip Fit Test")
                        }
                    }
                }
            }

            // 9. Hearing Health & Volume Limiter
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hearing Health & Safe Volume",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Est. Level: $estimatedDb dB SPL (Safe <85dB)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (estimatedDb <= 80) MaterialTheme.colorScheme.primary else if (estimatedDb <= 85) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = volumeLimiterEnabled,
                            onCheckedChange = onToggleVolumeLimiter
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "85 dB volume limiter prevents sudden hearing damage and long-term ear fatigue during music playback.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 10. iOS 18 Dynamic Island Pill
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Island Camera Pill",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Interactive punch-hole pill with battery & noise modes",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicIslandEnabled,
                            onCheckedChange = onToggleDynamicIsland
                        )
                    }
                }
            }

            // 11. Quick Settings Tile & Home Widget Status
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Quick Controls Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Quick Settings Tile: Pull down your notification bar and add 'Noise Control' for 1-tap ANC switching.\n• Home Screen Widget: Long press home screen to add AnyPods Pro battery dials & ANC controls.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Diagnostics & Telemetry Card
            DiagnosticSettingsCard(
                isConsentGranted = isDiagnosticConsentGranted,
                onToggleConsent = { granted ->
                    DiagnosticManager.setConsentGranted(context, granted)
                },
                onViewReport = {
                    reportPreviewText = DiagnosticManager.buildDiagnosticReport(context, state)
                    showReportDialog = true
                },
                onShareReport = {
                    if (!isDiagnosticConsentGranted) {
                        showConsentDialog = true
                    } else {
                        DiagnosticManager.shareReport(context, state)
                    }
                }
            )

            // Software Update Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Software Update",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "GitHub: SHAURYASANYAL3/anypods",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !isCheckingUpdate && (downloadProgress < 0 || downloadProgress == 100),
                            shape = RoundedCornerShape(50)
                        ) {
                            if (isCheckingUpdate) {
                                M3LoadingIndicator(
                                    size = 18.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...")
                            } else {
                                Text("Check Update")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Auto In-App Updates",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Automatically downloads and updates seamlessly in-app",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = onToggleAutoUpdate
                        )
                    }

                    if (updateInfo != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update Available: ${updateInfo.tagName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = updateInfo.releaseNotes.take(60) + if (updateInfo.releaseNotes.length > 60) "..." else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Button(
                                    onClick = { onConfirmUpdate(updateInfo) },
                                    shape = RoundedCornerShape(50),
                                    enabled = downloadProgress < 0 || downloadProgress == 100
                                ) {
                                    Text(if (downloadProgress in 0..99) "$downloadProgress%" else "Install")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Auto-update prompt modal dialog
        SoftwareUpdateDialog(
            updateInfo = updateInfo,
            downloadProgress = downloadProgress,
            onConfirmUpdate = onConfirmUpdate,
            onDismissUpdate = onDismissUpdate
        )

        // Web Image Search & Custom Fetch Dialog
        WebImageSearchDialog(
            show = showWebSearchDialog,
            state = state,
            customBitmap = customBitmap,
            isFetchingImage = isFetchingImage,
            onDismiss = { showWebSearchDialog = false }
        )

        // System Audio Balance ADB Setup Dialog
        BalancePermissionDialog(
            show = showBalanceDialog,
            onDismiss = { showBalanceDialog = false }
        )

        // Diagnostic Sharing Consent Dialog
        DiagnosticConsentDialog(
            show = showConsentDialog,
            onConfirm = { granted ->
                DiagnosticManager.setConsentGranted(context, granted)
                showConsentDialog = false
                if (granted) {
                    Toast.makeText(context, "Diagnostic sharing enabled. Thank you for supporting AnyPods!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Diagnostic Report Preview & Share Dialog
        DiagnosticReportDialog(
            show = showReportDialog,
            reportText = reportPreviewText,
            onShare = {
                DiagnosticManager.shareReport(context, state)
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
fun BatteryIndicatorPill(
    label: String,
    level: Int,
    isCharging: Boolean,
    statusText: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            M3CircularProgressIndicator(
                progress = { (level.coerceIn(0, 100) / 100f) },
                modifier = Modifier.size(54.dp),
                color = if (isCharging) MaterialTheme.colorScheme.tertiary else if (level > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 5.dp
            )
            Text(
                text = "$level%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = statusText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DeviceBatteryControlSection(
    title: String,
    level: Int,
    isCharging: Boolean,
    onLevelChange: (Int) -> Unit,
    onChargingChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$level%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
                if (isCharging) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⚡ Charging",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val presets = listOf(20, 50, 80, 100)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { pct ->
                OutlinedButton(
                    onClick = { onLevelChange(pct) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (level == pct) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Text("$pct%", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = 0f..100f
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active Charging (⚡)", fontSize = 13.sp)
            Switch(
                checked = isCharging,
                onCheckedChange = onChargingChange
            )
        }
    }
}

@Composable
fun BatteryDial(
    label: String,
    battery: Int,
    color: Color,
    isCharging: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(78.dp)
        ) {
            M3CircularProgressIndicator(
                progress = { if (battery < 0) 0f else battery / 100f },
                modifier = Modifier.fillMaxSize(),
                color = if (isCharging) MaterialTheme.colorScheme.primary else color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 6.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (battery >= 0) "$battery%" else "--",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCharging) {
                        Text(text = "⚡", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NoiseControlSegment(
    title: String,
    iconRes: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
