package me.anypods.app.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File

class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_STATUS = "me.anypods.app.updater.INSTALL_STATUS"
        private const val TAG = "AnyPods.InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No status message"

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Requesting user confirmation for in-app update")
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "AnyPods updated successfully via in-app installer")
                Toast.makeText(context, "AnyPods updated successfully!", Toast.LENGTH_LONG).show()
                // Cleanup temp update file
                try {
                    File(context.cacheDir, "anypods-update.apk").delete()
                } catch (_: Exception) {}
            }
            else -> {
                Log.e(TAG, "PackageInstaller session failed (status=$status): $message")
                // If package installer session returned failure, fall back to standard FileProvider intent
                val apkFile = File(context.cacheDir, "anypods-update.apk")
                if (apkFile.exists()) {
                    Log.i(TAG, "Falling back to FileProvider intent installer")
                    UpdateManager.installViaIntent(context, apkFile)
                } else {
                    Toast.makeText(context, "Update install error: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
