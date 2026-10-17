pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "LocalRouteAndroid"
include(":app")
// A separate UID for end-to-end VpnService tests; never included in Local Route APK.
include(":networkprobe")
