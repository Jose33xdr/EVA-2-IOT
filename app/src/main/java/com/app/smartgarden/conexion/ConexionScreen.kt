package com.app.smartgarden.conexion

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.ErrorRed
import com.app.smartgarden.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConexionScreen(
    conexionViewModel: ConexionViewModel = viewModel(),
    onVolver: () -> Unit
) {
    val uiState = conexionViewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Conexión Arduino / ESP32",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pestañas (Tabs)
            PrimaryTabRow(
                selectedTabIndex = uiState.pestanaActiva,
                containerColor = BackgroundSecondary,
                contentColor = AccentGreen
            ) {
                Tab(
                    selected = uiState.pestanaActiva == 0,
                    onClick = { conexionViewModel.cambiarPestana(0) },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi") },
                    text = { Text("Wi-Fi / Cloud") }
                )
                Tab(
                    selected = uiState.pestanaActiva == 1,
                    onClick = { conexionViewModel.cambiarPestana(1) },
                    icon = { Icon(Icons.Filled.Bluetooth, contentDescription = "Bluetooth") },
                    text = { Text("Bluetooth") }
                )
                Tab(
                    selected = uiState.pestanaActiva == 2,
                    onClick = { conexionViewModel.cambiarPestana(2) },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guía") },
                    text = { Text("Guía fácil") }
                )
            }

            // Contenido según la pestaña activa
            when (uiState.pestanaActiva) {
                0 -> PestanaWifiCloud()
                1 -> PestanaBluetooth(conexionViewModel, uiState)
                2 -> PestanaGuia()
            }
        }
    }
}

@Composable
private fun PestanaWifiCloud() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📡 Control por Wi-Fi y Firebase Firestore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El ESP32 se conecta a tu red Wi-Fi y sincroniza en tiempo real con Firebase Firestore. Cuando la app enciende la bomba o cambia los umbrales, el ESP32 lo detecta instantáneamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item {
            Text(
                text = "Código de Firmware para ESP32 (Arduino IDE):",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>

const char* ssid = "TU_RED_WIFI";
const char* password = "TU_PASSWORD_WIFI";

#define DHTPIN 4
#define DHTTYPE DHT11
DHT dht(DHTPIN, DHTTYPE);

const int RELAY_BOMBA = 5;

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_BOMBA, OUTPUT);
  dht.begin();

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.println("Conectando a WiFi...");
  }
  Serial.println("¡Conectado a WiFi!");
}

void loop() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  
  if (!isnan(h) && !isnan(t)) {
    Serial.printf("Temp: %.1f °C | Humedad: %.1f %%\n", t, h);
  }
  delay(5000);
}
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun PestanaBluetooth(viewModel: ConexionViewModel, uiState: ConexionUiState) {
    val permisoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.verificarBluetooth()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📡 Control Bluetooth Serial (HC-05 / ESP32)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estado: ${uiState.estadoConexionBt}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.dispositivoConectado != null) AccentGreen else TextSecondary
                    )
                    uiState.mensajeErrorBt?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reconexión automática",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (uiState.reconexionAutomatica)
                                "Activo: reintentará si se pierde la conexión"
                            else
                                "Inactivo: conexión solo manual",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.reconexionAutomatica,
                        onCheckedChange = { viewModel.setReconexionAutomatica(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentGreen,
                            checkedTrackColor = AccentGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        if (uiState.reconectandoBt) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentGreen
                            )
                            Text(
                                text = "Reconectando automáticamente...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                        Text(
                            text = uiState.estadoConexionBt,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelarReconexion() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Text("Detener reintentos")
                        }
                    }
                }
            }
        }

        if (uiState.necesitaPermisoBt) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Se requiere permiso para buscar y conectar dispositivos Bluetooth cercanos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permisoLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_SCAN
                                        )
                                    )
                                } else {
                                    permisoLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH,
                                            Manifest.permission.BLUETOOTH_ADMIN
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Text("Conceder permisos de Bluetooth")
                        }
                    }
                }
            }
        }

        if (uiState.dispositivoConectado == null) {
            item {
                Text(
                    text = "Dispositivos vinculados:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (uiState.dispositivosVinculados.isEmpty() && !uiState.necesitaPermisoBt) {
                item {
                    Text(
                        text = "No hay dispositivos Bluetooth vinculados. Vincula tu ESP32 o HC-05 desde los ajustes de Bluetooth de tu teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                items(uiState.dispositivosVinculados) { device ->
                    val nombreDispositivo = try {
                        device.name ?: "Dispositivo desconocido"
                    } catch (_: SecurityException) {
                        "Dispositivo Bluetooth"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nombreDispositivo,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Button(
                                onClick = { viewModel.conectarBluetooth(device) },
                                enabled = !uiState.cargandoBt,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("Conectar")
                            }
                        }
                    }
                }
            }
        } else {
            // Controles cuando está conectado
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.enviarComandoBluetooth("BOMBA_ON") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Bomba ON")
                    }
                    Button(
                        onClick = { viewModel.enviarComandoBluetooth("BOMBA_OFF") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("Bomba OFF")
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.desconectarBluetooth() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Desconectar Bluetooth")
                }
            }
        }

        item {
            Text(
                text = "Terminal Bluetooth (RX / TX):",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(12.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(uiState.logsBt) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.fontFamily(FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PestanaGuia() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📖 Guía Fácil de Conexión Hardware",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Sensor DHT11 / DHT22:\n   • VCC -> 3.3V / 5V\n   • GND -> GND\n   • DATA -> GPIO 4\n\n2. Módulo Relé (Bomba de agua):\n   • VCC -> 5V | GND -> GND | IN -> GPIO 5\n\n3. Bluetooth HC-05:\n   • TXD -> GPIO 16 (RX2)\n   • RXD -> GPIO 17 (TX2)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.TextStyle.fontFamily(family: FontFamily): androidx.compose.ui.text.TextStyle {
    return this.copy(fontFamily = family)
}
