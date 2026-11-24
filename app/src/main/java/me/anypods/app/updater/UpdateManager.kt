package me.anypods.app.updater

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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
    private const val PREFS_NAME = "anypods_updater_prefs"
    private const val KEY_AUTO_UPDATE = "auto_update_enabled"

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1) // -1 = idle, 0..100 = downloading
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(true)
    val autoUpdateEnabled = _autoUpdateEnabled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _autoUpdateEnabled.value = prefs.getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun checkForUpdates(context: Context, isManual: Boolean = false, autoDownloadIfFound: Boolean = false) {
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
                            if (name.equals("app-release.apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            } else if (apkUrl == null && name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
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

                        val shouldAutoDownload = autoDownloadIfFound || _autoUpdateEnabled.value
                        if (shouldAutoDownload) {
                            Log.d(TAG, "Auto-downloading newer update: ${update.tagName}")
                            downloadAndInstall(context, update, silentAutoInstall = true)
                        } else {
                            showUpdateNotification(context, update, isDownloaded = false)
                        }
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

    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo, silentAutoInstall: Boolean = false) {
        if (_downloadProgress.value in 0..100) return

        // Verify install permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                if (context is Activity) {
                    Toast.makeText(context, "Please allow AnyPods to install update packages", Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        _downloadProgress.value = 0

        scope.launch {
            try {
                val apkFile = File(context.cacheDir, "anypods-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(updateInfo.downloadUrl)
                var conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AnyPods-App")
                }

                // Handle HTTP redirects (GitHub Releases 302/307 to AWS S3)
                var code = conn.responseCode
                var redirects = 0
                while ((code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) && redirects < 5) {
                    val redirectUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "AnyPods-App")
                    }
                    code = conn.responseCode
                    redirects++
                }

                val totalLength = conn.contentLength
                var downloaded = 0

                conn.inputStream.use { input ->
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

                // Verify downloaded APK integrity
                val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (archiveInfo == null || archiveInfo.packageName != context.packageName) {
                    Log.e(TAG, "Downloaded file is corrupted or belongs to another package: ${archiveInfo?.packageName}")
                    apkFile.delete()
                    _downloadProgress.value = -1
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Downloaded update is invalid", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                showUpdateNotification(context, updateInfo, isDownloaded = true)

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _downloadProgress.value = -1
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "APK file missing or invalid", Toast.LENGTH_SHORT).show()
            return
        }

        // Attempt modern PackageInstaller Session first (supports seamless in-app upgrades)
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session.openWrite("anypods_update", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            Log.d(TAG, "Committed PackageInstaller session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller session failed (${e.message}), falling back to Intent", e)
            installViaIntent(context, apkFile)
        }
    }

    fun installViaIntent(context: Context, apkFile: File) {
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
            Log.e(TAG, "Failed launching package installer intent: ${e.message}")
            Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateNotification(context: Context, update: UpdateInfo, isDownloaded: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for AnyPods Pro updates"
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_UPDATE_TAG", update.tagName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = if (isDownloaded) {
            "AnyPods ${update.tagName} Ready to Install"
        } else {
            "New AnyPods Update Available (${update.tagName})"
        }

        val content = if (isDownloaded) {
            "Tap to complete the update now."
        } else {
            "Tap to download and update to version ${update.tagName}."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n${update.releaseNotes}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(1001, notification)
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.3.1"
        } catch (e: Exception) {
            "1.3.1"
        }
    }

    internal fun isVersionNewer(latest: String, current: String): Boolean {
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
