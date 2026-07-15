plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.logback.classic)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql.driver)
    implementation(libs.hikaricp)
    // Enables DATABASE_URL to use ?cloudSqlInstance=...&socketFactory=... to reach Cloud SQL
    // via IAM + mutual TLS instead of a public IP — see DatabaseFactory.kt kdoc.
    implementation(libs.cloud.sql.postgres.socket.factory)

    implementation(libs.auth0.java.jwt)
    implementation(libs.auth0.jwks.rsa)

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.vtempe.server.app.ApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8"
    )
}

