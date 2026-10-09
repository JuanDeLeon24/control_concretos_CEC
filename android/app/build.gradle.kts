import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val isReleaseTask = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

android {
    namespace = "co.obra.controlconcreto"
    compileSdk = 34
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "co.obra.controlconcreto"
        minSdk = 21
        targetSdk = 34
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val storeFileValue = requireNotNull(keystoreProperties.getProperty("storeFile")) {
                    "Missing storeFile in keystore.properties"
                }
                val storePasswordValue = requireNotNull(keystoreProperties.getProperty("storePassword")) {
                    "Missing storePassword in keystore.properties"
                }
                val keyAliasValue = requireNotNull(keystoreProperties.getProperty("keyAlias")) {
                    "Missing keyAlias in keystore.properties"
                }
                val keyPasswordValue = requireNotNull(keystoreProperties.getProperty("keyPassword")) {
                    "Missing keyPassword in keystore.properties"
                }

                storeFile = rootProject.file(storeFileValue)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        } else if (isReleaseTask) {
            throw GradleException(
                "Release build requested but keystore.properties is missing. Add the file or provide GitHub Actions secrets."
            )
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/proguard/androidx-*.pro",
                "META-INF/androidx.*.version"
            )
        }
    }

    lint {
        disable += "MissingDimensionBaselineProfileContentProvider"
    }
}

flutter {
    source = "../.."
}
