plugins {
    java
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "io.github.tamawish"
version = "1.2.0"
description = "Lightweight multi-currency economy for Paper, Purpur and Folia."

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.helpch.at/releases")
}

dependencies {
    implementation(project(":network-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")

    compileOnly(kotlin("stdlib"))
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.0")
    compileOnly("org.xerial:sqlite-jdbc:3.47.2.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    implementation("dev.dejvokep:boosted-yaml:1.3.7")

    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(kotlin("stdlib"))
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    testImplementation("org.xerial:sqlite-jdbc:3.47.2.0")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0")
    }
}

tasks {
    processResources {
        val props = mapOf("project" to mapOf("version" to version))
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("PureEconomy-${project.version}.jar")
        relocate("org.bstats", "io.github.tamawish.pureeconomy.lib.bstats")
        relocate("dev.dejvokep", "io.github.tamawish.pureeconomy.lib.boostedyaml")
        relocate("org.snakeyaml.engine", "io.github.tamawish.pureeconomy.lib.snakeyamlengine")
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("com.zaxxer:HikariCP:.*"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("com.mysql:mysql-connector-j:.*"))
            exclude(dependency("com.google.protobuf:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

// The root project remains the backwards-compatible Bukkit artifact. Proxy artifacts are built by
// :velocity and :bungee, while :network-core contains the platform-neutral transactional engine.
