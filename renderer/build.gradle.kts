import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // Desktop and the browser both draw Compose with Skiko and share its pointer and key input.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withJs()
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kool.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(compose.desktop.currentOs)
        }
    }
}
