// The iOS app's Kotlin side: builds the framework the Xcode project links (still named Shared, so Swift is unchanged)
// and supplies the iOS adapters: the RealityKit viewport from :renderer, and file storage for saves.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Swift implements the renderer's WorldStage, so its declarations must appear in the framework header.
            export(project(":renderer"))
        }
    }
    sourceSets {
        iosMain.dependencies {
            api(project(":renderer"))
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
