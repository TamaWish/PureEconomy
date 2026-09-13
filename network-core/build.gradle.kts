plugins {
    kotlin("jvm")
}

repositories { mavenCentral() }

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.yaml:snakeyaml:2.3")

    testImplementation(kotlin("stdlib"))
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test-junit5"))
    testImplementation("com.h2database:h2:2.3.232")
}

kotlin.jvmToolchain(21)
tasks.test { useJUnitPlatform() }
