plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
}

allprojects {
    group = "ai.kaios"
    version = "0.1.34"
}

tasks.register<Sync>("installDist") {
    group = "distribution"
    description = "Installs the KAI OS CLI distribution under the root build directory."
    dependsOn(":kaios-cli:installDist")
    from(project(":kaios-cli").layout.buildDirectory.dir("install/kaios"))
    into(layout.buildDirectory.dir("install/kaios-cli"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(kotlin("test-junit5"))
    }
}
