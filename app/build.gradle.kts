import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseKeystoreFile = file("keystore/release.jks")

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.zuiwan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.zuiwan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        val lastfmKey = System.getenv("LASTFM_API_KEY") ?: ""
        val lastfmSecret = System.getenv("LASTFM_SECRET") ?: ""
        buildConfigField("String", "LASTFM_KEY", "\"$lastfmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")
    }

    signingConfigs {
        if (releaseKeystoreFile.exists()) {
            create("release") {
                storeFile = releaseKeystoreFile
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
            isCrunchPngs = false
            isDebuggable = false
            signingConfig =
                if (releaseKeystoreFile.exists()) {
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
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.configureEach {
    exclude(group = "org.json", module = "json")
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
    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.encoding)
    implementation(libs.ktor.json)
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.innertubex)
    implementation(libs.tinypinyin)
    coreLibraryDesugaring(libs.desugaring)
}
