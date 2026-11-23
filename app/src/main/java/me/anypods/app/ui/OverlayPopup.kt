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

/**
 * Material 3 Expressive Floating Earbuds Pop-up Card
 * Features 32dp (84px) squircle containers, tonal pill chips, and M3 primary action buttons.
 */
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
                @Suppress("DEPRECATION")
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

            // Material 3 Expressive Surface Container (Extra-Large 84px / 32dp Squircle)
            background = GradientDrawable().apply {
                setColor(0xFFF7F9FD.toInt()) // M3 Surface Container Light
                cornerRadius = 84f
                setStroke(3, 0x1F006495) // Subtle M3 primary outline
            }
            elevation = 40f
        }

        // Title
        val titleText = TextView(context).apply {
            text = state.name
            textSize = 22f
            setTextColor(0xFF181C20.toInt()) // M3 OnSurface
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Subtitle / Status Pill (M3 Expressive Chip)
        val statusPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(if (state.isConnected) 0xFFCBE6FF.toInt() else 0xFFFFDAD6.toInt()) // M3 PrimaryContainer / ErrorContainer
                cornerRadius = 48f
            }
            setPadding(32, 10, 32, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 20)
            }
        }
        val statusText = TextView(context).apply {
            text = if (state.isConnected) "● Connected • Ready" else "○ Searching for Buds..."
            textSize = 12f
            setTextColor(if (state.isConnected) 0xFF001E31.toInt() else 0xFF410002.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        statusPill.addView(statusText)
        container.addView(statusPill)

        // Center Earbuds Graphic (Dynamic for all models & auto web-fetched photos)
        val modelInfo = me.anypods.app.model.PodModelManager.getSelectedModel(context, state.name)
        val customBitmap = me.anypods.app.model.PodImageManager.getDeviceBitmap(context, state.name)
        val earbudIcon = ImageView(context).apply {
            if (customBitmap != null) {
                setImageBitmap(customBitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                setImageResource(modelInfo.imageRes)
            }
            layoutParams = LinearLayout.LayoutParams(400, 300).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(earbudIcon)

        // Battery Row (Left, Right, Case) with M3 Expressive Pill Chips
        val batteryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 28)
        }

        val leftPct = (if (state.leftBattery >= 0) "${state.leftBattery}%" else "--%") + (if (state.isLeftCharging) " ⚡" else "")
        val rightPct = (if (state.rightBattery >= 0) "${state.rightBattery}%" else "--%") + (if (state.isRightCharging) " ⚡" else "")
        val casePct = (if (state.caseBattery >= 0) "${state.caseBattery}%" else "--%") + (if (state.isCaseCharging) " ⚡" else "")

        batteryRow.addView(createBatteryChip(context, "Left", leftPct, state.isLeftCharging))
        batteryRow.addView(createBatteryChip(context, "Case", casePct, state.isCaseCharging))
        batteryRow.addView(createBatteryChip(context, "Right", rightPct, state.isRightCharging))

        container.addView(batteryRow)

        // Done button (Material 3 Expressive Pill Button)
        val doneBtn = Button(context).apply {
            text = "Done"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF006495.toInt()) // M3 Primary Cyan-Blue
                cornerRadius = 64f // Pill shape
            }
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            ).apply {
                setMargins(24, 4, 24, 0)
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
            background = GradientDrawable().apply {
                setColor(0xFFEBEFF3.toInt()) // M3 SurfaceContainer Light
                cornerRadius = 40f
            }
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 0, 10, 0)
            }

            val valTxt = TextView(context).apply {
                text = value
                textSize = 17f
                setTextColor(if (isCharging) 0xFF006495.toInt() else 0xFF181C20.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val lblTxt = TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(0xFF41474D.toInt()) // M3 OnSurfaceVariant
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
