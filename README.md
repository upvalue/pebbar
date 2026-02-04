# pebbar

Prototype app that lets [ntfy.sh](https://ntfy.sh) notifications drive UI on a
Pebble smartwatch. An Android companion app intercepts ntfy broadcasts and
forwards them to the watch over Bluetooth.

Inspired by bitbar, xbar etc. Put arbitrary things on the watch without writing task-specific C/Java.

See [AGENTS.md](AGENTS.md) for architecture details. Pebble dev docs:
<https://developer.repebble.com/>

## Testing on Android (Wi-Fi debugging)

**Prerequisites:** Android Studio, a phone running Android 11+, and both devices on the same Wi-Fi network.

1. On your phone, enable **Settings > Developer Options > Wireless debugging**
2. Tap **Pair device with pairing code** and note the code, IP, and port
3. Pair from terminal:
   ```bash
   adb pair <IP>:<PAIRING_PORT>
   ```
4. Connect to the debug port (shown on the Wireless debugging screen):
   ```bash
   adb connect <IP>:<DEBUG_PORT>
   ```
5. Build and install:
   ```bash
   cd android && ./gradlew installDebug
   ```
6. Install the [ntfy.sh app](https://ntfy.sh) and subscribe to a topic
7. Open "Pebble Test App" on the phone and tap **Send Test Notification** to verify the Pebble connection
8. Send a real notification:
   ```bash
   curl -d "Hello Pebble" ntfy.sh/your-topic
   ```

Debug logs: `adb logcat -s NtfyReceiver`
