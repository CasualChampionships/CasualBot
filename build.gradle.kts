import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"

    id("com.github.johnrengelman.shadow") version "7.1.2"

    `maven-publish`
    application
}

group = "net.casual"
version = "0.0.1"

application.mainClass.set("CasualBotKt")

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.coroutines)

    implementation(libs.jda)
    implementation(libs.jda.ktx)
    implementation(libs.kmongo)
    implementation(libs.okhttp)
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
}

tasks.shadowJar {
    // from("LICENSE")

    // archiveFileName.set("${rootProject.name}-${archiveVersion.get()}.jar")
}