plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

repositories { maven("https://repo.papermc.io/repository/maven-public/") }

dependencies {
    implementation(project(":network-core"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation(kotlin("stdlib"))
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.yaml:snakeyaml:2.3")
}

kotlin.jvmToolchain(21)
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("PureEconomy-Velocity-${rootProject.version}.jar")
    relocate("com.zaxxer.hikari", "io.github.tamawish.pureeconomy.lib.hikari")
    relocate("org.yaml.snakeyaml", "io.github.tamawish.pureeconomy.lib.snakeyaml")
    relocate("com.mysql", "io.github.tamawish.pureeconomy.lib.mysql")
    relocate("com.google.protobuf", "io.github.tamawish.pureeconomy.lib.protobuf")
}
tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
