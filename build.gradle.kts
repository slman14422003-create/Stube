plugins {
    id("com.android.application") version "9.5.3" apply false
    id("com.android.test") version "9.5.3" apply false           // :benchmark (same version as AGP)
    id("androidx.baselineprofile") version "1.5.0" apply false   // generates and packages Baseline Profiles
    // AGP 9 يحمل دعم Kotlin مدمجاً (لا حاجة لـ org.jetbrains.kotlin.android). مكوّن Compose يجب أن يطابق نسخة Kotlin التي يعتمد عليها AGP 9.2
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
