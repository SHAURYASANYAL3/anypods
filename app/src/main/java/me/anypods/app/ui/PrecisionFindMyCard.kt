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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                    color = if (isTracking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isTracking) "RADAR LIVE" else "READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTracking) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // M3 Secondary Tab Row / Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 0 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeTab == 0) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                ) {
                    Text(
                        text = "📡 Precision Radar",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeTab == 1) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                ) {
                    Text(
                        text = "🔊 Sound Siren",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeTab == 0) {
                // Precision Distance Radar UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulsing Radar Circle & Distance Counter
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(
                                color = if (isTracking) {
                                    if (distance <= 1.0f) Color(0x334CAF50)
                                    else if (distance <= 3.0f) Color(0x33FFB300)
                                    else Color(0x33006495)
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
                                        if (distance <= 1.0f) Color(0x444CAF50)
                                        else if (distance <= 3.0f) Color(0x44FFB300)
                                        else Color(0x44006495)
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
                                        color = if (distance <= 1.0f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
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

                    // Proximity Status Chip
                    Surface(
                        color = if (isTracking) {
                            if (distance <= 1.0f) Color(0xFFE8F5E9)
                            else if (distance <= 3.0f) Color(0xFFFFF8E1)
                            else MaterialTheme.colorScheme.secondaryContainer
                        } else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (isTracking) proximityStatus else "Ready to track ${state.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTracking) {
                                if (distance <= 1.0f) Color(0xFF2E7D32)
                                else if (distance <= 3.0f) Color(0xFFE65100)
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { signalPct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Round,
                            color = if (distance <= 1.0f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                        shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Left Pod", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onFindMy(true, true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Both Pods", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onFindMy(false, true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Right Pod", fontSize = 12.sp)
                        }
                    }

                    if (isBeeping) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStopFindMy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
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
