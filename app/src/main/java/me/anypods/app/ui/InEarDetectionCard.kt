package me.anypods.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.anypods.app.model.EarbudState

@Composable
fun InEarDetectionCard(
    state: EarbudState,
    autoPauseEnabled: Boolean,
    onToggleAutoPause: (Boolean) -> Unit,
    autoResumeEnabled: Boolean,
    onToggleAutoResume: (Boolean) -> Unit,
    earChimeEnabled: Boolean,
    onToggleEarChime: (Boolean) -> Unit,
    lowBatteryAlertEnabled: Boolean,
    onToggleLowBatteryAlert: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row with Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic In-Ear Detection",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Smart optical & skin sensor media automation",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (state.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(50)
                ) {
                    val statusText = if (state.isConnected) {
                        val leftIn = if (state.isLeftInEar) "In" else "Out"
                        val rightIn = if (state.isRightInEar) "In" else "Out"
                        "L: $leftIn • R: $rightIn"
                    } else {
                        "Standby"
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Switch 1: Auto-Pause on Removal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Pause Playback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pauses music, video, and podcasts when an earbud is removed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPauseEnabled,
                    onCheckedChange = onToggleAutoPause
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switch 2: Auto-Resume on Re-insert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Resume Playback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Resumes previously playing media when placed back in ear",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoResumeEnabled,
                    onCheckedChange = onToggleAutoResume,
                    enabled = autoPauseEnabled
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switch 3: In-Ear Insertion Chime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "In-Ear Insertion Chime",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Plays a soft ear-con chime when earbud is seated in your ear",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = earChimeEnabled,
                    onCheckedChange = onToggleEarChime
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switch 4: Low-Battery Warning (< 15%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Low-Battery Warning Alert (< 15%)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sends a heads-up notification and plays an alert chime",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = lowBatteryAlertEnabled,
                    onCheckedChange = onToggleLowBatteryAlert
                )
            }
        }
    }
}
