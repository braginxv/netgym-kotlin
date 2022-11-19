
### This is the concise wrapper around the Netgym network library (https://github.com/braginxv/netgym) for more convenient use in Kotlin

To add this library to your project insert into the gradle build script (in Gradle Kotlin notation):

```
repositories {
    maven {
        name = "Sonatype snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencies {
    implementation("com.github.braginxv:netgym-kotlin:0.5-SNAPSHOT")
}
```
