package com.monika.livedashboard.agent

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max
import kotlin.math.min

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AgentSettings {
        val heartbeat = sanitizeHeartbeat(prefs.getInt(KEY_HEARTBEAT_SECONDS, 30))
        return AgentSettings(
            serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
            token = prefs.getString(KEY_TOKEN, "") ?: "",
            heartbeatSeconds = heartbeat,
            consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN, false),
            reportActivity = prefs.getBoolean(KEY_REPORT_ACTIVITY, true),
            reportBattery = prefs.getBoolean(KEY_REPORT_BATTERY, true),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START, false),
            isRunningEnabled = prefs.getBoolean(KEY_RUNNING_ENABLED, false)
        )
    }

    fun save(settings: AgentSettings) {
        prefs.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl.trim())
            .putString(KEY_TOKEN, settings.token.trim())
            .putInt(KEY_HEARTBEAT_SECONDS, sanitizeHeartbeat(settings.heartbeatSeconds))
            .putBoolean(KEY_CONSENT_GIVEN, settings.consentGiven)
            .putBoolean(KEY_REPORT_ACTIVITY, settings.reportActivity)
            .putBoolean(KEY_REPORT_BATTERY, settings.reportBattery)
            .putBoolean(KEY_AUTO_START, settings.autoStartOnBoot)
            .putBoolean(KEY_RUNNING_ENABLED, settings.isRunningEnabled)
            .apply()
    }

    fun setRunningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RUNNING_ENABLED, enabled).apply()
    }

    private fun sanitizeHeartbeat(value: Int): Int {
        return min(50, max(10, value))
    }

    companion object {
        private const val PREFS_NAME = "live_dashboard_agent"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_HEARTBEAT_SECONDS = "heartbeat_seconds"
        private const val KEY_CONSENT_GIVEN = "consent_given"
        private const val KEY_REPORT_ACTIVITY = "report_activity"
        private const val KEY_REPORT_BATTERY = "report_battery"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_RUNNING_ENABLED = "running_enabled"
    }
}
