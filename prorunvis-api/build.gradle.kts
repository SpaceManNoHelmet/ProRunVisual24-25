plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id ("io.spring.dependency-management") version "1.1.3"
}

apply(plugin = "io.spring.dependency-management")

repositories {
    mavenCentral()
}

dependencies {
    // For JUnit 5 tests
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // --- Removed Postgres and JPA below ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")  // important

    runtimeOnly("com.h2database:h2")

    // Spring Boot (unified to version 3.2.0)

    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.0"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JavaParser, Gson, Commons IO
    implementation("com.github.javaparser:javaparser-core:3.25.7")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("commons-io:commons-io:2.15.1")

    // Local modules
    implementation(project(":prorunvis"))
    implementation(project(":frontend"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copyWebApp") {
    from("../frontend/build")
    into("build/resources/main/static")
}

tasks.named("copyWebApp") {
    dependsOn(project(":frontend").tasks["appNpmBuild"])
}

tasks.named("compileJava") {
    dependsOn(tasks["copyWebApp"])
}