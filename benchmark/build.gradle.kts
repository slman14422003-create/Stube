import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.stube.benchmark"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 35
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

baselineProfile { useConnectedDevices = true }

dependencies {
    // MacrobenchmarkRule, BaselineProfileRule and (transitively) UiAutomator
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    // AndroidJUnit4 runner annotation
    implementation("androidx.test.ext:junit:1.3.0")
}

// Passes the real applicationId of the APK under test to the tests
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)!!.applicationId }
        )
    }
}
