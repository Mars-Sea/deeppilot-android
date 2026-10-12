pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "deeppilot-android"

// `core` is plain Kotlin/JVM: protocol, pairing, signing input, TLS pinning and the
// bridge client. It builds and tests without an Android SDK.
include(":core")

// `app` needs an Android SDK. Without one (sdk.dir in local.properties or
// ANDROID_HOME / ANDROID_SDK_ROOT) only `core` is configured, so the protocol
// layer can still be built and tested anywhere.
val localSdkDir = file("local.properties").takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.trim().startsWith("sdk.dir") }
val hasAndroidSdk = localSdkDir != null ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.warn("No Android SDK found: configuring :core only.")
}
