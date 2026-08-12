plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

group = "io.github.darkpro1337"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "3.5.2"
val exposedVersion = "0.61.0"
val okHttpVersion = "4.12.0"
val okioVersion = "3.9.1"

// JDK 26+: sqlite-jdbc uses System.load; Gson (via telegram bot) mutates final fields
val jvmCompatArgs = listOf(
    "--enable-native-access=ALL-UNNAMED",
    "--enable-final-field-mutation=ALL-UNNAMED",
)

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:10.0.0")
    // Compile-time visibility: telegram lib exposes retrofit2.Response in public API but declares it runtime-only
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    // Override vulnerable transitive okhttp 3.14.9 / okio 1.17.2 from telegram bot stack
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    implementation("com.squareup.okio:okio:$okioVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    constraints {
        implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
        implementation("com.squareup.okio:okio:$okioVersion")
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:$okHttpVersion")
        force("com.squareup.okio:okio:$okioVersion")
    }
}

application {
    mainClass.set("io.github.darkpro1337.MainKt")
    applicationDefaultJvmArgs = jvmCompatArgs
}

kotlin {
    jvmToolchain(26)
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(jvmCompatArgs)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(jvmCompatArgs)
}
