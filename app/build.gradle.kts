plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.jason.dnsrouter"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jason.dnsrouter"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
}
