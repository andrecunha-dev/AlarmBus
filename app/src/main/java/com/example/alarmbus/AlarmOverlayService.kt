package com.example.alarmbus

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * Serviço responsável por exibir uma janela de sobreposição (overlay) quando o alarme é acionado.
 * 
 * Esta janela aparece sobre outros aplicativos para permitir que o usuário pare o alarme
 * rapidamente, mesmo que o aplicativo AlarmBus não esteja em primeiro plano.
 */
class AlarmOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Infla o layout do overlay
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_alarm, null)

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        val btnStopAlarm = overlayView.findViewById<Button>(R.id.btnStopAlarm)
        btnStopAlarm?.setOnClickListener {
            AlarmPlayer.stopAlarm(this@AlarmOverlayService)
            stopSelf()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized && ::windowManager.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
