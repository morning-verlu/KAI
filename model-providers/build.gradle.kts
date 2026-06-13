plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":runtime-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
