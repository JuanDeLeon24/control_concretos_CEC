# 🔨 Guía de Compilación - Control de Concretos CEC

## Requisitos Previos

### Sistema Operativo
- **Windows, macOS o Linux** con al menos 8GB de RAM
- Conexión a internet estable

### Software Requerido
- **Flutter SDK 3.24.0+**: [Descargar](https://flutter.dev/docs/get-started/install)
- **Android SDK 33-34**: Incluido con Android Studio
- **Java 17+**: [Temurin](https://adoptium.net/) o similar
- **Git**: [Descargar](https://git-scm.com/)

## Instalación Inicial

### 1. Clonar el Repositorio
```bash
git clone https://github.com/JuanDeLeon24/control_concretos_CEC.git
cd control_concretos_CEC
```

### 2. Configurar Flutter
```bash
flutter clean
flutter pub get
flutter pub upgrade
```

### 3. Verificar Requisitos
```bash
flutter doctor
```

Todos los items deben estar ✓ (en verde).

## Compilación de APK

### Opción 1: Línea de Comandos (Recomendado)
```bash
# Build release (optimizado para producción)
flutter build apk --release --verbose

# Build debug (para desarrollo)
flutter build apk --debug --verbose
```

### Opción 2: Usando GitHub Actions (Automático)
1. Ir a **Actions** en GitHub
2. Seleccionar **Compilar Flutter APK**
3. Click en **Run workflow**
4. Descargar el APK desde los artefactos

## Resolución de Problemas

### Error: "Android SDK not found"
```bash
flutter config --android-sdk-path /path/to/android/sdk
flutter pub get
```

### Error: "Theme not found"
✅ **Resuelto en esta versión**. Las themes se han actualizado a Material theme.

### Error: "Gradle compilation failed"
```bash
# Limpiar cache
flutter clean
rm -rf android/.gradle build/

# Recompile
flutter pub get
flutter build apk --release --verbose
```

### Error: "Out of Memory (OOM)"
Editar `android/gradle.properties`:
```ini
org.gradle.jvmargs=-Xmx6G -XX:MaxMetaspaceSize=2G -XX:+HeapDumpOnOutOfMemoryError
```

### Error: "Permission denied"
```bash
chmod +x gradlew
flutter build apk --release
```

## Características de la Build

✅ **Compilación Optimizada**
- Minificación ProGuard habilitada
- Tree-shaking de recursos
- Optimización de dependencias

✅ **Compatibilidad**
- API 21+ (Android 5.0+)
- Target SDK 34 (Android 14)
- Material Design 3

✅ **Seguridad**
- Signing con debug key
- ProGuard rules configuradas
- AndroidX compatible

## Estructura del Proyecto

```
control_concretos_CEC/
├── android/           # Código nativo Android
│   ├── app/          # Módulo principal
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── lib/              # Código Dart/Flutter
├── pubspec.yaml      # Dependencias Flutter
└── .github/workflows # CI/CD automation
```

## Build Output

Ubicación del APK compilado:
```
build/app/outputs/flutter-apk/app-release.apk
```

Tamaño aproximado: 50-70 MB (optimizado)

## Comandos Útiles

```bash
# Información de build
flutter build apk --release --analyze-size

# Build con verbosidad máxima (para debugging)
flutter build apk --release --verbose

# Limpiar proyecto completamente
flutter clean && rm -rf android/.gradle build/

# Ver logs en tiempo real
flutter logs
```

## Soporte

Para reportar problemas:
- Abrir un issue en GitHub
- Incluir:
  - Salida de `flutter doctor`
  - Logs completos de error
  - Sistema operativo y versión

---

**Última actualización:** Octubre 2026
**Versión Flutter:** 3.24.0
**API Target:** 34 (Android 14)
