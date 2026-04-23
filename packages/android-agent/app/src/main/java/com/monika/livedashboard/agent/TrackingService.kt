package com.monika.livedashboard.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsStore: SettingsStore

    private var trackingJob: Job? = null
    private var consentUploaded = false
    private var lastSentKey = ""

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settingsStore.setRunningEnabled(false)
                stopTracking()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startTrackingIfNeeded()
                return START_STICKY
            }

            else -> return START_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTrackingIfNeeded() {
        if (trackingJob?.isActive == true) return

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Preparing tracker")
        )

        trackingJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsStore.load()

                if (!settings.isRunningEnabled) {
                    delay(2_000)
                    continue
                }

                if (!settings.consentGiven || !settings.reportActivity) {
                    updateNotification("Consent required")
                    delay(5_000)
                    continue
                }

                if (!UsageTracker.hasUsageStatsPermission(this@TrackingService)) {
                    updateNotification("Usage access is not granted")
                    delay(5_000)
                    continue
                }

                if (!consentUploaded) {
                    consentUploaded = ApiReporter.postConsent(settings)
                }

                val appInfo = UsageTracker.currentForegroundApp(this@TrackingService)
                if (appInfo != null) {
                    val timeBucket = appInfo.timestampMs / 10_000L
                    val dedupKey = "${appInfo.packageName}:$timeBucket"
                    if (dedupKey != lastSentKey) {
                        val extras = DeviceContextProvider.readExtras(this@TrackingService)
                        val sent = ApiReporter.postReport(settings, appInfo, extras)
                        if (sent) {
                            lastSentKey = dedupKey
                            updateNotification("Reporting: ${appInfo.appName}")
                        } else {
                            updateNotification("Report failed, retrying")
                        }
                    }
                }

                delay(settings.heartbeatSeconds * 1_000L)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Dashboard Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Uploads foreground app status to Live Dashboard"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Live Dashboard Agent")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.monika.livedashboard.agent.action.START"
        const val ACTION_STOP = "com.monika.livedashboard.agent.action.STOP"

        private const val CHANNEL_ID = "live_dashboard_agent_channel"
        private const val NOTIFICATION_ID = 11031
    }
}
