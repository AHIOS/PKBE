plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.uci"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.yubico:webauthn-server-core:2.6.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
    options.isIncremental = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
