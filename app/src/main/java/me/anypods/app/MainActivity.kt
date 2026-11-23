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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode
import me.anypods.app.audio.AudioEnhancer
import me.anypods.app.audio.SpatialAudioMode
import me.anypods.app.audio.ToneIntensity
import me.anypods.app.audio.ToneProfile
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.model.EarbudState
import me.anypods.app.ui.OverlayPopup
import me.anypods.app.updater.UpdateInfo
import me.anypods.app.updater.UpdateManager

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        BluetoothTracker.refreshConnectedDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioEnhancer.initAudioEffects()
        checkAndRequestPermissions()
        // Auto-check for updates from GitHub in the background
        UpdateManager.checkForUpdates(this, isManual = false)

        setContent {
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

            val updateInfo by UpdateManager.updateAvailable.collectAsState()
            val isCheckingUpdate by UpdateManager.isChecking.collectAsState()
            val downloadProgress by UpdateManager.downloadProgress.collectAsState()

            AnyPodsScreen(
                state = earbudState,
                currentNoiseMode = noiseMode,
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
                    AudioEnhancer.setAudioBalance(it)
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

    override fun onResume() {
        super.onResume()
        BluetoothTracker.refreshConnectedDevice()
    }

    override fun onDestroy() {
        AudioEnhancer.stopFindMyTone()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.airpods),
                            contentDescription = "Pods",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.size(36.dp)
                        )
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Image(
                        painter = painterResource(id = R.drawable.airpods),
                        contentDescription = "AirPods Visual",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(150.dp)
                    )

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

            // 1. Independent Battery & Charging Calibration for ALL THREE (Left, Right, Case)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Battery & Charging Calibration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set exact individual battery levels and live ⚡ charging state for Left Pod, Right Pod, and Case.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // LEFT POD CONTROLS
                    DeviceBatteryControlSection(
                        title = "Left Earbud",
                        level = state.leftBattery,
                        isCharging = state.isLeftCharging,
                        onLevelChange = { onSetLeftPod(it, state.isLeftCharging) },
                        onChargingChange = { onSetLeftPod(state.leftBattery, it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // RIGHT POD CONTROLS
                    DeviceBatteryControlSection(
                        title = "Right Earbud",
                        level = state.rightBattery,
                        isCharging = state.isRightCharging,
                        onLevelChange = { onSetRightPod(it, state.isRightCharging) },
                        onChargingChange = { onSetRightPod(state.rightBattery, it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // CHARGING CASE CONTROLS
                    DeviceBatteryControlSection(
                        title = "Charging Case",
                        level = state.caseBattery,
                        isCharging = state.isCaseCharging,
                        onLevelChange = { onSetCase(it, state.isCaseCharging) },
                        onChargingChange = { onSetCase(state.caseBattery, it) }
                    )
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Personalized Volume", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Automatically fine-tunes media volume based on ambient noise.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // 4. Spatial Audio
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spatial Audio (3D Surround)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Immersive theatre-style sound stage with audio virtualization.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            SpatialAudioMode.OFF to "Off",
                            SpatialAudioMode.FIXED to "Fixed",
                            SpatialAudioMode.HEAD_TRACKED to "Wide Stage"
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
                                Text(name, fontSize = 13.sp, fontWeight = if (spatialAudioMode == mode) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // 5. Headphone Accommodations (AirPods Pro EQ Tuning)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Headphone Accommodations (EQ)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Custom audio tuning profile directly from iOS accessibility settings.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Tuning Profile:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val profiles = listOf(
                            ToneProfile.BALANCED_TONE to "Balanced",
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
                                Text(label, fontSize = 11.sp, textAlign = TextAlign.Center)
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
                            ToneIntensity.SLIGHT to "Slight",
                            ToneIntensity.MODERATE to "Moderate",
                            ToneIntensity.STRONG to "Strong"
                        )
                        intensities.forEach { (intensity, label) ->
                            OutlinedButton(
                                onClick = { onSetHeadphoneAccommodations(toneProfile, intensity) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (toneIntensity == intensity) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                )
                            ) {
                                Text(label, fontSize = 12.sp)
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

            // 7. Find My Earbuds
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Find My Earbuds (Ultra-Loud)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Plays +25dB gain siren directly inside the earbud drivers so you can locate them across rooms or outside.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isBeeping = true
                                onFindMy(true, false)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Chirp Left", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                isBeeping = true
                                onFindMy(true, true)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                        ) {
                            Text("Chirp Both", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                isBeeping = true
                                onFindMy(false, true)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Chirp Right", fontSize = 12.sp)
                        }
                    }

                    if (isBeeping) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                isBeeping = false
                                onStopFindMy()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Stop Beeping", color = Color(0xFFFF5252))
                        }
                    }
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
