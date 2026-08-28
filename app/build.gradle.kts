plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.jason.dnsrouter"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.jason.dnsrouter"
        minSdk = 29
        targetSdk = 35
        versionCode = 17
        versionName = "1.98"
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.android.gms:play-services-cronet:18.1.1")
}
