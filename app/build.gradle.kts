import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// The JSON stays private/local; a stale copy must not silently connect a teammate to another project.
providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull?.let { contents ->
    val config = JsonSlurper().parseText(contents) as Map<*, *>
    val settings = JsonSlurper().parseText(
        providers.fileContents(rootProject.layout.projectDirectory.file(".firebaserc")).asText.get()
    ) as Map<*, *>
    val expected = (settings["projects"] as Map<*, *>)["default"]
    val actual = (config["project_info"] as Map<*, *>)["project_id"]
    require(actual == expected) {
        "app/google-services.json pertenece a '$actual'. Descarga el archivo del proyecto Firebase '$expected' y reemplázalo antes de compilar."
    }
}

android {
    namespace = "com.example.detalmick"
    compileSdk = 37

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.detalmick"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", providers.gradleProperty("useFirebaseEmulators").orElse("false").get())
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.functions)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
