package me.anypods.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import me.anypods.app.R
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode
import me.anypods.app.bluetooth.BluetoothTracker

object DynamicIslandOverlay {
    private var windowManager: WindowManager? = null
    private var islandView: View? = null
    private var isExpanded = false
    private var isShowing = false

    @SuppressLint("InflateParams")
    fun show(context: Context) {
        if (isShowing) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 12 // Positioned directly over/under the camera punch hole
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.widget_background)
            setPadding(24, 16, 24, 16)
        }

        // Compact view row
        val compactRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pillIcon = TextView(context).apply {
            text = "🎧"
            textSize = 14f
        }

        val pillText = TextView(context).apply {
            text = " AnyPods • ANC"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val pillBattery = TextView(context).apply {
            val state = BluetoothTracker.earbudState.value
            text = " • ${if (state.leftBattery >= 0) state.leftBattery else 100}%"
            setTextColor(Color.parseColor("#34C759"))
            textSize = 12f
        }

        compactRow.addView(pillIcon)
        compactRow.addView(pillText)
        compactRow.addView(pillBattery)
        root.addView(compactRow)

        // Expanded Action Row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 16, 0, 8)
        }

        fun createPillButton(label: String, mode: NoiseMode): Button {
            return Button(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2C2C2E"))
                setOnClickListener {
                    NoiseControlManager.setMode(context, mode)
                    pillText.text = " AnyPods • ${mode.name.take(4)}"
                }
            }
        }

        actionRow.addView(createPillButton("ANC", NoiseMode.NOISE_CANCELLATION))
        actionRow.addView(createPillButton("Transp", NoiseMode.TRANSPARENCY))
        actionRow.addView(createPillButton("Off", NoiseMode.OFF))
        root.addView(actionRow)

        // Tap to toggle expand/collapse
        root.setOnClickListener {
            isExpanded = !isExpanded
            actionRow.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }

        islandView = root
        try {
            wm.addView(root, layoutParams)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (!isShowing || islandView == null) return
        try {
            windowManager?.removeView(islandView)
        } catch (_: Exception) {}
        islandView = null
        isShowing = false
        isExpanded = false
    }

    fun isEnabled(): Boolean = isShowing
}
