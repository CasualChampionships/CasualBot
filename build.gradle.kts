plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"

    id("com.github.johnrengelman.shadow") version "7.1.2"

    `maven-publish`
    application
}

group = "net.casual"
version = "0.0.1"

application.mainClass.set("net.casual.bot.CasualBot")

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    maven("https://repo.fruxz.dev/releases/")
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.coroutines)

    implementation(libs.jda)
    implementation(libs.jda.ktx)
    implementation(libs.json)

    implementation(libs.logback)
    implementation(libs.klogging)

    implementation(libs.mojank)

    implementation(libs.casual.database)

    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.negotiation)
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
}

tasks.shadowJar {
    // from("LICENSE")

    // archiveFileName.set("${rootProject.name}-${archiveVersion.get()}.jar")
}