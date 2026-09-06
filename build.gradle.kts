plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.optionalconverter"
version = "1.0.2"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("213")
        untilBuild.set("253.*")
    }

    publishPlugin {
        token.set(providers.gradleProperty("intellijPublishToken"))
    }

    buildSearchableOptions {
        enabled = false
    }
}
