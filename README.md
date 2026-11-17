# o2 Auto-WEITER

A tiny Android app that automatically replies **`WEITER`** to o2's SMS from **80112**, so you get your next free 2 GB of high-speed data on the o2 *Unlimited On Demand* plan without lifting a finger.

## Why

On *Unlimited On Demand*, o2 sends an SMS when you've used 80% or 100% of your high-speed volume. You must reply `WEITER` each time to get another 2 GB. This app does that for you.

## How it works

- Listens for incoming SMS (works even when the app is closed).
- If the sender is `80112` and the text asks you to reply `WEITER` (the "80% … verbraucht" or "Datenvolumen verbraucht" messages), it sends `WEITER` back.
- Ignores the "Zusatzvolumen wurde erfolgreich aktiviert" confirmation.
- Replies instantly, then a 1-minute cooldown so the 80% and 100% messages arriving back-to-back only trigger one top-up.
- Replies from the same SIM that received the message (dual-SIM safe).
- Shows a notification and keeps a small log plus a "top-ups today" counter.

No internet, no server, no tracking. Everything stays on the phone.

## Install

Requirements: Android Studio or the Android SDK, JDK 17+, and a phone with USB debugging enabled.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open **o2 WEITER** once and:

1. Tap **Grant permissions** (SMS send/receive, phone state, notifications).
2. Tap **Battery: set Unrestricted**.
3. On Samsung, also make sure the app isn't under *Settings → Battery → Background usage limits → Sleeping apps*.

The app isn't on the Play Store, because Google doesn't allow SMS permissions for this kind of app. Sideload it as shown above.

## Usage

- **Auto-reply switch** turns automation on or off.
- **Send WEITER now** sends a manual request (asks to confirm first).
- **Log** shows the last 30 events.

## Tests

```bash
./gradlew testDebugUnitTest
```

The tests check the message matcher against real o2 SMS texts.

## Disclaimer

This is an unofficial hobby project and isn't affiliated with o2 or Telefónica. Message texts may change; if o2 rewords its SMS, adjust `Matcher.kt`.
