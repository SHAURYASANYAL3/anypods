package me.anypods.app.updater

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.anypods.app.MainActivity
import me.anypods.app.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateManager {
    private const val TAG = "AnyPods.UpdateManager"
    private const val GITHUB_REPO = "SHAURYASANYAL3/anypods"
    private const val CHANNEL_ID = "anypods_updates"

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1) // -1 = idle, 0..100 = downloading
    val downloadProgress = _downloadProgress.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun checkForUpdates(context: Context, isManual: Boolean = false) {
        if (_isChecking.value) return
        _isChecking.value = true

        scope.launch {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "AnyPods-App")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    val tagName = json.optString("tag_name", "")
                    val releaseNotes = json.optString("body", "Bug fixes and improvements.")
                    val assets = json.optJSONArray("assets")

                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    val currentVersion = getCurrentVersionName(context)
                    val isNewer = isVersionNewer(tagName, currentVersion)

                    Log.d(TAG, "Current: $currentVersion, Latest: $tagName, isNewer: $isNewer, url: $apkUrl")

                    if (isNewer && !apkUrl.isNullOrEmpty()) {
                        val update = UpdateInfo(
                            tagName = tagName,
                            downloadUrl = apkUrl,
                            releaseNotes = releaseNotes
                        )
                        _updateAvailable.value = update
                        showUpdateNotification(context, update)
                    } else if (isManual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "You are using the latest version ($currentVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No releases found on GitHub", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not reach GitHub: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
        _downloadProgress.value = -1
    }

    fun downloadAndInstall(activity: Activity, updateInfo: UpdateInfo) {
        if (_downloadProgress.value in 0..100) return

        // Check install permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
                Toast.makeText(activity, "Please allow AnyPods to install update packages", Toast.LENGTH_LONG).show()
                return
            }
        }

        _downloadProgress.value = 0

        scope.launch {
            try {
                val apkFile = File(activity.cacheDir, "anypods-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(updateInfo.downloadUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AnyPods-App")
                }

                // Handle HTTP redirects (GitHub Releases redirect to S3/CDN)
                var finalConn = conn
                var code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    val newUrl = conn.getHeaderField("Location")
                    finalConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "AnyPods-App")
                    }
                }

                val totalLength = finalConn.contentLength
                var downloaded = 0

                finalConn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                val pct = (downloaded * 100L / totalLength).toInt()
                                _downloadProgress.value = pct
                            }
                        }
                    }
                }

                _downloadProgress.value = 100
                Log.d(TAG, "Update downloaded successfully: ${apkFile.length()} bytes")

                withContext(Dispatchers.Main) {
                    installApk(activity, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _downloadProgress.value = -1
                    Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package installer: ${e.message}")
            Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateNotification(context: Context, update: UpdateInfo) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new AnyPods Pro updates on GitHub"
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle("New AnyPods Update Available (${update.tagName})")
            .setContentText("Tap to update or stay on the current version.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("A new version (${update.tagName}) is ready:\n${update.releaseNotes}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(1001, notification)
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()

        if (cleanLatest == cleanCurrent) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
