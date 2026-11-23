package me.anypods.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.anypods.app.bluetooth.ProximityTracker
import me.anypods.app.model.EarbudState

@Composable
fun PrecisionFindMyCard(
    state: EarbudState,
    isBeeping: Boolean,
    onFindMy: (Boolean, Boolean) -> Unit,
    onStopFindMy: () -> Unit
) {
    val context = LocalContext.current
    val isTracking by ProximityTracker.isTracking.collectAsState()
    val distance by ProximityTracker.distanceMeters.collectAsState()
    val signalPct by ProximityTracker.signalPercent.collectAsState()
    val rssi by ProximityTracker.rssiDbm.collectAsState()
    val proximityStatus by ProximityTracker.proximityStatus.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Distance Radar, 1 = Audio Siren

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Find My Earbuds",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Precision distance radar & audio siren locator",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (isTracking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isTracking) "RADAR LIVE" else "READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTracking) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // M3 Expressive Pill Segmented Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 0 },
                    shape = RoundedCornerShape(50),
                    color = if (activeTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Text(
                        text = "📡 Precision Radar",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 1 },
                    shape = RoundedCornerShape(50),
                    color = if (activeTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Text(
                        text = "🔊 Sound Siren",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (activeTab == 0) {
                // Precision Distance Radar UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulsing Radar Concentric Circles & Distance Counter
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(
                                color = if (isTracking) {
                                    if (distance <= 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else if (distance <= 3.0f) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
                                } else MaterialTheme.colorScheme.surfaceContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner ring
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .background(
                                    color = if (isTracking) {
                                        if (distance <= 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else if (distance <= 3.0f) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                    } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isTracking) {
                                    Text(
                                        text = "$distance",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (distance <= 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "METERS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "📡",
                                        fontSize = 36.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Radar Idle",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Proximity Status Pill Chip
                    Surface(
                        color = if (isTracking) {
                            if (distance <= 1.0f) MaterialTheme.colorScheme.primaryContainer
                            else if (distance <= 3.0f) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        } else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isTracking) proximityStatus else "Ready to track ${state.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTracking) {
                                if (distance <= 1.0f) MaterialTheme.colorScheme.onPrimaryContainer
                                else if (distance <= 3.0f) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isTracking) {
                        // Signal Strength Progress Bar & Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Signal Strength: $signalPct%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("$rssi dBm • Haptic Feedback", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        M3LinearProgressIndicator(
                            progress = { signalPct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            strokeHeight = 8.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button: Start / Stop Tracking
                    FilledTonalButton(
                        onClick = {
                            if (isTracking) {
                                ProximityTracker.stopTracking()
                            } else {
                                val address = state.address
                                if (address.isNotBlank()) {
                                    ProximityTracker.startTracking(context, address)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        enabled = state.address.isNotBlank()
                    ) {
                        Text(
                            text = if (isTracking) "Stop Radar Tracking" else "Start Precision Finding Radar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Audio Siren Beep Locator
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Plays an ultra-loud +25dB siren directly inside the earbud drivers so you can locate them under cushions, in clothes, or outdoors.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onFindMy(true, false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Left Pod", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onFindMy(true, true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Both Pods", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onFindMy(false, true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Right Pod", fontSize = 12.sp)
                        }
                    }

                    if (isBeeping) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStopFindMy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text("Stop Audio Siren", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
