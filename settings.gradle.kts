
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


rootProject.name = "MCP server"
include(":app")
include(":tools:sms")
include(":tools:smsintent")
include(":tools:ads")
include(":tools:externaltools")
include(":tools:contacts")
include(":tools:sensor")
include(":tools:camera")
include(":core")
include(":externalmcptool")
include(":mcp-provider")
