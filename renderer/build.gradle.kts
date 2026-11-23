import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_17
    }
    js {
        browser {
            // Renderer logic tests run on Node.js; the real browser path is checked by running the web app.
            testTask { enabled = false }
        }
        nodejs {
            testTask { useMocha { timeout = "300s" } }
        }
    }
    android {
        namespace = "id.branchworld.renderer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget = JvmTarget.JVM_17
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    // Kool draws the world on Desktop, Android and the browser; iOS has its own backend, so commonMain stays engine-free
    // (camera, picking, gestures, geometry). Desktop, the browser and iOS draw Compose with Skiko and share pointer input.
    applyDefaultHierarchyTemplate {
        common {
            group("kool") {
                withJvm()
                withJs()
                // The KMP Android library target has no named matcher of its own.
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
            group("skiko") {
                withJvm()
                withJs()
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        // iOS draws Compose with Skiko too, and shares its pointer input; the default hierarchy cannot express that
        // diamond, so iosMain is linked by hand.
        iosMain.get().dependsOn(getByName("skikoMain"))
        getByName("koolMain").dependencies {
            implementation(libs.kool.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        iosTest.dependencies {
            implementation(libs.compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(compose.desktop.currentOs)
        }
    }
}
