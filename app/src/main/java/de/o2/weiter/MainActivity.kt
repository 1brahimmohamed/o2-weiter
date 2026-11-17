package de.o2.weiter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var toggle: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "o2 Auto-WEITER"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Replies WEITER to 80112 automatically when o2 says your high-speed data is (almost) used up."
            setPadding(0, pad / 2, 0, pad)
        })

        toggle = Switch(this).apply {
            text = "Auto-reply enabled"
            textSize = 18f
            isChecked = store.enabled
            setOnCheckedChangeListener { _, on -> store.enabled = on; refresh() }
        }
        root.addView(toggle)

        status = TextView(this).apply { setPadding(0, pad, 0, pad) }
        root.addView(status)

        root.addView(button("Grant permissions") { requestPerms() })
        root.addView(button("Battery: set Unrestricted") { openBattery() })
        root.addView(button("Send WEITER now") {
            AlertDialog.Builder(this)
                .setMessage("Send WEITER to 80112 now?")
                .setPositiveButton("Send") { _, _ ->
                    val ok = SmsReceiver.sendWeiter(this)
                    if (ok) { store.lastSentAt = System.currentTimeMillis(); store.incrementToday() }
                    store.log(if (ok) "Sent WEITER (manual)" else "FAILED to send WEITER (manual)")
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })

        root.addView(TextView(this).apply {
            text = "Log"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })
        logView = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f }
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
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
        val missing = missingPerms()
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        status.text = buildString {
            append(if (missing.isEmpty()) "✅ Permissions granted\n" else "⚠️ Missing: ${missing.joinToString { it.substringAfterLast('.') }}\n")
            append(if (battery) "✅ Battery unrestricted\n" else "⚠️ Battery optimized – set to Unrestricted\n")
            append(if (store.enabled) "✅ Auto-reply ON\n" else "⏸ Auto-reply OFF\n")
            append("Top-ups today: ${store.todayCount}")
        }
        logView.text = store.logLines().joinToString("\n").ifEmpty { "Nothing yet." }
    }
}
