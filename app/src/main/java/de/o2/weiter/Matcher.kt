package de.o2.weiter

object Matcher {
    const val SENDER = "80112"
    const val REPLY = "WEITER"

    /** Cooldown so the 80% and 100% SMS (often seconds apart) only trigger one top-up. */
    const val COOLDOWN_MS = 60 * 1000L

    fun isFromO2(address: String?): Boolean {
        val digits = address?.filter { it.isDigit() } ?: return false
        return digits == SENDER
    }

    /** True for the "80% verbraucht" and "Datenvolumen verbraucht" messages, false for the confirmation. */
    fun asksForWeiter(body: String): Boolean {
        val b = body.lowercase()
        return b.contains("weiter") && b.contains("verbraucht") && !b.contains("erfolgreich aktiviert")
    }

    fun shouldReply(address: String?, body: String, lastSentAt: Long, now: Long): Boolean =
        isFromO2(address) && asksForWeiter(body) && now - lastSentAt >= COOLDOWN_MS
}
