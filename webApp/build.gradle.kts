plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "branchworld.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":renderer"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

// Kool fetches models and textures over HTTP from ./assets, next to index.html. The renderer module keeps the
// single copy of those files; the web bundle only mirrors them at build time.
tasks.named<Copy>("jsProcessResources") {
    from(rootProject.layout.projectDirectory.dir("renderer/src/commonMain/resources/diorama")) {
        into("assets/diorama")
    }
}
