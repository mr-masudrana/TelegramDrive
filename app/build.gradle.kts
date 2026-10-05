import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// api_id/api_hash কখনো সোর্স কোডে hardcode হয় না। দুটো উৎস থেকে আসতে পারে:
//  1) local.properties (লোকাল ডেভে, .gitignore করা, কখনো push হয় না)
//  2) -PtgApiId=... -PtgApiHash=... (CI/GitHub Actions থেকে, Secrets দিয়ে পাস করা)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val tgApiId: String = (project.findProperty("tgApiId") as String?)
    ?: localProps.getProperty("tg.api.id") ?: "0"
val tgApiHash: String = (project.findProperty("tgApiHash") as String?)
    ?: localProps.getProperty("tg.api.hash") ?: ""

android {
    namespace = "com.telegramdrive.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.telegramdrive.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("int", "TG_API_ID", tgApiId)
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // TDLib .so files go here once built:
    // app/src/main/jniLibs/arm64-v8a/libtdjni.so, armeabi-v7a, x86, x86_64
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0") // in-app image viewer
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // TDLib: NOT on Maven. Either:
    //   (a) implementation(files("libs/tdlib.jar"))  // if you build TdApi.java yourself into a jar
    //   (b) or put TdApi.kt/java sources directly under src/main/java (see README)
}
