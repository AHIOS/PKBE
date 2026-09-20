plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.uci.pkbe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uci.pkbe"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            buildConfigField("String", "PKBE_RP_ID", "\"localhost\"")
            buildConfigField("String", "PKBE_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        create("render") {
            dimension = "env"
            isDefault = true
            buildConfigField("String", "PKBE_RP_ID", "\"pkbe-nv4k.onrender.com\"")
            buildConfigField("String", "PKBE_BASE_URL", "\"https://pkbe-nv4k.onrender.com\"")
        }
        create("tunnel") {
            dimension = "env"
            buildConfigField("String", "PKBE_RP_ID", "\"YOUR_TUNNEL_HOST\"")
            buildConfigField("String", "PKBE_BASE_URL", "\"https://YOUR_TUNNEL_HOST\"")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
