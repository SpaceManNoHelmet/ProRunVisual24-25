plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
}

apply(plugin = "io.spring.dependency-management")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation ("org.postgresql:postgresql:42.2.23")
    //spring boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:2.7.8"))

    implementation ("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.javaparser:javaparser-core:3.25.7")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.7")
    implementation("com.google.code.gson:gson:2.10.1")
    //commons
    implementation("commons-io:commons-io:2.15.1")

    implementation(project(":prorunvis"))
    //frontend submodule
    implementation(project(":frontend"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copyWebApp"){
    from("../frontend/build")
    into("build/resources/main/static")
}

tasks.named("copyWebApp"){
    dependsOn(project(":frontend").tasks["appNpmBuild"])
}

tasks.named("compileJava"){
    dependsOn(tasks["copyWebApp"])
}