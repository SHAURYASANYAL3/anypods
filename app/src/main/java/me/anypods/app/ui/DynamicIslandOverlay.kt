package me.anypods.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import me.anypods.app.R
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode
import me.anypods.app.bluetooth.BluetoothTracker
import me.anypods.app.model.PodModelManager

object DynamicIslandOverlay {
    private var windowManager: WindowManager? = null
    private var islandView: View? = null
    private var isExpanded = false
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (36 * context.resources.displayMetrics.density).toInt()
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun show(context: Context) {
        handler.post {
            if (isShowing) return@post
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return@post
            }

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val prefs = context.getSharedPreferences("anypods_island", Context.MODE_PRIVATE)
            val defaultY = getStatusBarHeight(context) + (10 * context.resources.displayMetrics.density).toInt()
            val savedY = prefs.getInt("island_y", defaultY)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = savedY
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.widget_background)
                setPadding(28, 18, 28, 18)
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

            val state = BluetoothTracker.earbudState.value
            val modelName = PodModelManager.getModelById(state.modelId).name

            val pillText = TextView(context).apply {
                text = " $modelName • ANC"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val pillBattery = TextView(context).apply {
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
                        pillText.text = " $modelName • ${mode.name.take(4)}"
                    }
                }
            }

            actionRow.addView(createPillButton("ANC", NoiseMode.NOISE_CANCELLATION))
            actionRow.addView(createPillButton("Transp", NoiseMode.TRANSPARENCY))
            actionRow.addView(createPillButton("Off", NoiseMode.OFF))
            root.addView(actionRow)

            // Drag to reposition vertically so it never collides with any camera cutout
            var touchStartY = 0f
            var initialY = 0
            var isDragging = false

            root.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = event.rawY
                        initialY = layoutParams.y
                        isDragging = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - touchStartY).toInt()
                        if (kotlin.math.abs(deltaY) > 8) {
                            isDragging = true
                            layoutParams.y = (initialY + deltaY).coerceAtLeast(0)
                            try {
                                wm.updateViewLayout(root, layoutParams)
                            } catch (_: Exception) {}
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            prefs.edit().putInt("island_y", layoutParams.y).apply()
                            true
                        } else {
                            isExpanded = !isExpanded
                            actionRow.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            true
                        }
                    }
                    else -> false
                }
            }

            islandView = root
            try {
                wm.addView(root, layoutParams)
                isShowing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        handler.post {
            if (!isShowing || islandView == null) return@post
            try {
                windowManager?.removeView(islandView)
            } catch (_: Exception) {}
            islandView = null
            isShowing = false
            isExpanded = false
        }
    }

    fun isEnabled(): Boolean = isShowing
}
