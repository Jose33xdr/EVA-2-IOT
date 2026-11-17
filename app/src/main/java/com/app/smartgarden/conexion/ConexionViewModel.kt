package com.app.smartgarden.conexion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class ConexionUiState(
    val pestanaActiva: Int = 0, // 0: Wi-Fi/Cloud, 1: Bluetooth, 2: Guía
    val bluetoothDisponible: Boolean = false,
    val necesitaPermisoBt: Boolean = false,
    val dispositivosVinculados: List<BluetoothDevice> = emptyList(),
    val dispositivoConectado: BluetoothDevice? = null,
    val estadoConexionBt: String = "Desconectado",
    val logsBt: List<String> = emptyList(),
    val cargandoBt: Boolean = false,
    val mensajeErrorBt: String? = null
)

class ConexionViewModel : ViewModel() {

    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (_: Exception) {
        null
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    var uiState by mutableStateOf(ConexionUiState())
        private set

    init {
        verificarBluetooth()
    }

    fun cambiarPestana(index: Int) {
        uiState = uiState.copy(pestanaActiva = index)
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
        val nombre = try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
        uiState = uiState.copy(cargandoBt = true, mensajeErrorBt = null, estadoConexionBt = "Conectando a $nombre...")
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
                        estadoConexionBt = "Error de permisos",
                        mensajeErrorBt = "No se cuenta con el permiso para conectar al dispositivo Bluetooth"
                    )
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        cargandoBt = false,
                        estadoConexionBt = "Error de conexión",
                        mensajeErrorBt = "No se pudo conectar: ${e.localizedMessage}. Asegúrate de que el ESP32/Arduino esté vinculado y encendido."
                    )
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
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(
                    dispositivoConectado = null,
                    estadoConexionBt = "Desconectado (conexión perdida)",
                    logsBt = uiState.logsBt + "❌ Conexión perdida"
                )
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
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothSocket = null
        uiState = uiState.copy(
            dispositivoConectado = null,
            estadoConexionBt = "Desconectado",
            logsBt = uiState.logsBt + "🔌 Desconectado manualmente"
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
    }
}