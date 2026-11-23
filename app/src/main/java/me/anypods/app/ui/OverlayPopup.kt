package me.anypods.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.anypods.app.R
import me.anypods.app.model.EarbudState

object OverlayPopup {
    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(context: Context, state: EarbudState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            // Cannot draw over other apps without overlay permission
            return
        }

        dismiss()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val wmLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80 // slight margin from bottom
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)

            // iOS-style white glass background
            background = GradientDrawable().apply {
                setColor(0xFAFFFFFF.toInt()) // Crisp white with slight opacity
                cornerRadius = 72f
                setStroke(2, 0x22000000)
            }
            elevation = 32f
        }

        // Title
        val titleText = TextView(context).apply {
            text = state.name
            textSize = 22f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        container.addView(titleText)

        // Subtitle / Status
        val statusText = TextView(context).apply {
            text = if (state.isConnected) "Connected" else "Connecting..."
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(statusText)

        // Center Earbuds Graphic (Real Apple AirPods Pro 2)
        val earbudIcon = ImageView(context).apply {
            setImageResource(R.drawable.airpods_pro_2_buds)
            layoutParams = LinearLayout.LayoutParams(400, 300).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(earbudIcon)

        // Battery Row (Left, Right, Case)
        val batteryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        val leftPct = (if (state.leftBattery >= 0) "${state.leftBattery}%" else "--%") + (if (state.isLeftCharging) " ⚡" else "")
        val rightPct = (if (state.rightBattery >= 0) "${state.rightBattery}%" else "--%") + (if (state.isRightCharging) " ⚡" else "")
        val casePct = (if (state.caseBattery >= 0) "${state.caseBattery}%" else "--%") + (if (state.isCaseCharging) " ⚡" else "")

        batteryRow.addView(createBatteryChip(context, "Left", leftPct, state.isLeftCharging))
        batteryRow.addView(createBatteryChip(context, "Case", casePct, state.isCaseCharging))
        batteryRow.addView(createBatteryChip(context, "Right", rightPct, state.isRightCharging))

        container.addView(batteryRow)

        // Done button
        val doneBtn = Button(context).apply {
            text = "Done"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF007AFF.toInt()) // Apple Blue
                cornerRadius = 48f
            }
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            ).apply {
                setMargins(24, 8, 24, 0)
            }
        }
        container.addView(doneBtn)

        // Wrapper with side margins
        val rootWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 0, 36, 0)
            addView(container)
        }

        try {
            wm.addView(rootWrapper, wmLayoutParams)
            popupView = rootWrapper

            // Spring in animation
            rootWrapper.translationY = 500f
            rootWrapper.alpha = 0f
            val animY = ObjectAnimator.ofFloat(rootWrapper, "translationY", 500f, 0f).apply {
                interpolator = OvershootInterpolator(1.2f)
                duration = 450
            }
            val animAlpha = ObjectAnimator.ofFloat(rootWrapper, "alpha", 0f, 1f).apply {
                duration = 300
            }
            AnimatorSet().apply {
                playTogether(animY, animAlpha)
                start()
            }

            // Auto-dismiss after 6 seconds
            dismissRunnable?.let { handler.removeCallbacks(it) }
            val run = Runnable { dismiss() }
            dismissRunnable = run
            handler.postDelayed(run, 6000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createBatteryChip(context: Context, label: String, value: String, isCharging: Boolean = false): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 12, 32, 12)

            val valTxt = TextView(context).apply {
                text = value
                textSize = 18f
                setTextColor(if (isCharging) 0xFF34C759.toInt() else 0xFF1C1C1E.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val lblTxt = TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(0xFF8E8E93.toInt())
            }
            addView(valTxt)
            addView(lblTxt)
        }
    }

    fun dismiss() {
        val pv = popupView ?: return
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        try {
            windowManager?.removeView(pv)
        } catch (e: Exception) {
            // Ignored
        }
        popupView = null
    }
}
