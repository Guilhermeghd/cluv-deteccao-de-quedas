import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// le o local.properties (onde fica a chave do clima, fora do codigo)
val propriedadesLocais = Properties()
val arquivoLocal = rootProject.file("local.properties")
if (arquivoLocal.exists()) {
    arquivoLocal.inputStream().use { propriedadesLocais.load(it) }
}

android {
    namespace = "com.example.cluv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.cluv"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // vira BuildConfig.OPENWEATHER_KEY no codigo Java (vazio se nao estiver no local.properties)
        val chaveClima = propriedadesLocais.getProperty("OPENWEATHER_KEY", "")
        buildConfigField("String", "OPENWEATHER_KEY", "\"" + chaveClima + "\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.osmdroid)   // mapa do OpenStreetMap (sem chave)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}