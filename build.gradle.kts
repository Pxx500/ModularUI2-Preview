plugins {
    java
    application
}

group = "dev.modularui.preview"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven("https://nexus.gtnewhorizons.com/repository/releases/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val bundledRuntime by configurations.creating

configurations.named(sourceSets.main.get().runtimeOnlyConfigurationName) {
    extendsFrom(bundledRuntime)
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("it.unimi.dsi:fastutil:8.5.13")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.commons:commons-lang3:3.15.0")
    implementation("org.joml:joml:1.10.8")

    add(bundledRuntime.name, "com.github.GTNewHorizons:ModularUI2:2.3.84-1.7.10:dev") {
        isTransitive = false
    }
    add(bundledRuntime.name, "com.github.GTNewHorizons:ModularUI:1.3.4:dev") {
        isTransitive = false
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val distributionZip = tasks.named<Zip>("distZip")

tasks.test {
    useJUnitPlatform()
    dependsOn(distributionZip)

    doFirst {
        systemProperty("preview.distribution.zip", distributionZip.get().archiveFile.get().asFile)
        systemProperty(
            "modularui.test.jar",
            bundledRuntime.single { it.name.startsWith("ModularUI2-") })
    }
}

application {
    applicationName = "modularui2-preview"
    mainClass.set("dev.modularui.preview.UiPreviewMain")
    applicationDefaultJvmArgs = listOf("-Djoml.nounsafe=true")
}

distributions {
    named("main") {
        contents {
            from("preview.bat")
            from("preview.sh") {
                filePermissions {
                    unix("rwxr-xr-x")
                }
            }
            from("README.md")
            from("LICENSE")
            from("LICENSES") {
                into("LICENSES")
            }
            from("THIRD_PARTY_NOTICES.md")
            from("examples") {
                into("examples")
            }
        }
    }
}

tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.register("agentVerify") {
    group = "verification"
    description = "Runs the complete verification suite used by coding agents."
    dependsOn(tasks.check)
}
