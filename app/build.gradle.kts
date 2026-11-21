import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/*
 * Release signing key, from keystore.properties at the project root (local builds, never
 * committed) or else from environment variables (GitHub Actions). Without either, the release
 * APK is left unsigned: the F-Droid build server signs it itself.
 */
val releaseSigning: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        props.stringPropertyNames().associateWith { props.getProperty(it) }
    } else {
        System.getenv("PBILL_KEYSTORE_FILE")?.let { path ->
            val password = System.getenv("PBILL_KEYSTORE_PASSWORD").orEmpty()
            mapOf(
                "storeFile" to path,
                "storePassword" to password,
                "keyAlias" to System.getenv("PBILL_KEY_ALIAS").orEmpty(),
                // PKCS12 keystores use the same password for the key
                "keyPassword" to (System.getenv("PBILL_KEY_PASSWORD") ?: password),
            )
        }
    }
}

android {
    namespace = "net.cardolan.pbill"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.cardolan.pbill"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The dependency metadata block is encrypted with a Google key, rejected by F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
    }
}

ksp {
    // Exported schemas are kept in git to write and test future migrations
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
