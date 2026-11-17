plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.jwillhub.vault"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.jwillhub.vault"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    signingConfigs {
        // The private key is unlocked by the workflow; without it the release build is left unsigned.
        val keystore = System.getenv("VAULT_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KS_PASS")
                keyAlias = "androiddebugkey"
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            // A release build is not debuggable, so adb cannot attach to the app or read its data.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// No runtime libraries besides the Kotlin standard library: the app uses only what Android itself provides.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
