package com.app.smartgarden.simulacion

import com.app.smartgarden.home.Planta

/**
 * Lógica pura de la simulación del invernadero (sin Firebase ni Android).
 * Dado el estado actual de una planta, calcula el siguiente estado, incluida
 * la máquina de estados de la ALERTA DE TEMPERATURA:
 *
 *   ninguna ──(temp ≥ umbral de alerta)──► pendiente  (2 min para actuar)
 *   pendiente ──(usuario atiende)──────► atendida
 *   pendiente ──(pasan 2 min)──────────► emergencia   (ventilador de emergencia)
 *   atendida / emergencia ──(temp baja del umbral − margen)──► ninguna
 */
object SimulacionEngine {

    const val INTERVALO_MS = 4000L
    const val ESPERA_ALERTA_MS = 2 * 60 * 1000L // tiempo que tiene el usuario para actuar

    const val ALERTA_NINGUNA = "ninguna"
    const val ALERTA_PENDIENTE = "pendiente"
    const val ALERTA_ATENDIDA = "atendida"
    const val ALERTA_EMERGENCIA = "emergencia"

    private const val DECREMENTO_HUM = 1.2     // baja sola si la bomba está apagada
    private const val HUMEDECIMIENTO_HUM = 2.5 // sube cuando la bomba está encendida

    private const val MARGEN_APAGADO_HUM = 5.0  // cuánto debe subir la humedad para apagar la bomba
    private const val MARGEN_FIN_ALERTA = 2.0   // cuánto debe bajar bajo el umbral de alerta para darla por terminada

    private const val TEMP_MIN = 18.0
    private const val TEMP_MAX = 45.0
    private const val HUM_MIN = 0.0
    private const val HUM_MAX = 100.0

    data class Resultado(
        val temperatura: Double,
        val humedad: Double,
        val ventilador: Boolean,
        val bomba: Boolean,
        val alertaEstado: String,
        val alertaInicio: Long,
        val emergencia: Boolean
    )

    fun siguiente(planta: Planta, ahora: Long = System.currentTimeMillis()): Resultado {
        var temp = planta.temperatura
        var hum = planta.humedad
        var bomba = planta.bombaEncendida

        // Deriva: humedad baja sola si no hay riego; temperatura fluctúa en rango ambiental o baja si se atiende
        val derivaTemp = when {
            planta.alertaEstado == ALERTA_ATENDIDA || planta.alertaEstado == ALERTA_EMERGENCIA -> -0.6
            temp > 25.5 -> -0.2
            temp < 22.0 -> 0.2
            else -> listOf(-0.1, 0.0, 0.1).random()
        }
        temp += derivaTemp
        hum += if (bomba) HUMEDECIMIENTO_HUM else -DECREMENTO_HUM

        temp = temp.coerceIn(TEMP_MIN, TEMP_MAX)
        hum = hum.coerceIn(HUM_MIN, HUM_MAX)

        // En modo automático se controla la bomba según el umbral de humedad
        if (planta.modoAutomatico) {
            if (!bomba && hum <= planta.humedadUmbral) {
                bomba = true
            } else if (bomba && hum >= planta.humedadUmbral + MARGEN_APAGADO_HUM) {
                bomba = false
            }
        }

        // ----- Máquina de estados de la alerta -----
        var estado = planta.alertaEstado
        var inicio = planta.alertaInicio
        var emergencia = false
        val sobreUmbral = temp >= planta.alertaUmbral
        val normalizada = temp <= planta.alertaUmbral - MARGEN_FIN_ALERTA

        when (estado) {
            ALERTA_PENDIENTE -> when {
                normalizada -> { estado = ALERTA_NINGUNA; inicio = 0L }
                ahora - inicio >= ESPERA_ALERTA_MS -> { estado = ALERTA_EMERGENCIA; emergencia = true }
            }
            ALERTA_ATENDIDA -> if (normalizada) { estado = ALERTA_NINGUNA; inicio = 0L }
            ALERTA_EMERGENCIA -> if (normalizada) { estado = ALERTA_NINGUNA; inicio = 0L; emergencia = false }
            else -> if (sobreUmbral) { estado = ALERTA_PENDIENTE; inicio = ahora }
        }

        return Resultado(
            temperatura = Math.round(temp * 10) / 10.0,   // un decimal, sin ruido de punto flotante
            humedad = Math.round(hum * 10) / 10.0,
            ventilador = false,
            bomba = bomba,
            alertaEstado = estado,
            alertaInicio = inicio,
            emergencia = emergencia
        )
    }
}
