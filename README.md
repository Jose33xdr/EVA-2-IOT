# EVA-2-IOT

Aplicación Android para monitoreo y control de riego de plantas (IoT).
Proyecto de la Unidad 2 — Aplicaciones Móviles Android.

## Tecnologías
- Kotlin + Jetpack Compose (Material 3), arquitectura MVVM, Navigation Compose
- Firebase Authentication y Cloud Firestore
- Conectividad Bluetooth SPP para ESP32 / Arduino con reconexión automática inteligente
- Servicio en primer plano (simulador de dispositivos ESP32) y notificaciones de alerta en tiempo real

## Funcionalidades
- Registro, login seguro (bloqueo tras intentos fallidos), recuperación de contraseña y sesión persistente
- Gestión de plantas con sensores asignables y umbrales configurables por planta
- Control automático y manual de bomba de agua
- Alerta de temperatura con notificación emergente, vibración y protocolo de emergencia
- Conexión Bluetooth SPP con búsqueda de dispositivos vinculados, permisos de tiempo de ejecución (Android 12+) y reconexión automática configurable
- Roles usuario / administrador (usuarios, plantas, sensores, registros)

## Cómo ejecutar
1. Abre el proyecto con Android Studio.
2. Crea un proyecto en Firebase, registra la app con el paquete `com.app.smartgarden` y habilita
   *Authentication* (correo/contraseña y acceso anónimo) y *Firestore*.
3. Descarga tu `google-services.json` y colócalo en `app/` (ver `google-services.json.example`).
4. Publica las reglas de seguridad de Firestore del proyecto.
5. Ejecuta la app en un emulador o dispositivo (Android 8+).

## Nota
`google-services.json` no se incluye en el repositorio por ser configuración privada del proyecto Firebase.
