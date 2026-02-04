package io.upvalue.io

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.getpebble.android.kit.PebbleKit
import io.upvalue.io.ui.theme.MyApplicationTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    companion object {
        val PEBBLE_APP_UUID: UUID = UUID.fromString("069e7e9c-1944-4a3c-a6e8-27ef9f96a2ae")
        const val KEY_CONTENT = 0
        const val NTFY_ACTION = "io.heckel.ntfy.MESSAGE_RECEIVED"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startBridgeService()
        } else {
            Toast.makeText(this, "Notification permission required for service", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionAndStartService()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DebugScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSendFaceJson = { json -> sendFaceJson(json) }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED -> {
                    startBridgeService()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startBridgeService()
        }
    }

    private fun startBridgeService() {
        val intent = Intent(this, NtfyForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Pebble bridge service started", Toast.LENGTH_SHORT).show()
    }

    private fun sendFaceJson(json: String) {
        when (val result = FaceJsonParser.parse(json)) {
            is FaceJsonParser.ParseResult.Success -> {
                val dict = FaceJsonParser.toPebbleDictionary(result.elements)
                PebbleKit.sendDataToPebble(applicationContext, PEBBLE_APP_UUID, dict)
                Toast.makeText(this, "Sent ${result.elements.size} element(s) to Pebble", Toast.LENGTH_SHORT).show()
            }
            is FaceJsonParser.ParseResult.Error -> {
                Toast.makeText(this, "Parse error: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    onSendFaceJson: (String) -> Unit
) {
    var quickText by remember { mutableStateOf("") }
    var rawJson by remember { mutableStateOf("") }
    var rawJsonExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pebble Face Debug",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Text section
        Text(
            text = "Quick Text",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = quickText,
            onValueChange = { quickText = it },
            label = { Text("Text to display") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (quickText.isNotBlank()) {
                    onSendFaceJson(FaceJsonParser.wrapQuickText(quickText))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send to Watch")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Raw JSON section (collapsible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rawJsonExpanded = !rawJsonExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rawJsonExpanded) "\u25BE Raw JSON" else "\u25B8 Raw JSON",
                style = MaterialTheme.typography.titleMedium
            )
        }

        AnimatedVisibility(visible = rawJsonExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    label = { Text("JSON face description") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (rawJson.isNotBlank()) {
                            onSendFaceJson(rawJson)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Raw JSON")
                }
            }
        }
    }
}
