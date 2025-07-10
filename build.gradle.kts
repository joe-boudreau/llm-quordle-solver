plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.seleniumhq.selenium:selenium-java:4.33.0")
    implementation("org.seleniumhq.selenium:selenium-chrome-driver:4.33.0")
    implementation("org.seleniumhq.selenium:selenium-support:4.33.0")
    implementation("com.aallam.openai:openai-client:4.0.1")
    implementation("io.ktor:ktor-client-cio:3.2.0")
}

application {
    mainClass.set("MainKt") // This should match your main function's location
}


java {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

// Add Kotlin compiler options to ensure compatibility with Java 22
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "22"
        languageVersion = "2.1"
        apiVersion = "2.1"
    }
}
