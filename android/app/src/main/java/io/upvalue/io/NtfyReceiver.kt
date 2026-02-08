package io.upvalue.io

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.getpebble.android.kit.PebbleKit

class NtfyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NtfyReceiver"
        private const val MAX_LENGTH = 255
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive triggered! Action: ${intent.action}")
        Log.d(TAG, "Extras: ${intent.extras?.keySet()?.joinToString { "$it=${intent.extras?.get(it)}" }}")

        if (intent.action != "io.heckel.ntfy.MESSAGE_RECEIVED") {
            Log.d(TAG, "Wrong action, ignoring")
            return
        }

        val message = intent.getStringExtra("message")
        if (message == null) {
            Log.d(TAG, "No message extra found")
            return
        }

        Log.d(TAG, "Message received (${message.length} chars): $message")

        val trimmed = message.trim()
        val result = FaceJsonParser.parse(trimmed)
        Log.d(TAG, "Parse result: $result")

        val elements = when (result) {
            is FaceJsonParser.ParseResult.Success -> result.elements
            is FaceJsonParser.ParseResult.Error -> {
                Log.d(TAG, "Not face JSON (${result.message}), wrapping as text")
                val truncated = if (trimmed.length > MAX_LENGTH) trimmed.take(MAX_LENGTH) else trimmed
                listOf(FaceJsonParser.FaceElement(type = 0, value = truncated))
            }
        }

        val dict = FaceJsonParser.toPebbleDictionary(elements)
        PebbleKit.sendDataToPebble(context, MainActivity.PEBBLE_APP_UUID, dict)

        Log.d(TAG, "Sent to Pebble: ${elements.size} element(s)")
        // Toast.makeText(context, "ntfy→Pebble: ${elements.size} element(s)", Toast.LENGTH_SHORT).show()
    }
}
