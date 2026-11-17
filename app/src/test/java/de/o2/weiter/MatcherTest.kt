package de.o2.weiter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private val eighty = "Hallo Max, Du hast 80% Deines aktivierten Highspeed-Datenvolumens verbraucht. Um kostenlos weitere 2 GB Highspeed-Datenvolumen abzurufen, einfach mit WEITER auf diese SMS antworten. Dein o2 Team"
    private val empty = "Hallo Max, Du hast Dein aktiviertes Highspeed-Datenvolumen verbraucht. Ab jetzt surfst Du bis zum Ende des Tages mit reduzierter Geschwindigkeit. Du möchtest mit Highspeed weitersurfen? Dann einfach mit WEITER auf diese SMS antworten, um kostenlos weitere 2 GB Highspeed-Datenvolumen abzurufen. Dein o2 Team"
    private val confirm = "Das Zusatzvolumen wurde erfolgreich aktiviert. Ihr o2 Team"

    @Test fun matchesRealMessages() {
        assertTrue(Matcher.asksForWeiter(eighty))
        assertTrue(Matcher.asksForWeiter(empty))
        assertFalse(Matcher.asksForWeiter(confirm))
    }

    @Test fun senderCheck() {
        assertTrue(Matcher.isFromO2("80112"))
        assertFalse(Matcher.isFromO2("+4917612345678"))
        assertFalse(Matcher.isFromO2(null))
    }

    @Test fun cooldownPreventsDoubleReply() {
        val t = 1_000_000_000L
        assertTrue(Matcher.shouldReply("80112", eighty, 0L, t))
        assertFalse(Matcher.shouldReply("80112", empty, t, t + 5_000))
        assertTrue(Matcher.shouldReply("80112", empty, t, t + Matcher.COOLDOWN_MS))
    }
}
