package de.o2.weiter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny SharedPreferences wrapper: on/off switch, cooldown timestamp, daily counter and a short log. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("o2weiter", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) { p.edit().putBoolean("enabled", v).apply() }

    var lastSentAt: Long
        get() = p.getLong("lastSentAt", 0L)
        set(v) { p.edit().putLong("lastSentAt", v).apply() }

    val todayCount: Int
        get() = if (p.getString("countDay", "") == today()) p.getInt("count", 0) else 0

    fun incrementToday() {
        val n = todayCount + 1
        p.edit().putString("countDay", today()).putInt("count", n).apply()
    }

    fun log(msg: String) {
        val ts = SimpleDateFormat("dd.MM HH:mm:ss", Locale.GERMANY).format(Date())
        val lines = (listOf("$ts  $msg") + logLines()).take(30)
        p.edit().putString("log", lines.joinToString("\n")).apply()
    }

    fun logLines(): List<String> = p.getString("log", "")!!.lines().filter { it.isNotBlank() }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
