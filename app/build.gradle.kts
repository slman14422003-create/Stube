import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------- التوقيع: متغيرات بيئة (CI) أو ملف keystore.properties محلي (غير مرفوع) ----------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)
val ksPath = signingValue("KEYSTORE_PATH", "storeFile")
val ksPass = signingValue("KEYSTORE_PASSWORD", "storePassword")
val ksAlias = signingValue("KEY_ALIAS", "keyAlias")
val ksKeyPass = signingValue("KEY_PASSWORD", "keyPassword") ?: ksPass
val hasReleaseSigning = ksPath != null && ksPass != null && ksAlias != null && rootProject.file(ksPath).exists()

android {
    namespace = "com.nova.browser"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "com.stube.app"
        minSdk = 35
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        // مستودع التحديثات: يُملأ تلقائياً في GitHub Actions (owner/repo)، فارغ محلياً = التحديث معطّل
        buildConfigField("String", "UPDATE_REPO", "\"" + (System.getenv("GITHUB_REPOSITORY") ?: "") + "\"")
        ndk { abiFilters += listOf("arm64-v8a") }   // يقلّل حجم الـ APK كثيراً (مكتبات FFmpeg)
    }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = rootProject.file(ksPath!!)
            storePassword = ksPass
            keyAlias = ksAlias
            keyPassword = ksKeyPass
        }
    }
    androidResources { localeFilters += listOf("ar", "en", "fr", "es", "de", "tr", "ru", "fa", "ur", "id", "zh", "ja", "ko", "hi", "pt", "it", "bn", "he", "th", "vi", "nl", "pl", "uk") }   // نصوص المكتبات (مربعات التاريخ/الحوارات…) باللغات الشائعة فقط لتبقى الحزمة صغيرة
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { jniLibs { pickFirsts += "**/libc++_shared.so"; useLegacyPackaging = true } }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST") } }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"      // يتعايش مع نسخة الإصدار على نفس الجهاز
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // بلا مفاتيح (مثل طلبات الدمج من forks) يُوقَّع بمفتاح debug كي لا يفشل البناء
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }   // BuildConfig مطلوب لـ Updater (كان سبب الخطأ)
}

// Kotlin مدمج في AGP 9؛ نثبّت هدف JVM صراحةً كي لا يتبع JDK البناء (25)
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi", "-opt-in=kotlinx.coroutines.FlowPreview")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.tracing:tracing-ktx:1.2.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("androidx.window:window:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-splashscreen:1.2.0")   // 1.0.1 كان يُسقط أحياناً عند الخروج (NPE داخلي في مزامنة حركة الأيقونة)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:157.+")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")           // شبكة HTTP/2 مع كاش وإعادة استخدام الاتصالات (اقتراحات البحث)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1") // ملفات Baseline لتسريع بدء التشغيل وتقليل التقطيع
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")   // تحويل الصوت إلى MP3 وغيره (FFmpeg)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") // استخراج روابط الفيديو/الصوت من يوتيوب (مجاني ومفتوح المصدر)
    implementation("androidx.metrics:metrics-performance:1.0.0")   // JankStats: رصد الإطارات البطيئة (يُفعَّل في نسخة debug فقط)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")   // كشف تسرّب الذاكرة (Activity) أثناء التطوير فقط؛ لا يدخل نسخة الإصدار
    testImplementation("junit:junit:4.13.2")
}

val syncShieldList by tasks.registering(Copy::class) {
    from("../tools/src/blocklist.txt")
    into("src/main/assets/extensions/semo")
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncShieldList) }
