plugins {
    kotlin("jvm") version "1.9.24"

    id("com.github.johnrengelman.shadow") version "7.1.2"

    `maven-publish`
    application
}

group = "net.casualuhc"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.20")
    implementation("club.minnced:jda-ktx:0.11.0-beta.20")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.mongodb:mongo-java-driver:3.12.11")
    implementation("com.github.SparklingComet:java-mojang-api:-SNAPSHOT")

    testImplementation(kotlin("test"))
}

tasks.jar {
    enabled = false

    archiveClassifier.set("default")
}

tasks.shadowJar {
    // from("LICENSE")

    // archiveFileName.set("${rootProject.name}-${archiveVersion.get()}.jar")
}

tasks.distTar {
    dependsOn("shadowJar")
}

tasks.distZip {
    dependsOn("shadowJar")
}

tasks.startScripts {
    dependsOn("shadowJar")
}

application {
    mainClass.set("UHCBotKt")
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifact(tasks["shadowJar"])
        }
    }
}