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
import me.anypods.app.ui.theme.AnyPodsTheme
import me.anypods.app.updater.UpdateInfo
import me.anypods.app.updater.UpdateManager
import me.anypods.app.audio.EarTipFitTester
import me.anypods.app.audio.HearingHealthManager
import me.anypods.app.audio.FitTestResult
import me.anypods.app.widget.AnyPodsWidgetProvider

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        BluetoothTracker.refreshConnectedDevice()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAudioSessionIntent(intent)
    }

    private fun handleAudioSessionIntent(intent: Intent?) {
        val sessionId = intent?.getIntExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: 0
        if (sessionId > 0) {
            AudioEnhancer.registerSession(sessionId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioEnhancer.initAudioEffects()
        AudioEnhancer.initBalance(this)
        handleAudioSessionIntent(intent)
        checkAndRequestPermissions()
        // Auto-check for updates from GitHub in the background
        UpdateManager.checkForUpdates(this, isManual = false)

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
            val lrBalance by AudioEnhancer.currentBalance.collectAsState()
            val ancIntensity by AudioEnhancer.currentAncIntensity.collectAsState()
            val antiHiss by AudioEnhancer.antiHissWindReduction.collectAsState()

            val updateInfo by UpdateManager.updateAvailable.collectAsState()
            val isCheckingUpdate by UpdateManager.isChecking.collectAsState()
            val downloadProgress by UpdateManager.downloadProgress.collectAsState()

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
    var showWebSearchDialog by remember { mutableStateOf(false) }

    val customBitmap = remember(state.name, imageVersion) {
        PodImageManager.getDeviceBitmap(context, state.name)
    }

    var selectedBrand by remember { mutableStateOf("All") }
    var selectedDeviceModel by remember {
        mutableStateOf(PodModelManager.getSelectedModel(context, state.name).name)
    }

    // Auto-detect matching pod model when Bluetooth connects, and auto-fetch web image
    LaunchedEffect(state.name, state.isConnected) {
        if (state.isConnected && state.name.isNotEmpty() && state.name != "Wireless Earbuds") {
            val detected = PodModelManager.detectModel(state.name)
            selectedDeviceModel = detected.name
            PodModelManager.saveSelectedModel(context, detected.id)
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = onCheckForUpdates) {
                            Text("⬆", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onRefresh,
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
                shape = RoundedCornerShape(24.dp),
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
                                color = if (state.isConnected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (state.isConnected) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isConnected) "Connected (${state.name})" else "Waiting for Earbuds",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (state.isConnected) Color(0xFF2E7D32) else Color(0xFFEF6C00)
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
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
                        color = if (customBitmap != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { showWebSearchDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (customBitmap != null) "🌐 Web Photo Active • Tap to Search/Change" else if (isFetchingImage) "🌐 Fetching photo from web..." else "🌐 Fetch Real Photo from Web",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (customBitmap != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                color = if (isBrandSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable { selectedBrand = brand }
                            ) {
                                Text(
                                    text = brand,
                                    fontSize = 11.sp,
                                    fontWeight = if (isBrandSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBrandSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .clickable {
                                        selectedDeviceModel = model.name
                                        PodModelManager.saveSelectedModel(context, model.id)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

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
                            color = Color(0xFF34C759),
                            isCharging = state.isLeftCharging
                        )
                        BatteryDial(
                            label = "Case",
                            battery = state.caseBattery,
                            color = Color(0xFF007AFF),
                            isCharging = state.isCaseCharging
                        )
                        BatteryDial(
                            label = "Right Bud",
                            battery = state.rightBattery,
                            color = Color(0xFF34C759),
                            isCharging = state.isRightCharging
                        )
                    }
                }
            }

            // Quick Actions: Pop-up Trigger
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AirPods Pop-up Card",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Show the Apple iOS style floating popup card with live battery gauges.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onShowPopup,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                    ) {
                        Text("Show AirPods Pop-up Card", color = Color.White)
                    }
                }
            }

            // 1. Live Hardware Battery & Charging Dashboard (100% Automated)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
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
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            Text(
                                text = "Ambient: ${ambientDecibels} dB",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
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
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

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
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when (ancIntensity) {
                                        AncIntensity.ULTRA -> "⚡ 98% Studio Seal"
                                        AncIntensity.BALANCED -> "⚡ 85% Commute"
                                        AncIntensity.COMFORT -> "⚡ 68% Gentle"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Intensity level selector chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Ultra Studio", "Max isolation (-30 dB)", AncIntensity.ULTRA),
                                Triple("Balanced", "Commute (-22 dB)", AncIntensity.BALANCED),
                                Triple("Comfort", "No ear pressure (-15 dB)", AncIntensity.COMFORT)
                            ).forEach { (title, subtitle, intensity) ->
                                val isSel = ancIntensity == intensity
                                Surface(
                                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { AudioEnhancer.setAncIntensity(intensity) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))

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
                }
            }

            // 3. Audio Intelligence
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audio Intelligence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Spatial Audio (3D Surround)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Binaural 3D",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Immersive theatre-style sound stage with binaural virtualizer and acoustic stage widening.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            SpatialAudioMode.OFF to "Off (Stereo)",
                            SpatialAudioMode.FIXED to "Fixed (Cinema)",
                            SpatialAudioMode.HEAD_TRACKED to "Wide Stage (3D)"
                        )
                        modes.forEach { (mode, name) ->
                            OutlinedButton(
                                onClick = { onSetSpatialAudio(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (spatialAudioMode == mode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (spatialAudioMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // 5. Headphone Accommodations (Apple Audiophile Tuning)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Headphone Accommodations (EQ)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Apple iOS Profile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (toneProfile) {
                            ToneProfile.BALANCED_TONE -> "Balanced: Rich warm punchy bass + silky crystal highs (Apple Harman curve)."
                            ToneProfile.VOCAL_RANGE -> "Vocal Range: Pushes dialogue and voices forward with clear articulation."
                            ToneProfile.BRIGHTNESS -> "Brightness: Crisp cymbal sparkle, airy strings, and high-frequency definition."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Tuning Profile:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val profiles = listOf(
                            ToneProfile.BALANCED_TONE to "Balanced Tone",
                            ToneProfile.VOCAL_RANGE to "Vocal Range",
                            ToneProfile.BRIGHTNESS to "Brightness"
                        )
                        profiles.forEach { (prof, label) ->
                            OutlinedButton(
                                onClick = { onSetHeadphoneAccommodations(prof, toneIntensity) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (toneProfile == prof) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (toneProfile == prof) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Boost Level:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val intensities = listOf(
                            ToneIntensity.SLIGHT to "Slight (+4dB)",
                            ToneIntensity.MODERATE to "Moderate (+7dB)",
                            ToneIntensity.STRONG to "Strong (+10dB)"
                        )
                        intensities.forEach { (intensity, label) ->
                            OutlinedButton(
                                onClick = { onSetHeadphoneAccommodations(toneProfile, intensity) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (toneIntensity == intensity) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (toneIntensity == intensity) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // 6. Left / Right Audio Balance
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Left / Right Audio Balance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                lrBalance < -0.1f -> "L ${(-lrBalance * 100).toInt()}%"
                                lrBalance > 0.1f -> "R ${(lrBalance * 100).toInt()}%"
                                else -> "Center"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust balance if one ear hears less sound.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = lrBalance,
                        onValueChange = onSetBalance,
                        valueRange = -1f..1f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Left", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Center", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Right", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ear Tip Fit Test",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Acoustic Sweep",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tests if your ear tips seal the ear canal properly for deep bass and optimal noise cancellation.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (fitTesting) {
                        Text(
                            text = "Playing Apple acoustic test chime... ${(fitProgress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fitProgress },
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onStopFitTest,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
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
                                        color = if (fitResult.isLeftGood) Color(0xFF34C759) else Color(0xFFFF9500)
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
                                        color = if (fitResult.isRightGood) Color(0xFF34C759) else Color(0xFFFF9500)
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Test Again")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartFitTest,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Run Ear Tip Fit Test")
                        }
                    }
                }
            }

            // 9. Hearing Health & Volume Limiter
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
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
                                text = "Est. Level: $estimatedDb dB SPL (WHO Safe <85dB)",
                                fontSize = 13.sp,
                                color = if (estimatedDb <= 80) Color(0xFF34C759) else if (estimatedDb <= 85) Color(0xFFFF9500) else Color(0xFFFF3B30)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
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

            // Software Update Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
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
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...")
                            } else {
                                Text("Check Update")
                            }
                        }
                    }

                    if (updateInfo != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
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
        if (updateInfo != null) {
            AlertDialog(
                onDismissRequest = {
                    if (downloadProgress < 0) onDismissUpdate()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Update Available", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = updateInfo.tagName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "A new version of AnyPods Pro is available. Would you like to update now or stay on the current version?",
                            fontSize = 14.sp
                        )
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = updateInfo.releaseNotes,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (downloadProgress in 0..100) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (downloadProgress == 100) "Download complete! Opening installer..." else "Downloading update: $downloadProgress%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmUpdate(updateInfo) },
                        enabled = downloadProgress < 0 || downloadProgress == 100,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (downloadProgress in 0..99) "Downloading..." else "Update Now")
                    }
                },
                dismissButton = {
                    if (downloadProgress < 0) {
                        OutlinedButton(onClick = onDismissUpdate) {
                            Text("Stay on Current Version")
                        }
                    }
                }
            )
        }

        // Web Image Search & Custom Fetch Dialog
        if (showWebSearchDialog) {
            val defaultSearchQuery = if (state.name.isNotBlank() && state.name != "Wireless Earbuds") {
                "${state.name} earbuds"
            } else {
                "AirPods Pro earbuds"
            }
            var queryText by remember { mutableStateOf(defaultSearchQuery) }

            AlertDialog(
                onDismissRequest = { if (!isFetchingImage) showWebSearchDialog = false },
                title = {
                    Text("Search & Fetch Pod Photo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "AnyPods can search the whole internet to find and apply high-resolution product photos for ANY wireless earbuds in the world.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            label = { Text("Earbuds Model or Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isFetchingImage) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching & downloading photo...", fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetDeviceName = if (state.name.isNotBlank() && state.name != "Wireless Earbuds") state.name else queryText
                            PodImageManager.fetchDeviceImage(context, targetDeviceName, queryText) { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) "Photo matched & applied successfully!" else "Could not download photo. Please try different keywords.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showWebSearchDialog = false
                            }
                        },
                        enabled = !isFetchingImage && queryText.isNotBlank()
                    ) {
                        Text(if (isFetchingImage) "Searching..." else "Search & Apply")
                    }
                },
                dismissButton = {
                    Row {
                        if (customBitmap != null) {
                            TextButton(
                                onClick = {
                                    PodImageManager.deleteDeviceImage(context, state.name)
                                    showWebSearchDialog = false
                                    Toast.makeText(context, "Reset to default model icon", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isFetchingImage
                            ) {
                                Text("Reset Icon", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(
                            onClick = { showWebSearchDialog = false },
                            enabled = !isFetchingImage
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
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
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (level.coerceIn(0, 100) / 100f) },
                modifier = Modifier.size(52.dp),
                color = if (level > 20) Color(0xFF34C759) else Color(0xFFFF3B30),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                strokeWidth = 4.5.dp
            )
            Text(
                text = "$level%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = statusText,
            fontSize = 10.sp,
            color = if (isCharging) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = if (isCharging) Color(0xFF34C759) else MaterialTheme.colorScheme.primary
                )
                if (isCharging) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⚡ Charging",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF34C759)
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
                    shape = RoundedCornerShape(10.dp),
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
    val progress = animateFloatAsState(
        targetValue = if (battery < 0) 0f else battery / 100f,
        label = "BatteryProgress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = if (isCharging) Color(0xFF34C759) else color,
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (battery >= 0) "$battery%" else "--",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (isCharging) {
                        Text(text = "⚡", fontSize = 12.sp, color = Color(0xFF34C759))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
