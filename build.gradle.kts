val netgymVersion by extra("1.0.0-SNAPSHOT")
val coroutinesVersion by extra("1.6.1")
val kotlinVersion: String by project

group = "com.github.braginxv"
version = "0.5-SNAPSHOT"

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka") version "1.7.20"
  `java-library`
  `maven-publish`
  signing
}


repositories {
  mavenCentral()
  google()
  gradlePluginPortal()
  maven {
    name = "Sonatype snapshots"
    url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
  }
}

java {
  withSourcesJar()
  withJavadocJar()
}

val javadocJar = tasks.named<Jar>("javadocJar") {
  from(tasks.named("dokkaJavadoc"))
}

publishing {
  publications {
    create<MavenPublication>("netgym-kotlin") {
      from(components["java"])

      pom {
        packaging = "jar"
        name.set("Netgym network library for Kotlin")
        url.set("https://github.com/braginxv/netgym-kotlin")

        description.set(
          """High performance asynchronous network library for a client side of jvm-apps (including Android).
            It provides handling of a large number of parallel connections (TCP, UDP) using a single client instance."""
            .trimMargin()
        )

        scm {
          connection.set("scm:https://github.com/braginxv/netgym-kotlin.git")
          developerConnection.set("scm:git@github.com:braginxv/netgym-kotlin.git")
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
      credentials {
        val userName: String? by project
        val userPassword: String? by project

        username = userName
        password = userPassword
      }
    }
  }
}

signing {
  val signingKey: String? by project
  val signingPassword: String? by project
  useInMemoryPgpKeys(signingKey, signingPassword)
  sign(configurations.archives.get())
}

tasks.test {
  useJUnitPlatform()
}

dependencies {
  api("com.github.braginxv:netgym:${netgymVersion}")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
  testImplementation("org.jetbrains.kotlin:kotlin-test:${kotlinVersion}")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
  testImplementation("io.mockk:mockk:1.12.8")
}
