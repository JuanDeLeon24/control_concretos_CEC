# Guía de compilación — Control Concreto CEC

La aplicación principal de control de llegadas de concreto es el proyecto **Android nativo Kotlin/Jetpack Compose** de la raíz (`app/`). Las carpetas `android/` y `lib/` corresponden a un prototipo Flutter separado; no se usan para compilar esta aplicación.

## Compilar sin configurar llaves de firma (recomendado para probar)

1. Sube el contenido de este repositorio a GitHub.
2. Abre **Actions → Compilar APK Control Concreto → Run workflow**.
3. Descarga **ControlConcreto-APK-debug** para probar. Si están los cuatro secretos de firma, también se publica **ControlConcreto-APK-release**.

El flujo instala Java 17, Gradle 8.7 y Android SDK 34, compila el módulo `:app` y verifica la firma. El APK debug no requiere secretos; el release requiere `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`.

## Compilar desde Android Studio

1. Instala Android Studio y el Android SDK Platform 34 / Build Tools 34.0.0.
2. Abre la carpeta raíz, identificada por `settings.gradle.kts`, `build.gradle.kts` y `app/`.
3. Espera la sincronización y ejecuta **Build → Build APK(s)**.
4. El APK de depuración se guarda en `app/build/outputs/apk/debug/app-debug.apk`.

## APK release y actualizaciones

Para producir un APK release que se instale encima de una versión ya instalada, configura `keystore.properties` con la llave de firma original, el alias y las contraseñas. No compartas esas credenciales. Un APK debug se firma con la llave de depuración y normalmente no puede actualizar una instalación firmada con otra llave.

## Versiones del proyecto

- Java: 17
- Android Gradle Plugin: 8.5.2
- Gradle: 8.7
- Kotlin: 2.0.20
- Compile SDK: 34
- Minimum SDK: 26
