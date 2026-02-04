# AGENTS.md

## Project Overview

A server-driven UI watchface for Pebble smartwatches. An Android companion app parses JSON face descriptions and sends structured key-value data to the watch via PebbleKit. The watch renders persistent UI elements using pre-allocated fixed-length buffers. An ntfy.sh integration forwards incoming notifications to the watch as single text face elements.

For up-to-date Pebble development documentation, see <https://developer.repebble.com/>.

## Architecture

```
Android App (MainActivity)
  │  User types text or raw JSON
  │  FaceJsonParser validates & parses
  │  Maps to PebbleDictionary (integer keys)
  ▼
Pebble Watch (Bluetooth / PebbleKit)
  │  inbox_received_handler()
  │  Copies values into static buffers
  ▼
Watch Display
  - Time always shown at top (42pt Bitham Bold)
  - Up to 4 face elements stacked vertically below
  - Persists until next message (send count=0 to clear)
  - Vibrates on each update
```

ntfy.sh notifications are also bridged via `NtfyReceiver`, which wraps each incoming message as a single text face element using `FaceJsonParser.toPebbleDictionary()`.

```
ntfy.sh app (Android)
  │  broadcast: "io.heckel.ntfy.MESSAGE_RECEIVED"
  ▼
NtfyReceiver (BroadcastReceiver)
  │  truncates to 255 chars
  │  builds FaceElement, sends via toPebbleDictionary()
  ▼
Pebble Watch (same rendering path as above)
```

## Directory Structure

```
android/                        # Android companion app (Kotlin)
  app/src/main/java/io/upvalue/io/
    MainActivity.kt             # Entry point, debug UI, sends face JSON to watch
    FaceJsonParser.kt           # JSON parser, PebbleDictionary builder, text wrapper
    NtfyForegroundService.kt    # Background service keeping receiver alive
    NtfyReceiver.kt             # Intercepts ntfy broadcasts, sends as face element
  app/src/test/java/io/upvalue/io/
    FaceJsonParserTest.kt       # Unit tests for parser (19 tests)
    ExampleUnitTest.kt          # Placeholder test
  app/src/main/AndroidManifest.xml
  app/build.gradle.kts
  gradle/libs.versions.toml     # Dependency versions

watch/                          # Pebble watchface (C)
  src/c/myfirstproject.c        # All watch logic: time display + face layout rendering
  package.json                  # Pebble app metadata (UUID, message keys, platforms)
  wscript                       # Pebble build script (waf/Python)
  Justfile                      # Build recipe

.devcontainer/                  # VS Code dev container with Pebble SDK
```

## Communication Protocol

**App UUID:** `069e7e9c-1944-4a3c-a6e8-27ef9f96a2ae`

Messages are sent as `PebbleDictionary` objects with integer keys:

| Key | Name | Type | Description |
|-----|------|------|-------------|
| 0 | `FACE_COUNT` | uint8 | Number of elements (0–4) |
| 1 | `ELEM_0_TYPE` | uint8 | Element 0 type (0 = text) |
| 2 | `ELEM_0_VALUE` | cstring | Element 0 text content |
| 3 | `ELEM_1_TYPE` | uint8 | Element 1 type |
| 4 | `ELEM_1_VALUE` | cstring | Element 1 text content |
| 5 | `ELEM_2_TYPE` | uint8 | Element 2 type |
| 6 | `ELEM_2_VALUE` | cstring | Element 2 text content |
| 7 | `ELEM_3_TYPE` | uint8 | Element 3 type |
| 8 | `ELEM_3_VALUE` | cstring | Element 3 text content |

JSON input format (parsed on Android):
```json
{"face": [{"type": "text", "value": "Hello world!"}]}
```

Design decisions:
- JSON is parsed on Android because the Pebble SDK has no JSON parser
- Values are truncated to 255 chars on Android before sending
- The watch copies values into static 256-byte buffers via `strncpy` (the old code pointed `text_layer` at transient tuple data, which was a bug)
- Send `FACE_COUNT = 0` to clear the display
- Inbox buffer is 2048 bytes (increased from 256)

## Android App

**Package:** `io.upvalue.io`
**Min SDK:** 24 (Android 7.0) | **Target SDK:** 36

### Key Components

- **MainActivity** (`MainActivity.kt`): Launches the foreground service, requests notification permission on Android 13+. Provides a debug UI with two sections: Quick Text (wraps input in JSON and sends) and Raw JSON (sends JSON as-is). Calls `FaceJsonParser` to validate and convert JSON to a `PebbleDictionary`.
- **FaceJsonParser** (`FaceJsonParser.kt`): Pure Kotlin object (no Android deps except PebbleKit for `toPebbleDictionary`). `parse()` validates JSON and returns a `ParseResult` sealed class (`Success` with elements list, or `Error` with message). `toPebbleDictionary()` maps elements to the integer key scheme. `wrapQuickText()` wraps plain text into JSON format using `org.json` for proper escaping.
- **NtfyForegroundService** (`NtfyForegroundService.kt`): A `START_STICKY` foreground service that keeps `NtfyReceiver` registered even when the app UI is closed. Shows a persistent "Listening for ntfy messages" notification.
- **NtfyReceiver** (`NtfyReceiver.kt`): `BroadcastReceiver` filtering for action `io.heckel.ntfy.MESSAGE_RECEIVED`. Extracts the `"message"` string extra, truncates to 255 characters, wraps as a single `FaceElement`, and sends via `FaceJsonParser.toPebbleDictionary()`.

### Dependencies

- **PebbleKit 4.0.1** (from Sonatype Maven, `@aar`) for Bluetooth communication with the watch
- **Jetpack Compose** with Material 3 for UI
- Standard AndroidX lifecycle and activity libraries
- **org.json:json:20231013** (test only) — provides a real `org.json` implementation for unit tests since Android's `org.json` is stubbed in local JVM tests

### Testing

Unit tests are in `app/src/test/java/io/upvalue/io/FaceJsonParserTest.kt`. Run with `./gradlew test`.

Tests cover: valid single/multi-element JSON, empty array, missing `face` key, invalid JSON, element count capping at 4, unknown type rejection, missing type, long value truncation, exact boundary (255 chars), empty values, non-object elements, `wrapQuickText` with plain text, empty string, special characters, unicode, escape sequences, and `FaceElement` data class equality.

Note: `toPebbleDictionary()` cannot be unit tested locally because PebbleKit is distributed as an AAR with no JVM stub. It would need instrumented tests or a mock.

## Pebble Watch App

**SDK Version:** 3 | **Type:** Watchface

### Source: `watch/src/c/myfirstproject.c`

The watchface uses pre-allocated static data structures — no dynamic allocation at runtime.

**Data structures (~1033 bytes static):**
- `FaceLayout` containing up to 4 `FaceElement` structs
- Each `FaceElement`: uint8 type, 256-byte char buffer, bool active flag
- 4 pre-allocated `TextLayer` pointers

**Layout:**
1. **Time layer** (top, y=10): HH:MM in 42pt Bitham Bold, updated every minute via `tick_timer_service`
2. **Face elements** (y=65 to bottom): Up to 4 text layers, vertically divided equally among active elements. 24pt Gothic Bold, centered, word-wrapped.

**Message handling:** `inbox_received_handler` reads `KEY_FACE_COUNT`, clamps to 4, zeroes the layout, iterates elements to copy type/value into static buffers, calls `update_face_layout()`, then `vibes_short_pulse()`. An `inbox_dropped_handler` logs errors via `APP_LOG`.

**Key difference from old code:** The old notification system pointed `text_layer_set_text` at `content_tuple->value->cstring`, which is transient memory owned by the `DictionaryIterator`. The new system copies into persistent static buffers.

### Target Platforms

aplite (original Pebble), basalt (Pebble Time), chalk (Pebble Time Round), diorite (Pebble 2), emery (Pebble Time 2).

### Memory Usage (from `pebble build`)

| Platform | RAM Footprint | Free Heap | Total Available |
|----------|--------------|-----------|-----------------|
| aplite   | 2660 bytes   | 21916 bytes | 24 KB |
| basalt   | 2660 bytes   | 62876 bytes | 64 KB |
| chalk    | 2660 bytes   | 62876 bytes | 64 KB |
| diorite  | 2660 bytes   | 62876 bytes | 64 KB |
| emery    | 2660 bytes   | 128412 bytes | 128 KB |

### Building

The watch app uses the Pebble SDK build system (`pebble build` via wscript). The dev container at `.devcontainer/` provides a pre-configured environment with the Pebble SDK, Node.js, Python 3, and libsdl2 for emulator support.

The `package.json` `messageKeys` array must list keys in the same order as the integer key scheme (FACE_COUNT=0, ELEM_0_TYPE=1, ...). The C code uses hardcoded `#define` macros for keys rather than the auto-generated header, but the `messageKeys` must still be present for the build system.

## Target Hardware

The physical device running the Pebble firmware is not original Pebble hardware:

- **SoC:** Nordic nRF52840 (ARM Cortex-M4F @ 64 MHz)
- **RAM:** 256 KB
- **Flash:** 1 MB

The Pebble SDK build targets (aplite, basalt, chalk, diorite, emery) are for SDK compatibility, but the physical device has significantly more resources than any original Pebble model. Memory budgets that were tight on original hardware (e.g., aplite's 24 KB app RAM) are not a concern on this device.

## Setup

1. Install the [ntfy.sh Android app](https://ntfy.sh) and subscribe to one or more topics.
2. Build and install the Android companion app onto the same phone paired with a Pebble watch.
3. Build the watchface (`watch/build/myfirstproject.pbw`) and install it on the Pebble.
4. Use the Quick Text or Raw JSON debug UI in the Android app to send face descriptions to the watch.
