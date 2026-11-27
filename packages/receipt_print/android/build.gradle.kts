group = "dev.receiptprint.receipt_print"
version = file("../engine-version.txt").readText().trim()

buildscript {
    val kotlinVersion = "2.4.10"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

val receiptMavenRepository = layout.projectDirectory.dir("maven")

if (providers.gradleProperty("receiptPrintConfigureRepositories").orNull != "false") {
    rootProject.allprojects {
        repositories {
            exclusiveContent {
                forRepository { maven { url = uri(receiptMavenRepository) } }
                filter { includeGroup("receipt-kmp") }
            }
        }
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "dev.receiptprint.receipt_print"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
            // Both platforms package the same ReceiptLine source and license.
            assets.srcDir("../ios/receipt_print/Sources/receipt_print/Resources")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("receipt-kmp:receipt-engine-jvm:${project.version}")
    testImplementation("junit:junit:4.13.2")
}
