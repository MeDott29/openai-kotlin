plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "com.example.swipeai"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // OpenAI client
    implementation("com.aallam.openai:openai-client:3.6.3")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    
    // JGraphT for graph operations
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    
    // JSON parsing
    implementation("org.json:json:20231013")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.swipeai.SimpleAppKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
} 