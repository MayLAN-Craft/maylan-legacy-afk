plugins {
    id("java")
}

group = "ru.maylan"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven { url = uri("https://repo.extendedclip.com/releases/") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

