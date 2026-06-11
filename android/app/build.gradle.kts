import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val repoRoot = layout.projectDirectory.dir("../..")
val generatedRustJniLibs = layout.buildDirectory.dir("generated/rustJniLibs")
val hostRustLibraryName = when {
    OperatingSystem.current().isMacOsX -> "libmcv_uniffi.dylib"
    OperatingSystem.current().isWindows -> "mcv_uniffi.dll"
    else -> "libmcv_uniffi.so"
}
val hostRustLibrary = repoRoot.file("target/debug/$hostRustLibraryName")

android {
    namespace = "app.multicardvault"
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "app.multicardvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDir(repoRoot.dir("bindings/kotlin"))
            jniLibs.srcDir(generatedRustJniLibs)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("net.java.dev.jna:jna:5.19.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.java.dev.jna:jna:5.19.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.register<Exec>("buildHostRustLibrary") {
    workingDir = repoRoot.asFile
    commandLine("cargo", "build", "-p", "mcv-uniffi")
    outputs.file(hostRustLibrary)
}

tasks.register<Exec>("buildAndroidRustLibraries") {
    workingDir = repoRoot.asFile
    commandLine(
        "cargo",
        "ndk",
        "-t",
        "arm64-v8a",
        "-t",
        "armeabi-v7a",
        "-t",
        "x86_64",
        "-o",
        generatedRustJniLibs.get().asFile.absolutePath,
        "build",
        "-p",
        "mcv-uniffi",
        "--release",
    )
    inputs.file(repoRoot.file("Cargo.toml"))
    inputs.file(repoRoot.file("Cargo.lock"))
    inputs.dir(repoRoot.dir("crates"))
    outputs.dir(generatedRustJniLibs)
}

tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders" }
    .configureEach {
        dependsOn("buildAndroidRustLibraries")
    }

tasks.withType<Test>().configureEach {
    dependsOn("buildHostRustLibrary")
    systemProperty("uniffi.component.mcv_uniffi.libraryOverride", hostRustLibrary.asFile.absolutePath)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
    }
}
