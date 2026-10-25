import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val customKeystoreFile = file("keystore/release.jks")
val bundledKeystoreFile = file("signing/zuiwan.p12")
val activeKeystoreFile = if (customKeystoreFile.exists()) customKeystoreFile else bundledKeystoreFile
val autoVersionCode: Int =
    ((System.currentTimeMillis() / 1_000L - 1_700_000_000L) / 10L)
        .toInt()
        .coerceAtLeast(100)
val ciRefName: String = System.getenv("GITHUB_REF_NAME").orEmpty()
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val autoVersionName: String = when {
    ciRefName.startsWith("v") && ciRefName.length > 1 -> ciRefName.removePrefix("v")
    ciRunNumber != null && ciRunNumber > 0 -> "1.0.$ciRunNumber"
    else -> "1.0.0"
}
val currentGitSha: String =
    runCatching {
        providers
            .exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }
            .standardOutput
            .asText
            .get()
            .trim()
            .ifBlank { "local" }
    }.getOrDefault("local")

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
        versionCode = autoVersionCode
        versionName = autoVersionName
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GIT_SHA", "\"$currentGitSha\"")
        buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L")
        buildConfigField("String", "GITHUB_REPO", "\"truecurrentz/Zuiwan-musicdeplaya\"")
    }

    signingConfigs {
        create("release") {
            storeFile = activeKeystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "zuiwan123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "zuiwan"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "zuiwan123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.haze)
    implementation(libs.haze.blur)
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
