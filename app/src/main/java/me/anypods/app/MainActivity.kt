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
                onSetCaseBattery = { level, isCharging ->
                    BluetoothTracker.setCaseBattery(level, isCharging)
                },
                onSetBudCharging = { left, charging ->
                    BluetoothTracker.setBudCharging(left, charging)
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
    onSetCaseBattery: (Int, Boolean) -> Unit,
    onSetBudCharging: (Boolean, Boolean) -> Unit
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
                    // Status Badge
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

                    // 3 Distinct Battery Gauges (Left, Case, Right)
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

            // 1. Noise Control Modes (Noise Cancellation, Off, Transparency, Adaptive Audio)
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
                        text = "Apple AirPods Pro acoustic modes: Full Noise Cancellation, Transparency pass-through, or smart Adaptive Audio.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 4-Segment Noise Control
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

            // 2. Audio Intelligence: Conversation Awareness, Personalized Volume, Loud Sound Reduction
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

                    // Conversation Awareness
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

                    // Personalized Volume
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

                    // Loud Sound Reduction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Loud Sound Reduction", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Caps sudden harsh sounds (horns, sirens) in Transparency mode.",
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

            // 3. Spatial Audio (Fixed, Head Tracked, Off)
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

            // 4. Headphone Accommodations (AirPods Pro EQ Tuning)
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

                    // Profiles
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

                    // Intensities
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

            // 5. Left / Right Audio Balance
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

            // 6. Case Battery & Charging Level Calibration Card
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
                            text = "Case Battery & Live Charging",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.caseBattery}%" + (if (state.isCaseCharging) " ⚡" else ""),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isCaseCharging) Color(0xFF34C759) else Color(0xFF007AFF)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fine-tune charging case battery level if your earbuds do not report individual case telemetry.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val casePresets = listOf(25, 45, 70, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        casePresets.forEach { level ->
                            OutlinedButton(
                                onClick = { onSetCaseBattery(level, state.isCaseCharging) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (state.caseBattery == level) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text("$level%", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = state.caseBattery.toFloat(),
                        onValueChange = { onSetCaseBattery(it.toInt(), state.isCaseCharging) },
                        valueRange = 0f..100f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Case is plugged in (⚡ Charging)", fontSize = 14.sp)
                        Switch(
                            checked = state.isCaseCharging,
                            onCheckedChange = { onSetCaseBattery(state.caseBattery, it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Left Earbud Charging (⚡)", fontSize = 14.sp)
                        Switch(
                            checked = state.isLeftCharging,
                            onCheckedChange = { onSetBudCharging(true, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Right Earbud Charging (⚡)", fontSize = 14.sp)
                        Switch(
                            checked = state.isRightCharging,
                            onCheckedChange = { onSetBudCharging(false, it) }
                        )
                    }
                }
            }

            // 7. Find My Earbuds (Ultra Loud Beacon +25dB Boost)
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
                        text = "Overrides volume to 100% with +25dB gain boost so you can hear lost earbuds outside or across rooms.",
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

            // 8. Quick Actions: Pop-up Trigger
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

            Spacer(modifier = Modifier.height(20.dp))
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
