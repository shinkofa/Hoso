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
        google()
        mavenCentral()
    }
}

rootProject.name = "Hoso"
include(":app")

// Local fork of StreamPack 3.1.2 patched for Twitch audio race condition.
// Branch: fix/twitch-audio-race-hoso (in streampack-fork/.git)
// See: streampack-fork/core/.../EncodingPipelineOutput.kt — endpoint.startStream()
// reordered before encoder coroutines so RTMP publish() completes before the
// AAC sequence header (csd-0) is emitted.
includeBuild("streampack-fork") {
    dependencySubstitution {
        substitute(module("io.github.thibaultbee.streampack:streampack-core"))
            .using(project(":streampack-core"))
        substitute(module("io.github.thibaultbee.streampack:streampack-services"))
            .using(project(":streampack-services"))
        substitute(module("io.github.thibaultbee.streampack:streampack-rtmp"))
            .using(project(":streampack-rtmp"))
    }
}
