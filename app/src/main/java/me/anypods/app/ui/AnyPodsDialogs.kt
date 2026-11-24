package me.anypods.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.anypods.app.model.EarbudState
import me.anypods.app.model.PodImageManager
import me.anypods.app.updater.UpdateInfo

@Composable
fun SoftwareUpdateDialog(
    updateInfo: UpdateInfo?,
    downloadProgress: Int,
    onConfirmUpdate: (UpdateInfo) -> Unit,
    onDismissUpdate: () -> Unit
) {
    if (updateInfo == null) return

    AlertDialog(
        onDismissRequest = {
            if (downloadProgress < 0) onDismissUpdate()
        },
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Update Available", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = updateInfo.tagName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (downloadProgress in 0..100) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (downloadProgress == 100) "Download complete! Opening installer..." else "Downloading update: $downloadProgress%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    M3LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        strokeHeight = 10.dp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmUpdate(updateInfo) },
                shape = RoundedCornerShape(50),
                enabled = downloadProgress < 0 || downloadProgress == 100,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (downloadProgress in 0..99) "Downloading..." else "Update Now")
            }
        },
        dismissButton = {
            if (downloadProgress < 0) {
                OutlinedButton(
                    onClick = onDismissUpdate,
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Stay on Current Version")
                }
            }
        }
    )
}

@Composable
fun WebImageSearchDialog(
    show: Boolean,
    state: EarbudState,
    customBitmap: Bitmap?,
    isFetchingImage: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val defaultSearchQuery = if (state.name.isNotBlank() && state.name != "Wireless Earbuds") {
        "${state.name} earbuds"
    } else {
        "AirPods Pro earbuds"
    }
    var queryText by remember { mutableStateOf(defaultSearchQuery) }

    AlertDialog(
        onDismissRequest = { if (!isFetchingImage) onDismiss() },
        shape = RoundedCornerShape(28.dp),
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isFetchingImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        M3LoadingIndicator(size = 22.dp)
                        Spacer(modifier = Modifier.width(10.dp))
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
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(50),
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
                            onDismiss()
                            Toast.makeText(context, "Reset to default model icon", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isFetchingImage
                    ) {
                        Text("Reset Icon", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isFetchingImage
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun BalancePermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val command = "adb shell pm grant me.anypods.app android.permission.WRITE_SECURE_SETTINGS"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text("System Balance Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To modify Android's system-wide stereo audio balance, Android requires granting the Secure Settings permission once via ADB:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = command,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    "After running this command on your computer connected via USB, system balance works permanently without root.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(command))
                    Toast.makeText(context, "ADB command copied to clipboard!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(50)
            ) {
                Text("Copy ADB Command")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DiagnosticConsentDialog(
    show: Boolean,
    onConfirm: (Boolean) -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = { onConfirm(false) },
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📊", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Help Improve AnyPods", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Would you like to share anonymous diagnostic logs? Sharing helps developers tune frequency curves, fix connection bugs, and support your specific phone and earbuds better.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔒 Privacy-First Guarantee:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Includes: Device model, Android OS version, detected AirPods model, audio frequencies & EQ bands.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Excludes: MAC addresses are masked. NEVER collects names, accounts, locations, or audio recordings.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "You can change this anytime under Diagnostics & Feedback.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(true) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Agree & Share Diagnostics")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onConfirm(false) },
                shape = RoundedCornerShape(50)
            ) {
                Text("No Thanks")
            }
        }
    )
}

@Composable
fun DiagnosticReportDialog(
    show: Boolean,
    reportText: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diagnostic Report", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Anonymous",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This report contains only technical audio and device configuration details:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = reportText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(reportText))
                        Toast.makeText(context, "Diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Copy")
                }
                Button(
                    onClick = {
                        onShare()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Share Logs")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
