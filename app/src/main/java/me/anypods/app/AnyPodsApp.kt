package me.anypods.app

import android.app.Application
import android.content.Intent
import android.os.Build
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.service.EarbudService

class AnyPodsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BluetoothTracker.init(this)
        
        // Start monitoring service
        val intent = Intent(this, EarbudService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
