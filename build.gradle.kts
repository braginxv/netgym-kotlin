val netgymVersion by extra("0.9")
val coroutinesVersion by extra("1.6.1")
val kotlinVersion: String by project

group = "com.github.braginxv"
version = "0.5-SNAPSHOT"

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("netgym-kotlin") {
            from(components["java"])
//            artifactId = "netgym-kotlin"
//            groupId = "com.github.braginxv"

            pom {
                packaging = "jar"
                name.set("Example Application")
                url.set("https://github.com/braginxv/netgym-kotlin")

                description.set(
                    "Full-featured high performance asynchronous network library for a client side of jvm-apps (including Android).\n" +
                            "It provides handling of a large number of parallel connections (TCP, UDP) using a single client instance."
                )

                scm {
                    connection.set("scm:git:https://github.com/braginxv/netgym-kotlin")
                    developerConnection.set("scm:git:https://github.com/braginxv/netgym-kotlin")
                    url.set("https://github.com/braginxv/netgym-kotlin")
                    tag.set("HEAD")
                }

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("braginxv")
                        name.set("Vladimir Bragin")
                        email.set("uncloudedvm@gmail.com")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
//            credentials {
//                username = project.properties["braginxv"].toString()
//                password = project.properties["ossrhPassword"].toString()
//            }
        }
    }
}

signing {
    useGpgCmd()
    sign(configurations.archives.get())
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    api("com.github.braginxv:netgym:${netgymVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
//    implementation("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
