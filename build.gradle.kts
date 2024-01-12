val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val h2_version: String by project
plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

group = "subit"
version = "0.0.1"

application {
    mainClass.set("subit.ForumBackend")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm") // core
    implementation("io.ktor:ktor-server-auth-jvm") // 登陆验证
    implementation("io.ktor:ktor-server-auth-jwt-jvm") // jwt登陆验证
    implementation("io.ktor:ktor-server-content-negotiation") // request/response时反序列化
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version") // 数据库
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version") // 数据库
    implementation("com.h2database:h2:$h2_version") // 数据库
    implementation("io.ktor:ktor-server-netty-jvm") // netty
    implementation("ch.qos.logback:logback-classic:$logback_version") // 日志
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.7") // 日志

    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm") // json on request/response
    implementation("net.mamoe.yamlkt:yamlkt:0.13.0") // yaml for kotlin on read/write file
    implementation("io.ktor:ktor-server-config-yaml-jvm") // yaml on read application.yaml

    implementation("me.nullaqua:BluestarAPI-kotlin:4.0.0-pre7") // BluestarAPI
    implementation("org.fusesource.jansi:jansi:2.4.1") // 终端颜色码
    implementation("org.jline:jline-terminal-jansi:3.24.1") // 终端打印、命令等
    implementation("org.jline:jline-reader:3.24.1") // 终端打印、命令等
    implementation("org.jline:jline-terminal:3.24.1") // 终端打印、命令等
    implementation("org.jline:jline-style:3.24.1") // 终端打印、命令等

    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
