plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)

    `maven-publish`
    application
}

group = "net.casual"
version = "0.1.0"

application.mainClass.set("net.casual.bot.CasualBot")

repositories {
    mavenCentral()
    maven("https://maven.supersanta.me/snapshots")
    maven("https://jitpack.io/")
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

tasks.shadowJar {
    // from("LICENSE")

    // archiveFileName.set("${rootProject.name}-${archiveVersion.get()}.jar")
}