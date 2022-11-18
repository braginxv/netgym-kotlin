rootProject.name = "netgym-kotlin"

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        `java-library`
        `maven-publish`
        signing
    }
}
