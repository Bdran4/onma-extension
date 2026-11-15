import java.util.Properties

plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

val cfg = Properties().apply { file("config.properties").inputStream().use { load(it) } }
val suffix = cfg.getProperty("pkgSuffix")
val code = cfg.getProperty("versionCode").toInt()

android {
    namespace = "eu.kanade.tachiyomi.extension.$suffix"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.$suffix"
        minSdk = 21
        targetSdk = 34
        versionCode = code
        versionName = "1.4.$code"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly("com.github.keiyoushi:extensions-lib:18a8e26be2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
    compileOnly("org.jsoup:jsoup:1.16.2")
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    compileOnly("com.github.mihonapp:injekt:91edab2317")
}
