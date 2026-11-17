package com.app.smartgarden.conexion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class ConexionUiState(
    val pestanaActiva: Int = 0, // 0: Wi-Fi IP, 1: Bluetooth, 2: Guía
    val ipEsp32: String = "192.168.1.50",
    val estadoConexionWifi: String = "Desconectado",
    val cargandoWifi: Boolean = false,
    val mensajeErrorWifi: String? = null,
    val logsWifi: List<String> = emptyList(),
    val temperaturaWifi: String = "--",
    val humedadWifi: String = "--",
    val bombaWifiEstado: String = "OFF",
    val reconexionWifiActiva: Boolean = true,
    val reconectandoWifi: Boolean = false,
    val autoActualizarWifi: Boolean = false,
    val bluetoothDisponible: Boolean = false,
    val necesitaPermisoBt: Boolean = false,
    val dispositivosVinculados: List<BluetoothDevice> = emptyList(),
    val dispositivoConectado: BluetoothDevice? = null,
    val estadoConexionBt: String = "Desconectado",
    val logsBt: List<String> = emptyList(),
    val cargandoBt: Boolean = false,
    val mensajeErrorBt: String? = null,
    val reconexionAutomatica: Boolean = true,
    val reconectandoBt: Boolean = false,
    val reintentoActual: Int = 0
)

class ConexionViewModel : ViewModel() {

    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (_: Exception) {
        null
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var ultimoDispositivo: BluetoothDevice? = null
    private var desconexionManual: Boolean = false
    private var reconexionJob: Job? = null
    private var wifiPollJob: Job? = null

    var uiState by mutableStateOf(ConexionUiState())
        private set

    init {
        verificarBluetooth()
    }

    fun cambiarPestana(index: Int) {
        uiState = uiState.copy(pestanaActiva = index)
    }

    fun setIpEsp32(ip: String) {
        uiState = uiState.copy(ipEsp32 = ip)
    }

    fun setReconexionWifi(activar: Boolean) {
        uiState = uiState.copy(reconexionWifiActiva = activar)
        if (!activar) {
            detenerAutoActualizarWifi()
        }
    }

    fun setAutoActualizarWifi(activar: Boolean) {
        uiState = uiState.copy(autoActualizarWifi = activar)
        if (activar) {
            iniciarBucleWifi()
        } else {
            detenerAutoActualizarWifi()
        }
    }

    private fun iniciarBucleWifi() {
        wifiPollJob?.cancel()
        wifiPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && uiState.autoActualizarWifi) {
                conectarYLeerWifiSilencioso()
                delay(5000)
            }
        }
    }

    fun detenerAutoActualizarWifi() {
        wifiPollJob?.cancel()
        wifiPollJob = null
        uiState = uiState.copy(
            autoActualizarWifi = false,
            reconectandoWifi = false,
            estadoConexionWifi = "Desconectado"
        )
    }

    fun conectarYLeerWifi() {
        val ip = uiState.ipEsp32.trim()
        if (ip.isBlank()) {
            uiState = uiState.copy(mensajeErrorWifi = "Introduce una dirección IP válida para el ESP32")
            return
        }
        uiState = uiState.copy(
            cargandoWifi = true,
            reconectandoWifi = false,
            mensajeErrorWifi = null,
            estadoConexionWifi = "Conectando a http://$ip/ ..."
        )
        viewModelScope.launch(Dispatchers.IO) {
            realizarPeticionHttp(ip, silencioso = false)
        }
    }

    private suspend fun conectarYLeerWifiSilencioso() {
        val ip = uiState.ipEsp32.trim()
        if (ip.isBlank()) return
        realizarPeticionHttp(ip, silencioso = true)
    }

    private suspend fun realizarPeticionHttp(ip: String, silencioso: Boolean) {
        try {
            val url = java.net.URL("http://$ip/")
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

                var temp = "--"
                var hum = "--"
                var bomba = "OFF"

                try {
                    val tempRegex = "\"temperatura\":\\s*([0-9.]+)".toRegex()
                    val humRegex = "\"humedad\":\\s*([0-9.]+)".toRegex()
                    val bombaRegex = "\"bomba\":\\s*\"([^\"]+)\"".toRegex()

                    tempRegex.find(responseBody)?.groupValues?.get(1)?.let { temp = "$it °C" }
                    humRegex.find(responseBody)?.groupValues?.get(1)?.let { hum = "$it %" }
                    bombaRegex.find(responseBody)?.groupValues?.get(1)?.let { bomba = it }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoWifi = false,
                        reconectandoWifi = false,
                        estadoConexionWifi = "Conectado (HTTP 200)",
                        temperaturaWifi = temp,
                        humedadWifi = hum,
                        bombaWifiEstado = bomba,
                        mensajeErrorWifi = null,
                        logsWifi = if (!silencioso) uiState.logsWifi + "📥 RX [http://$ip/]: $responseBody" else uiState.logsWifi
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoWifi = false,
                        reconectandoWifi = uiState.reconexionWifiActiva && uiState.autoActualizarWifi,
                        estadoConexionWifi = if (uiState.reconexionWifiActiva && uiState.autoActualizarWifi) "Conexión perdida. Reconectando..." else "Error HTTP $responseCode",
                        mensajeErrorWifi = "El ESP32 respondió con código HTTP $responseCode"
                    )
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(
                    cargandoWifi = false,
                    reconectandoWifi = uiState.reconexionWifiActiva && uiState.autoActualizarWifi,
                    estadoConexionWifi = if (uiState.reconexionWifiActiva && uiState.autoActualizarWifi) "Sin señal. Reconectando automáticamente..." else "Error de conexión",
                    mensajeErrorWifi = if (!silencioso) "No se pudo conectar a http://$ip/: ${e.localizedMessage}" else null
                )
            }
        }
    }

    fun enviarComandoWifi(endpoint: String, descripcionComando: String) {
        val ip = uiState.ipEsp32.trim()
        if (ip.isBlank()) {
            uiState = uiState.copy(mensajeErrorWifi = "Introduce la IP del ESP32")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(logsWifi = uiState.logsWifi + "📤 TX [http://$ip/$endpoint]: $descripcionComando")
                }
                val url = java.net.URL("http://$ip/$endpoint")
                val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                }
                val responseCode = connection.responseCode
                val body = try { connection.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
                connection.disconnect()

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        bombaWifiEstado = if (endpoint.contains("on", ignoreCase = true)) "ON" else if (endpoint.contains("off", ignoreCase = true)) "OFF" else uiState.bombaWifiEstado,
                        logsWifi = uiState.logsWifi + "📥 Respuesta [$responseCode]: $body"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        mensajeErrorWifi = "Error al enviar comando Wi-Fi: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun setReconexionAutomatica(activar: Boolean) {
        uiState = uiState.copy(reconexionAutomatica = activar)
        if (!activar) {
            cancelarReconexion()
        }
    }

    fun cancelarReconexion() {
        reconexionJob?.cancel()
        reconexionJob = null
        uiState = uiState.copy(
            reconectandoBt = false,
            cargandoBt = false,
            estadoConexionBt = "Desconectado",
            logsBt = uiState.logsBt + "⏹️ Reconexión automática detenida por el usuario"
        )
    }

    @SuppressLint("MissingPermission")
    fun verificarBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            uiState = uiState.copy(
                bluetoothDisponible = false,
                necesitaPermisoBt = false,
                mensajeErrorBt = "Bluetooth no soportado en este dispositivo"
            )
            return
        }
        try {
            val vinculado = adapter.bondedDevices?.toList() ?: emptyList()
            uiState = uiState.copy(
                bluetoothDisponible = true,
                necesitaPermisoBt = false,
                dispositivosVinculados = vinculado,
                mensajeErrorBt = null
            )
        } catch (_: SecurityException) {
            uiState = uiState.copy(
                bluetoothDisponible = false,
                necesitaPermisoBt = true,
                dispositivosVinculados = emptyList(),
                mensajeErrorBt = "Se requiere permiso de Dispositivos cercanos (Bluetooth)"
            )
        } catch (e: Exception) {
            uiState = uiState.copy(
                bluetoothDisponible = false,
                necesitaPermisoBt = false,
                dispositivosVinculados = emptyList(),
                mensajeErrorBt = "Error al verificar Bluetooth: ${e.localizedMessage}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun conectarBluetooth(device: BluetoothDevice) {
        desconexionManual = false
        ultimoDispositivo = device
        reconexionJob?.cancel()
        reconexionJob = null

        val nombre = try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
        uiState = uiState.copy(
            cargandoBt = true,
            reconectandoBt = false,
            mensajeErrorBt = null,
            estadoConexionBt = "Conectando a $nombre..."
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket?.close()
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    bluetoothAdapter?.cancelDiscovery()
                } catch (_: SecurityException) {}
                socket.connect()
                bluetoothSocket = socket

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoBt = false,
                        reconectandoBt = false,
                        reintentoActual = 0,
                        dispositivoConectado = device,
                        estadoConexionBt = "Conectado a $nombre",
                        logsBt = uiState.logsBt + "🔗 Conectado exitosamente a $nombre"
                    )
                }
                escucharBluetooth(socket)
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoBt = false,
                        reconectandoBt = false,
                        estadoConexionBt = "Error de permisos",
                        mensajeErrorBt = "No se cuenta con el permiso para conectar al dispositivo Bluetooth"
                    )
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoBt = false,
                        estadoConexionBt = "Error de conexión",
                        mensajeErrorBt = "No se pudo conectar: ${e.localizedMessage}. Asegúrate de que el ESP32 esté encendido."
                    )
                }
                if (uiState.reconexionAutomatica && !desconexionManual) {
                    iniciarReconexionAutomatica(device)
                }
            }
        }
    }

    private suspend fun escucharBluetooth(socket: BluetoothSocket) {
        val buffer = ByteArray(1024)
        try {
            val input = socket.inputStream
            while (true) {
                val bytes = input.read(buffer)
                if (bytes > 0) {
                    val mensaje = String(buffer, 0, bytes).trim()
                    if (mensaje.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(logsBt = uiState.logsBt + "📥 RX: $mensaje")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            try {
                socket.close()
            } catch (_: Exception) {}
            bluetoothSocket = null

            val dev = ultimoDispositivo
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(
                    dispositivoConectado = null,
                    estadoConexionBt = if (uiState.reconexionAutomatica && !desconexionManual) "Conexión perdida. Reconectando..." else "Desconectado (conexión perdida)",
                    logsBt = uiState.logsBt + "❌ Conexión perdida con el dispositivo"
                )
                if (uiState.reconexionAutomatica && !desconexionManual && dev != null) {
                    iniciarReconexionAutomatica(dev)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarReconexionAutomatica(device: BluetoothDevice) {
        reconexionJob?.cancel()
        val nombre = try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }

        reconexionJob = viewModelScope.launch(Dispatchers.IO) {
            var intento = 1
            while (isActive && uiState.reconexionAutomatica && !desconexionManual) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoBt = true,
                        reconectandoBt = true,
                        reintentoActual = intento,
                        estadoConexionBt = "Reconectando a $nombre (intento $intento)...",
                        logsBt = uiState.logsBt + "🔄 Intentando reconectar a $nombre (intento $intento)..."
                    )
                }

                delay(3000)
                if (!isActive || desconexionManual || !uiState.reconexionAutomatica) break

                try {
                    bluetoothSocket?.close()
                } catch (_: Exception) {}

                try {
                    val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    try {
                        bluetoothAdapter?.cancelDiscovery()
                    } catch (_: SecurityException) {}

                    socket.connect()
                    bluetoothSocket = socket

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(
                            cargandoBt = false,
                            reconectandoBt = false,
                            reintentoActual = 0,
                            dispositivoConectado = device,
                            estadoConexionBt = "Conectado a $nombre",
                            logsBt = uiState.logsBt + "✅ Reconectado exitosamente a $nombre"
                        )
                    }

                    escucharBluetooth(socket)
                    break
                } catch (e: IOException) {
                    intento++
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(
                            estadoConexionBt = "Fallo en intento. Reintentando en 3s...",
                            logsBt = uiState.logsBt + "⚠️ Reintento fallido, esperando para volver a intentar..."
                        )
                    }
                } catch (e: SecurityException) {
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(
                            cargandoBt = false,
                            reconectandoBt = false,
                            estadoConexionBt = "Error de permisos",
                            mensajeErrorBt = "Permiso Bluetooth revocado"
                        )
                    }
                    break
                }
            }
        }
    }

    fun enviarComandoBluetooth(comando: String) {
        val socket = bluetoothSocket
        if (socket == null || !socket.isConnected) {
            uiState = uiState.copy(mensajeErrorBt = "No hay ningún dispositivo Bluetooth conectado")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                socket.outputStream.write((comando + "\n").toByteArray())
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(logsBt = uiState.logsBt + "📤 TX: $comando")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(mensajeErrorBt = "Error al enviar comando: ${e.localizedMessage}")
                }
            }
        }
    }

    fun desconectarBluetooth() {
        desconexionManual = true
        reconexionJob?.cancel()
        reconexionJob = null
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothSocket = null
        uiState = uiState.copy(
            cargandoBt = false,
            reconectandoBt = false,
            reintentoActual = 0,
            dispositivoConectado = null,
            estadoConexionBt = "Desconectado",
            logsBt = uiState.logsBt + "🔌 Desconectado manualmente"
        )
    }

    override fun onCleared() {
        super.onCleared()
        desconexionManual = true
        reconexionJob?.cancel()
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
    }
}
