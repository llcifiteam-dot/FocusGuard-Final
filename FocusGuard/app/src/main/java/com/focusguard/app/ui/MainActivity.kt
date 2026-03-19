package com.focusguard.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.focusguard.app.R
import com.focusguard.app.data.StorageManager
import com.focusguard.app.services.FocusAccessibilityService
import com.focusguard.app.services.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var storage: StorageManager
    private var currentTab = "home"

    // Views
    private lateinit var tabHome: TextView
    private lateinit var tabInsights: TextView
    private lateinit var tabSettings: TextView

    private lateinit var screenHome: View
    private lateinit var screenInsights: View
    private lateinit var screenSettings: View

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runOnUiThread { updateHomeStats() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = StorageManager(this)

        setupTabs()
        setupHomeScreen()
        setupInsightsScreen()
        setupSettingsScreen()
        showTab("home")
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, IntentFilter("com.focusguard.SCAN_COMPLETE"),
            RECEIVER_NOT_EXPORTED)
        updateHomeStats()
        updateShieldUI()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (e: Exception) {}
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private fun setupTabs() {
        tabHome = findViewById(R.id.tab_home)
        tabInsights = findViewById(R.id.tab_insights)
        tabSettings = findViewById(R.id.tab_settings)
        screenHome = findViewById(R.id.screen_home)
        screenInsights = findViewById(R.id.screen_insights)
        screenSettings = findViewById(R.id.screen_settings)

        tabHome.setOnClickListener { showTab("home") }
        tabInsights.setOnClickListener { showTab("insights"); renderInsights() }
        tabSettings.setOnClickListener { showTab("settings") }
    }

    private fun showTab(tab: String) {
        currentTab = tab
        screenHome.visibility = if (tab == "home") View.VISIBLE else View.GONE
        screenInsights.visibility = if (tab == "insights") View.VISIBLE else View.GONE
        screenSettings.visibility = if (tab == "settings") View.VISIBLE else View.GONE

        val active = ContextCompat.getColor(this, R.color.green)
        val inactive = ContextCompat.getColor(this, R.color.muted)
        tabHome.setTextColor(if (tab == "home") active else inactive)
        tabInsights.setTextColor(if (tab == "insights") active else inactive)
        tabSettings.setTextColor(if (tab == "settings") active else inactive)
    }

    // ── Home Screen ───────────────────────────────────────────────────────────
    private fun setupHomeScreen() {
        val shieldCard = findViewById<LinearLayout>(R.id.shield_card)
        shieldCard.setOnClickListener { toggleShield() }

        val focusModeBtn = findViewById<Button>(R.id.btn_focus_mode)
        focusModeBtn.setOnClickListener {
            storage.focusMode = !storage.focusMode
            focusModeBtn.text = if (storage.focusMode) "ON" else "OFF"
            focusModeBtn.setBackgroundColor(
                if (storage.focusMode) getColor(R.color.green) else getColor(R.color.surface)
            )
            Toast.makeText(this,
                if (storage.focusMode) "🎯 Deep Focus Mode ON" else "😌 Deep Focus Mode OFF",
                Toast.LENGTH_SHORT).show()
        }
        updateHomeStats()
    }

    private fun toggleShield() {
        if (storage.apiKey.isEmpty()) {
            Toast.makeText(this, "Please add your API key in Settings first!", Toast.LENGTH_LONG).show()
            showTab("settings")
            return
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityDialog()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
            return
        }

        storage.shieldActive = !storage.shieldActive
        val service = FocusAccessibilityService.instance
        if (storage.shieldActive) {
            service?.startScanLoop()
        } else {
            service?.stopScanLoop()
        }
        updateShieldUI()
    }

    private fun updateShieldUI() {
        val isActive = storage.shieldActive && isAccessibilityEnabled()
        val shieldCard = findViewById<LinearLayout>(R.id.shield_card)
        val statusDot = findViewById<View>(R.id.shield_dot)
        val statusText = findViewById<TextView>(R.id.tv_shield_status)
        val subText = findViewById<TextView>(R.id.tv_shield_sub)

        if (isActive) {
            shieldCard.setBackgroundResource(R.drawable.bg_shield_active)
            statusDot.setBackgroundResource(R.drawable.circle_green)
            statusText.text = "Shield Active"
            statusText.setTextColor(getColor(R.color.green))
            subText.text = "Scanning every ${storage.scanIntervalSeconds}s — AI watching your screen"
        } else {
            shieldCard.setBackgroundResource(R.drawable.bg_shield_inactive)
            statusDot.setBackgroundResource(R.drawable.circle_red)
            statusText.text = "Shield Paused"
            statusText.setTextColor(getColor(R.color.red))
            subText.text = "Tap to start AI monitoring"
        }
    }

    private fun updateHomeStats() {
        val profile = storage.getProfile()
        val score = if (profile.totalScans > 0) (100 - profile.avgScore).toInt().coerceIn(0, 100) else 0

        val scoreColor = when {
            score >= 70 -> getColor(R.color.green)
            score >= 45 -> getColor(R.color.yellow)
            else -> getColor(R.color.red)
        }

        findViewById<TextView>(R.id.tv_focus_score).apply {
            text = if (profile.totalScans > 0) "$score" else "--"
            setTextColor(scoreColor)
        }
        findViewById<TextView>(R.id.tv_stat_scans).text = "${storage.totalScansToday}"
        findViewById<TextView>(R.id.tv_stat_blocks).text = "${storage.totalBlocks}"
        findViewById<TextView>(R.id.tv_streak).text = "${profile.focusStreak}🔥"

        val learningPct = when {
            profile.totalScans < 3 -> 10
            profile.totalScans < 10 -> 30
            profile.totalScans < 30 -> 60
            profile.totalScans < 100 -> 85
            else -> 97
        }
        val learningLabel = when {
            profile.totalScans < 3 -> "Just started — needs more data"
            profile.totalScans < 10 -> "Learning your patterns..."
            profile.totalScans < 30 -> "Building your behavior model"
            profile.totalScans < 100 -> "Personalized to you"
            else -> "Deep behavior model active"
        }
        val learningLevel = when {
            profile.totalScans < 10 -> "LEARNING"
            profile.totalScans < 30 -> "ADAPTING"
            profile.totalScans < 100 -> "SMART"
            else -> "EXPERT"
        }

        findViewById<ProgressBar>(R.id.progress_learning).progress = learningPct
        findViewById<TextView>(R.id.tv_learning_label).text = learningLabel
        findViewById<TextView>(R.id.tv_learning_level).text = learningLevel

        // Last scan result
        val history = storage.getScanHistory()
        if (history.isNotEmpty()) {
            val last = history[0]
            val vc = when (last.verdict) {
                "DISTRACTION" -> getColor(R.color.red)
                "FOCUSED" -> getColor(R.color.green)
                else -> getColor(R.color.yellow)
            }
            val emoji = when (last.verdict) { "DISTRACTION" -> "🚫"; "FOCUSED" -> "✅"; else -> "⚠️" }
            findViewById<TextView>(R.id.tv_last_verdict).apply { text = "$emoji ${last.verdict}"; setTextColor(vc) }
            findViewById<TextView>(R.id.tv_last_score).apply { text = "${last.score}/100"; setTextColor(vc) }
            findViewById<TextView>(R.id.tv_last_reason).text = last.reason
            findViewById<View>(R.id.last_result_card).visibility = View.VISIBLE
        }

        // Week chart
        renderWeekChart()
    }

    private fun renderWeekChart() {
        val chartContainer = findViewById<LinearLayout>(R.id.chart_container) ?: return
        chartContainer.removeAllViews()
        val weekStats = storage.getWeeklyStats()
        weekStats.forEach { (day, score) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            val barHeight = if (score != null) (score * 1.0f).toInt().coerceIn(4, 100) else 4
            val color = when {
                score == null -> getColor(R.color.border)
                score >= 70 -> getColor(R.color.green)
                score >= 45 -> getColor(R.color.yellow)
                else -> getColor(R.color.red)
            }
            if (score != null) {
                val valTv = TextView(this).apply {
                    text = "$score"; textSize = 8f; setTextColor(color)
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                col.addView(valTv)
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight).apply {
                    setMargins(4, 0, 4, 0)
                }
                setBackgroundColor(color)
                // rounded
            }
            col.addView(bar)
            val label = TextView(this).apply {
                text = day; textSize = 8f
                setTextColor(getColor(R.color.muted))
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            col.addView(label)
            chartContainer.addView(col)
        }
    }

    // ── Insights Screen ────────────────────────────────────────────────────────
    private fun setupInsightsScreen() {}

    private fun renderInsights() {
        val profile = storage.getProfile()
        val history = storage.getScanHistory()

        // Top apps
        val appsContainer = findViewById<LinearLayout>(R.id.apps_container)
        appsContainer.removeAllViews()
        profile.topApps.filter { it.avgScore > 50 }.take(5).forEach { app ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_app_row, appsContainer, false)
            row.findViewById<TextView>(R.id.tv_app_name).text = app.app
            row.findViewById<TextView>(R.id.tv_app_score).text = app.avgScore.toInt().toString()
            row.findViewById<ProgressBar>(R.id.progress_app).progress = app.avgScore.toInt()
            row.findViewById<TextView>(R.id.tv_app_count).text = "${app.count}x"
            appsContainer.addView(row)
        }

        // Recent history
        val histContainer = findViewById<LinearLayout>(R.id.history_container)
        histContainer.removeAllViews()
        history.take(15).forEach { scan ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_history_row, histContainer, false)
            val color = when (scan.verdict) {
                "DISTRACTION" -> getColor(R.color.red)
                "FOCUSED" -> getColor(R.color.green)
                else -> getColor(R.color.yellow)
            }
            row.findViewById<View>(R.id.verdict_dot).setBackgroundColor(color)
            row.findViewById<TextView>(R.id.tv_hist_app).text = scan.appName
            row.findViewById<TextView>(R.id.tv_hist_reason).text = scan.reason
            row.findViewById<TextView>(R.id.tv_hist_score).apply { text = "${scan.score}"; setTextColor(color) }
            row.findViewById<TextView>(R.id.tv_hist_time).text =
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(scan.timestamp))
            histContainer.addView(row)
        }

        // Stats
        val trend = profile.weeklyTrend
        val trendText = when (trend) { "IMPROVING" -> "📈 Improving"; "DECLINING" -> "📉 Declining"; else -> "➡️ Stable" }
        val trendColor = when (trend) { "IMPROVING" -> getColor(R.color.green); "DECLINING" -> getColor(R.color.red); else -> getColor(R.color.yellow) }
        findViewById<TextView>(R.id.tv_trend).apply { text = trendText; setTextColor(trendColor) }
        findViewById<TextView>(R.id.tv_total_scans).text = "${profile.totalScans}"
        findViewById<TextView>(R.id.tv_avg_score).text = "${profile.avgScore.toInt()}"
        findViewById<TextView>(R.id.tv_override_rate).text = "${profile.overrideRate}%"
    }

    // ── Settings Screen ────────────────────────────────────────────────────────
    private fun setupSettingsScreen() {
        // API key
        val apiKeyInput = findViewById<EditText>(R.id.et_api_key)
        if (storage.apiKey.isNotEmpty()) {
            apiKeyInput.setText(storage.apiKey)
            updateKeyStatus(true)
        }
        findViewById<Button>(R.id.btn_save_key).setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            storage.apiKey = key
            updateKeyStatus(key.isNotEmpty())
            if (key.isNotEmpty()) Toast.makeText(this, "✅ API key saved!", Toast.LENGTH_SHORT).show()
        }

        // Scan interval pills
        listOf(
            Pair(R.id.pill_10s, 10), Pair(R.id.pill_20s, 20),
            Pair(R.id.pill_30s, 30), Pair(R.id.pill_60s, 60)
        ).forEach { (id, value) ->
            findViewById<Button>(id).apply {
                if (storage.scanIntervalSeconds == value) setBackgroundResource(R.drawable.pill_active)
                setOnClickListener {
                    storage.scanIntervalSeconds = value
                    updateIntervalPills(value)
                    FocusAccessibilityService.instance?.let {
                        if (storage.shieldActive) it.startScanLoop()
                    }
                }
            }
        }

        // Block toggle
        findViewById<Switch>(R.id.switch_blocking).apply {
            isChecked = storage.blockingEnabled
            setOnCheckedChangeListener { _, checked -> storage.blockingEnabled = checked }
        }

        // Permissions
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset All Data")
                .setMessage("Delete all scan history and behavior profile? This cannot be undone.")
                .setPositiveButton("Reset") { _, _ -> storage.resetAll(); updateHomeStats(); Toast.makeText(this, "Data cleared ✓", Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updatePermissionStatus()
    }

    private fun updateKeyStatus(valid: Boolean) {
        val tv = findViewById<TextView>(R.id.tv_key_status)
        tv.text = if (valid) "✓ API key saved" else "⚠ No API key — app won't work"
        tv.setTextColor(if (valid) getColor(R.color.green) else getColor(R.color.red))
    }

    private fun updateIntervalPills(active: Int) {
        mapOf(R.id.pill_10s to 10, R.id.pill_20s to 20, R.id.pill_30s to 30, R.id.pill_60s to 60).forEach { (id, v) ->
            findViewById<Button>(id).setBackgroundResource(
                if (v == active) R.drawable.pill_active else R.drawable.pill_inactive
            )
        }
    }

    private fun updatePermissionStatus() {
        val accOk = isAccessibilityEnabled()
        val overlayOk = Settings.canDrawOverlays(this)
        findViewById<TextView>(R.id.tv_accessibility_status).apply {
            text = if (accOk) "✓ Enabled" else "✗ Not enabled — tap to enable"
            setTextColor(if (accOk) getColor(R.color.green) else getColor(R.color.red))
        }
        findViewById<TextView>(R.id.tv_overlay_status).apply {
            text = if (overlayOk) "✓ Enabled" else "✗ Not enabled — tap to enable"
            setTextColor(if (overlayOk) getColor(R.color.green) else getColor(R.color.red))
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val serviceName = "${packageName}/${FocusAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }

    private fun checkPermissions() {
        if (!isAccessibilityEnabled() || !Settings.canDrawOverlays(this)) {
            showPermissionsWelcomeDialog()
        }
    }

    private fun showPermissionsWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("🛡️ Two Permissions Needed")
            .setMessage(
                "FocusGuard needs:\n\n" +
                "1️⃣ Accessibility Service — to READ your screen automatically\n\n" +
                "2️⃣ Display Over Other Apps — to BLOCK distracting apps\n\n" +
                "We'll guide you through both. Takes 30 seconds."
            )
            .setPositiveButton("Let's go!") { _, _ ->
                if (!isAccessibilityEnabled()) openAccessibilitySettings()
                else if (!Settings.canDrawOverlays(this)) openOverlaySettings()
            }
            .setCancelable(false)
            .show()
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "FocusGuard needs Accessibility Service to read your screen automatically.\n\n" +
                "Steps:\n" +
                "1. Tap 'Open Settings'\n" +
                "2. Find 'FocusGuard Screen Monitor'\n" +
                "3. Toggle it ON\n" +
                "4. Tap Allow"
            )
            .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Display Over Other Apps")
            .setMessage("FocusGuard needs this permission to show the block screen on top of distracting apps.\n\nTap 'Open Settings' then toggle ON.")
            .setPositiveButton("Open Settings") { _, _ -> openOverlaySettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")))
    }
}
