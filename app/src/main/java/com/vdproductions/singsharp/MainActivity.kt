package com.vdproductions.singsharp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vdproductions.singsharp.ui.theme.SingSharpTheme

class MainActivity : ComponentActivity() {
    companion object {
        var isServiceRunning = false
    }

    private lateinit var overlayPermissionIntentLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var extraInstructions = false

    val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isServiceRunning) {
            stopService()
            return
        }

        createNotificationChannel()

        overlayPermissionIntentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("temp", "a")
            if (!hasPermissions()) {
                Log.d("temp", "b")
                extraInstructions = true
            }
            else {
                startService()
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                tryStartService()
            }
        }

        if (!hasPermissions())
        {
            enableEdgeToEdge()
            setContent {
                SingSharpTheme {
                    ConsentScreen(
                        ::needExtraInstructions,
                        ::tryStartService,
                        ::hasPermissions
                    )
                }
            }
        } else {
            startService()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!hasPermissions())
        {
            enableEdgeToEdge()
            setContent {
                SingSharpTheme {
                    ConsentScreen(
                        ::needExtraInstructions,
                        ::tryStartService,
                        ::hasPermissions
                    )
                }
            }
        } else {
            startService()
        }
    }

    private fun needExtraInstructions(): Boolean {
        return extraInstructions;
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } && Settings.canDrawOverlays(this)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        else if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            overlayPermissionIntentLauncher.launch(intent)
        }
    }

    private fun tryStartService() {
        if (!hasPermissions()) {
            requestPermissions()
        }
        else {
            startService()
        }
    }

    private fun startService() {
        val intent = Intent(this, SingService::class.java)
        startService(intent)
        finish()
        isServiceRunning = true
    }

    private fun stopService() {
        val intent = Intent(this, SingService::class.java)
        stopService(intent)
        finish()
        isServiceRunning = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "sing_sharp_service_channel",
            "Sing Sharp service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for Sing Sharp service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

@Composable
fun ConsentScreen(needExtraInstructions: () -> Boolean, tryStartService: () -> Unit, hasPermissions: () -> Boolean) {
    if (hasPermissions()) {
        tryStartService()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Requires microphone permission to analyze audio.",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "Requires permission to post notifications to show when Sing Sharp is running and to turn it off.",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "Requires permission to display as overlay to show in front of other apps.",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (needExtraInstructions()) {
            Text(
                text = "Permission to display over other apps was not granted, press Accept again, find Sing Sharp, and toggle \"Allow display over other apps\".",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        Button(
            onClick = tryStartService,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Accept")
        }
    }
}