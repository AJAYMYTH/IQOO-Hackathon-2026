import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val sdkDirProp = localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
val ndkFolder = if (sdkDirProp != null) file("$sdkDirProp/ndk/27.2.12479018") else null
val hasValidNdk = ndkFolder != null && ndkFolder.exists() && file("$ndkFolder/source.properties").exists()

android {
    namespace = "com.apexos.repoguardian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apexos.repoguardian"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val githubClientId = localProperties.getProperty("GITHUB_CLIENT_ID")
            ?: System.getenv("GITHUB_CLIENT_ID")
            ?: "Ov23liZPOsPuWwr6VQTG"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        
        if (hasValidNdk) {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
            externalNativeBuild {
                cmake {
                    arguments.addAll(listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DGGML_OPENMP=OFF",
                        "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16",
                        "-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG -fomit-frame-pointer",
                        "-DCMAKE_CXX_FLAGS_RELEASE=-O3 -DNDEBUG -fomit-frame-pointer",
                        "-DCMAKE_C_FLAGS_DEBUG=-O3 -DNDEBUG -fomit-frame-pointer",
                        "-DCMAKE_CXX_FLAGS_DEBUG=-O3 -DNDEBUG -fomit-frame-pointer",
                        "-DLLAMA_BUILD_COMMON=ON",
                        "-DLLAMA_BUILD_TESTS=OFF",
                        "-DLLAMA_BUILD_EXAMPLES=OFF",
                        "-DLLAMA_BUILD_SERVER=OFF",
                        "-DLLAMA_BUILD_TOOLS=OFF",
                        "-DBUILD_SHARED_LIBS=OFF"
                    ))
                    cppFlags.addAll(listOf("-std=c++17", "-O3", "-DNDEBUG"))
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    if (hasValidNdk) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        ndkVersion = "27.2.12479018"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")

    // Compose BOM & UI
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Navigation & Lifecycle
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit & Moshi & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore & Coroutines
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

