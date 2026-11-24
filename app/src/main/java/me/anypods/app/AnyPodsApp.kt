package me.anypods.app

import android.app.Application
import android.content.Intent
import android.os.Build
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.diagnostics.DiagnosticManager
import me.anypods.app.service.EarbudService

class AnyPodsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticManager.init(this)
        BluetoothTracker.init(this)
        
        // Start monitoring service safely if permissions are already present
        EarbudService.startServiceSafely(this)
    }
}
