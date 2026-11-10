import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

tasks.withType<Test>().configureEach {
    systemProperty("receipt.fixtures.dir", rootProject.file("fixtures").absolutePath)
    inputs.file(rootProject.file("fixtures/previews/core-nodes.receiptline")).withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":receipt-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
