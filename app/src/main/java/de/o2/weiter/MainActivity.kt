package de.o2.weiter

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    // Palette
    private val bgTop = 0xFF0A1022.toInt()
    private val bgBottom = 0xFF04060C.toInt()
    private val panel = 0xCC0B1324.toInt()
    private val cyan = 0xFF00E5FF.toInt()
    private val magenta = 0xFFFF2BD6.toInt()
    private val green = 0xFF39FF88.toInt()
    private val amber = 0xFFFFB020.toInt()
    private val red = 0xFFFF4D6D.toInt()
    private val dim = 0xFF6B7A99.toInt()
    private val text = 0xFFE6F1FF.toInt()
    private val logGreen = 0xFF8CFFC1.toInt()

    private val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val mono: Typeface = Typeface.MONOSPACE

    private lateinit var store: Store
    private lateinit var subtitle: TextView
    private lateinit var halo: View
    private lateinit var ring: View
    private lateinit var countView: TextView
    private lateinit var gbView: TextView
    private lateinit var armBtn: TextView
    private lateinit var smsValue: TextView
    private lateinit var powerValue: TextView
    private lateinit var autoValue: TextView
    private lateinit var permBtn: TextView
    private lateinit var batteryBtn: TextView
    private lateinit var logView: TextView
    private var pulse: ObjectAnimator? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // ── Header ─────────────────────────────────────────────
        root.addView(label("O2 // AUTO-WEITER", 24f, cyan, condensed).apply {
            letterSpacing = 0.12f
            // Room around the text so the glow isn't clipped into a visible box.
            setPadding(0, dp(10), dp(12), dp(6))
            glow(cyan)
        })
        subtitle = label("", 11f, dim, mono).apply {
            letterSpacing = 0.2f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(subtitle)

        // ── Reactor core ───────────────────────────────────────
        root.addView(buildCore(), LinearLayout.LayoutParams(MATCH_PARENT, dp(270)))

        // ── Arm / disarm ───────────────────────────────────────
        armBtn = label("", 15f, green, condensed).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setOnClickListener { store.enabled = !store.enabled; refresh() }
        }
        root.addView(armBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(58)).apply { topMargin = dp(8) })

        // ── System status ──────────────────────────────────────
        val status = card()
        status.addView(sectionTitle("// SYSTEM STATUS"))
        smsValue = statusRow(status, "SMS LINK")
        powerValue = statusRow(status, "POWER")
        autoValue = statusRow(status, "AUTO-REPLY")
        statusRow(status, "TARGET").apply { text = "80112 → WEITER"; setTextColor(cyan) }
        root.addView(status, cardParams())

        // ── Actions ────────────────────────────────────────────
        permBtn = neonButton("GRANT PERMISSIONS", amber) { requestPerms() }
        batteryBtn = neonButton("SET POWER: UNRESTRICTED", amber) { openBattery() }
        root.addView(permBtn, buttonParams())
        root.addView(batteryBtn, buttonParams())
        root.addView(neonButton("▶  SEND WEITER NOW", magenta) { confirmManualSend() }, buttonParams())

        // ── Event log ──────────────────────────────────────────
        val log = card()
        log.addView(sectionTitle("// EVENT LOG"))
        logView = label("", 11f, logGreen, mono).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        log.addView(logView)
        root.addView(log, cardParams())

        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgTop, bgBottom))
            isFillViewport = true
            clipToPadding = false
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
        // Edge-to-edge: keep content clear of the status and navigation bars.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars()); b.top to b.bottom
            } else {
                @Suppress("DEPRECATION") (insets.systemWindowInsetTop to insets.systemWindowInsetBottom)
            }
            v.setPadding(0, top, 0, bottom)
            insets
        }
        window.decorView.setBackgroundColor(bgBottom)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        pulse?.cancel()
    }

    // ── Core widget ────────────────────────────────────────────
    private fun buildCore(): FrameLayout {
        val frame = FrameLayout(this)

        halo = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(125).toFloat()
                colors = intArrayOf(0x5500E5FF, 0x0000E5FF)
            }
        }
        frame.addView(halo, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))

        ring = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x1400E5FF)
                setStroke(dp(2), cyan)
            }
            elevation = dp(16).toFloat()
            if (Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = cyan; outlineAmbientShadowColor = cyan }
        }
        frame.addView(ring, FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER))

        val inner = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0x88FF2BD6.toInt(), dp(6).toFloat(), dp(5).toFloat())
            }
            elevation = dp(16).toFloat()
        }
        frame.addView(inner, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
        // Slowly rotating dashed inner ring.
        ObjectAnimator.ofFloat(inner, View.ROTATION, 0f, 360f).apply {
            duration = 20_000; repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            elevation = dp(17).toFloat()
        }
        countView = label("0", 64f, text, condensed).apply { gravity = Gravity.CENTER; glow(cyan) }
        center.addView(countView)
        center.addView(label("TOP-UPS TODAY", 10f, dim, mono).apply { letterSpacing = 0.25f; gravity = Gravity.CENTER })
        gbView = label("", 12f, magenta, mono).apply {
            letterSpacing = 0.1f; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0); glow(magenta)
        }
        center.addView(gbView)
        frame.addView(center, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        return frame
    }

    private fun setPulsing(on: Boolean) {
        if (on && pulse?.isRunning != true) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                halo,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.06f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.06f),
            ).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        } else if (!on) {
            pulse?.cancel()
            halo.alpha = 0.15f; halo.scaleX = 1f; halo.scaleY = 1f
        }
    }

    // ── Building blocks ────────────────────────────────────────
    private fun label(s: String, size: Float, color: Int, face: Typeface) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); typeface = face
    }

    // Small radius: larger blurs get clipped to the text bounds and show up as a faint box.
    private fun TextView.glow(color: Int) = setShadowLayer(dp(4).toFloat(), 0f, 0f, color)

    private fun sectionTitle(s: String) = label(s, 11f, cyan, mono).apply {
        letterSpacing = 0.2f
        setPadding(0, 0, 0, dp(10))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(panel)
            setStroke(dp(1), 0x4400E5FF)
        }
    }

    private fun cardParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) }
    private fun buttonParams() = LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(10) }

    private fun statusRow(parent: LinearLayout, name: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(label(name, 12f, dim, mono).apply { letterSpacing = 0.1f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val value = label("", 12f, text, mono).apply { letterSpacing = 0.1f }
        row.addView(value)
        parent.addView(row)
        return value
    }

    private fun neonBackground(color: Int, fillAlpha: Int): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(dp(1), color)
        }
        val ripple = ColorStateList.valueOf(Color.argb(0x55, Color.red(color), Color.green(color), Color.blue(color)))
        return RippleDrawable(ripple, shape, null)
    }

    private fun neonButton(s: String, color: Int, onClick: () -> Unit) = label(s, 13f, color, mono).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.15f
        setTypeface(mono, Typeface.BOLD)
        background = neonBackground(color, 0x10)
        isClickable = true
        setOnClickListener { onClick() }
    }

    // ── Behaviour ──────────────────────────────────────────────
    private fun confirmManualSend() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Send WEITER?")
            .setMessage("Request another 2 GB from o2 now by texting WEITER to 80112.")
            .setPositiveButton("Send") { _, _ ->
                val ok = SmsReceiver.sendWeiter(this)
                if (ok) { store.lastSentAt = System.currentTimeMillis(); store.incrementToday() }
                store.log(if (ok) "Sent WEITER (manual)" else "FAILED to send WEITER (manual)")
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun neededPerms(): Array<String> {
        val p = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        return p.toTypedArray()
    }

    private fun missingPerms() = neededPerms().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun requestPerms() {
        val missing = missingPerms()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun openBattery() {
        val pm = getSystemService(PowerManager::class.java)
        val intent = if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try { startActivity(intent) } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refresh() {
        val permsOk = missingPerms().isEmpty()
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val armed = store.enabled
        val live = armed && permsOk

        subtitle.text = if (live) "LINK 80112 · MONITORING" else "LINK 80112 · STANDBY"
        subtitle.setTextColor(if (live) green else dim)

        val n = store.todayCount
        countView.text = n.toString()
        gbView.text = if (n == 0) "NO BOOST YET" else "+${n * 2} GB HIGHSPEED"

        armBtn.text = if (armed) "◉  ARMED · AUTO-REPLY ACTIVE" else "○  DISARMED · TAP TO ARM"
        armBtn.setTextColor(if (armed) green else red)
        armBtn.background = neonBackground(if (armed) green else red, 0x1A)
        if (armed) armBtn.glow(green) else armBtn.setShadowLayer(0f, 0f, 0f, 0)

        fun set(v: TextView, ok: Boolean, good: String, bad: String, badColor: Int = amber) {
            v.text = "● " + if (ok) good else bad
            v.setTextColor(if (ok) green else badColor)
        }
        set(smsValue, permsOk, "ONLINE", "NO PERMISSION", red)
        set(powerValue, batteryOk, "UNRESTRICTED", "OPTIMIZED")
        set(autoValue, armed, "ARMED", "OFF", red)

        permBtn.visibility = if (permsOk) View.GONE else View.VISIBLE
        batteryBtn.visibility = if (batteryOk) View.GONE else View.VISIBLE

        ring.alpha = if (live) 1f else 0.4f
        setPulsing(live)

        val lines = store.logLines()
        logView.text = if (lines.isEmpty()) "› awaiting signal from 80112_" else lines.joinToString("\n") { "› $it" }
    }
}
