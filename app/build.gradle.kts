plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
}

val sdkRevision =
    rootProject.layout.projectDirectory.file("llmedge-sdk-revision.txt").asFile.readText().trim()

check(sdkRevision.matches(Regex("[0-9a-f]{40}"))) {
    "llmedge-sdk-revision.txt must contain one full Git revision"
}

android {
    namespace = "com.example.llmedgeexample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.llmedgeexample"
        minSdk = 30
        targetSdk = 35
        versionCode = 306
        versionName = "0.3.6"

        buildConfigField(
            "String",
            "LLMEDGE_SDK_REVISION",
            "\"$sdkRevision\""
        )

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val debugKeystore =
            file("${System.getProperty("user.home")}/.android/debug.keystore")

        if (debugKeystore.exists()) {
            create("debug_key") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "androiddebugkey"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro",
            )

            val debugKeySigning =
                signingConfigs.findByName("debug_key")

            if (debugKeySigning != null) {
                signingConfig = debugKeySigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val verifySdkRevision =
    tasks.register("verifySdkRevision") {
        doLast {
            if (
                System.getenv("LLMEDGE_SKIP_SDK_REVISION_CHECK") == "true"
            ) {
                logger.lifecycle(
                    "verifySdkRevision: SKIPPED via " +
                        "LLMEDGE_SKIP_SDK_REVISION_CHECK=true"
                )
                return@doLast
            }

            val sdkRoot = rootProject.projectDir.parentFile

            fun git(vararg arguments: String): Pair<Int, String> {
                val process =
                    ProcessBuilder(
                        "git",
                        "-C",
                        sdkRoot.absolutePath,
                        *arguments
                    )
                        .redirectErrorStream(true)
                        .start()

                val output =
                    process.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                        .trim()

                return process.waitFor() to output
            }

            val (revisionExit, checkedOutRevision) =
                git("rev-parse", "HEAD")

            check(revisionExit == 0) {
                "Unable to read the llmedge SDK revision: " +
                    checkedOutRevision
            }

            check(checkedOutRevision == sdkRevision) {
                "Release requires llmedge SDK $sdkRevision, " +
                    "but the parent checkout is $checkedOutRevision"
            }

            val (diffExit, diffOutput) =
                git(
                    "diff",
                    "--quiet",
                    "HEAD",
                    "--",
                    ".",
                    ":(exclude)llmedge-examples"
                )

            check(diffExit == 0) {
                "Release requires a clean llmedge SDK checkout" +
                    diffOutput
                        .takeIf(String::isNotBlank)
                        ?.let { ": $it" }
                        .orEmpty()
            }
        }
    }

tasks.matching {
    it.name == "preReleaseBuild"
}.configureEach {
    dependsOn(verifySdkRevision)
}

dependencies {

    // MATCHES LLMEdge V0.4.7.2 / commit 4146974
    implementation("io.github.aatricks:llmedge:0.4.7.2")

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.0"
    )

    implementation(
        "androidx.activity:activity-ktx:1.8.0"
    )

    implementation(
        "androidx.core:core-ktx:1.15.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"
    )

    testImplementation(
        "junit:junit:4.13.2"
    )

    testImplementation(
        "org.jetbrains.kotlin:kotlin-test:2.0.0"
    )

    testImplementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1"
    )

    testImplementation(
        "org.robolectric:robolectric:4.13"
    )

    testImplementation(
        "androidx.test:core:1.6.1"
    )

    androidTestImplementation(
        "androidx.test.ext:junit:1.1.5"
    )

    androidTestImplementation(
        "androidx.test:runner:1.5.2"
    )

    androidTestImplementation(
        "org.jetbrains.kotlin:kotlin-test:2.0.0"
    )

    androidTestImplementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1"
    )
}
