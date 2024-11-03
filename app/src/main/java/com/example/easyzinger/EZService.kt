package com.example.easyzinger

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class EZService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ezView: EZView

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        val pendingIntent = createStopServiceIntent()

        val notification = NotificationCompat.Builder(this, "easy_zinger_service_channel")
            .setContentTitle("Easy Zinger running")
            .setContentText("Tap notification to stop")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent) // Set the pending intent to stop the service
            .build()

        startForeground(1, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the overlay layout
        ezView = EZView(this, null)

        // Set the layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100 // Change this value to set the initial position

        // Add the view to the window
        windowManager.addView(ezView, params)
    }

    private fun createStopServiceIntent(): PendingIntent {
        val stopIntent = Intent(this, EZService::class.java).apply {
            action = "STOP_SERVICE"
        }
        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ezView.isInitialized) {
            windowManager.removeView(ezView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}