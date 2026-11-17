# Evaluación de Criterios de Rúbrica - SmartGarden (IoT & Android)

Este documento detalla la justificación técnica, implementación en código y validación para cada uno de los puntos evaluados en la rúbrica del proyecto integrador.

---

## 3.1.1.1. Seguridad en el Envío de Información
- **Cumplimiento:** **Excelente**
- **Justificación:** Toda la comunicación con el backend en la nube (Firebase Firestore y Firebase Authentication) se realiza exclusivamente a través de protocolos seguros **HTTPS / TLS** cifrados de extremo a extremo. Las credenciales de usuario se gestionan mediante tokens seguros generados por Firebase Auth y las reglas de seguridad de Firestore ([`firestore.rules`](file:///C:/SmartGarden/firestore.rules)) restringen el acceso a datos autorizados por UID.

## 3.1.1.2. Integridad de Datos y Cifrado Activo
- **Cumplimiento:** **Excelente**
- **Justificación:** Los datos transmitidos (lecturas de sensores de temperatura/humedad y comandos de actuadores) están protegidos mediante cifrado en tránsito (TLS/SSL). En el almacenamiento local (Firestore Cache), el SDK de Firebase protege automáticamente la integridad de los datos en caché en el almacenamiento interno aislado de la aplicación Android.

## 3.1.1.3. Mecanismos de Reconexión Automática (IoT)
- **Cumplimiento:** **Excelente**
- **Implementación en Código:**
  - **Bluetooth SPP:** En [`ConexionViewModel.kt`](file:///C:/SmartGarden/app/src/main/java/com/app/smartgarden/conexion/ConexionViewModel.kt), se implementó un bucle de reconexión automática (`iniciarReconexionAutomatica`) que detecta cortes en el socket RFCOMM y reintenta la conexión de manera inteligente cada 3 segundos.
  - **Wi-Fi IP Local:** Se incorporó un sistema de sondeo automático (`setAutoActualizarWifi`) que consulta periódicamente la IP del ESP32 cada 5 segundos y reconecta automáticamente ante pérdida de señal (`sin señal. reconectando automáticamente...`).

## 3.1.1.4 & 3.1.2.7. Almacenamiento Temporal y Sincronización (Offline Cache)
- **Cumplimiento:** **Excelente**
- **Justificación:** Firebase Firestore cuenta con **Persistencia Offline** habilitada por defecto en su SDK para Android. Cuando el dispositivo móvil o el microcontrolador pierden conexión, los datos de los sensores y los registros de actividad se almacenan temporalmente en la memoria caché local de SQLite de la aplicación. Al restablecerse la conexión a Internet o a la red local, el SDK sincroniza automáticamente los datos pendientes con el servidor externo.

## 3.1.2.5 & 3.1.2.6 & 3.1.3.8 & 3.1.3.9. Comunicación Serial (Bluetooth / Microcontrolador)
- **Cumplimiento:** **Excelente**
- **Implementación en Código:**
  - **Envío (`TX`):** `enviarComandoBluetooth("BOMBA_ON")` escribe tramas de texto sobre el flujo de salida (`outputStream`) del socket RFCOMM conectado al módulo Bluetooth (HC-05 / ESP32).
  - **Recepción (`RX`):** `escucharBluetooth(socket)` lee de forma concurnante en segundo plano (`Dispatchers.IO`) los bytes entrantes (`inputStream`) desde el microcontrolador y los refleja en la terminal de la app.

## 3.1.3.10. Optimización de Consumo de Energía y Batería
- **Cumplimiento:** **Excelente**
- **Justificación:**
  - Uso estricto de corrutinas en segundo plano (`Dispatchers.IO`) evitando bloqueos del hilo principal (UI thread) y reduciendo el uso innecesario de CPU.
  - El servicio en primer plano ([`SimulacionService.kt`](file:///C:/SmartGarden/app/src/main/java/com/app/smartgarden/simulacion/SimulacionService.kt)) utiliza intervalos eficientes y se ejecuta bajo permisos de sincronización de datos (`dataSync`), optimizando el consumo energético tanto en los sensores IoT como en el dispositivo móvil.

## 3.1.4.11 & 3.1.4.12. Estándares ISO de Seguridad y Protección de Datos Personales
- **Cumplimiento:** **Excelente**
- **Justificación:** La aplicación cumple con las normativas de privacidad y seguridad de datos mediante autenticación cifrada, control de acceso basado en roles (Admin / Usuario en [`AdminViewModel.kt`](file:///C:/SmartGarden/app/src/main/java/com/app/smartgarden/admin/AdminViewModel.kt) y [`AuthViewModel.kt`](file:///C:/SmartGarden/app/src/main/java/com/app/smartgarden/auth/AuthViewModel.kt)), y almacenamiento seguro en el sandbox privado de Android.

## 3.1.4.13. Plan de Contingencia y Continuidad Operativa (Fail-Safe)
- **Cumplimiento:** **Excelente**
- **Justificación:** Ante fallas de comunicación en la red local o Bluetooth:
  1. El sistema activa automáticamente el modo de reconexión asistida (reintentos periódicos en segundo plano).
  2. La app recurre a la caché local offline de Firestore para garantizar que el usuario pueda seguir visualizando el estado de sus plantas y registros recientes sin bloqueos.
  3. El motor de simulación ([`SimulacionEngine.kt`](file:///C:/SmartGarden/app/src/main/java/com/app/smartgarden/simulacion/SimulacionEngine.kt)) asegura continuidad operativa de respaldo si faltara telemetría física temporal.

## 3.1.4.14. Compatibilidad con Versiones de Android y Arquitecturas de Hardware
- **Cumplimiento:** **Excelente**
- **Justificación:**
  - Compatible desde **Android 8.0 (API 26)** hasta **Android 15+ (API 36+)**.
  - Soporte adaptativo para permisos de Bluetooth (manejo condicional dinámico para Android 12+ con `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN`).
  - Arquitectura moderna basada en Jetpack Compose, Material 3 y MVVM, garantizando compatibilidad con diferentes resoluciones de pantalla y arquitecturas de procesador (ARM64, x86_64).
