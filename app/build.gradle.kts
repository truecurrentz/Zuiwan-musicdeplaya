plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.zuiwan"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.zuiwan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    val keystoreFile = file("keystore/release.jks")
    val hasKeystore = keystoreFile.exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "zuiwan123"
                keyAlias = System.getenv("KEY_ALIAS") ?: "zuiwan"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "zuiwan123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.datastore)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.material3)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.coil)
    implementation(libs.coil.okhttp)
    implementation(libs.palette)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    implementation(libs.media3)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.session)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.encoding)
    implementation(libs.ktor.json)

    implementation(libs.innertubex)
    implementation(libs.tinypinyin)
    implementation(libs.guava)
    implementation(libs.coroutines.guava)

    coreLibraryDesugaring(libs.desugaring)
}
