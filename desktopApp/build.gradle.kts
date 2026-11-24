import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":renderer"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    // Java Sound reads only PCM; this MIT-licensed pure-Java decoder plays the MP3 music.
    implementation(libs.javamp3)

    implementation(libs.compose.uiToolingPreview)
    testImplementation(libs.kotlin.testJunit)
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "id.branchworld.game.MainKt"

        nativeDistributions {
            modules("jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "id.branchworld.game"
            packageVersion = "1.0.0"
        }
    }
}
