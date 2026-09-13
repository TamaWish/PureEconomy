plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
    maven("https://repo.md-5.net/content/repositories/releases/")
    maven("https://libraries.minecraft.net/")
}

dependencies {
    implementation(project(":network-core"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    implementation(kotlin("stdlib"))
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.yaml:snakeyaml:2.3")
}

kotlin.jvmToolchain(21)
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("PureEconomy-Bungee-${rootProject.version}.jar")
    relocate("com.zaxxer.hikari", "io.github.tamawish.pureeconomy.lib.hikari")
    relocate("org.yaml.snakeyaml", "io.github.tamawish.pureeconomy.lib.snakeyaml")
    relocate("com.mysql", "io.github.tamawish.pureeconomy.lib.mysql")
    relocate("com.google.protobuf", "io.github.tamawish.pureeconomy.lib.protobuf")
}
tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
