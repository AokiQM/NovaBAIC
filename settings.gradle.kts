pluginManagement {
    repositories {
        google()
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

rootProject.name = "BetterAIChat2"

include(":app")

include(":core:model")
include(":core:data")
include(":core:network")
include(":core:engine")
include(":core:runtime")
include(":core:designsystem")

include(":feature:chat")
include(":feature:conversations")
include(":feature:tasks")
include(":feature:settings")
include(":feature:agents")
include(":feature:library")

include(":device:api")
include(":device:impl")

include(":tools")
include(":mcp")
include(":eval")
