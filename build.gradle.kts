import java.security.MessageDigest
import java.util.HexFormat

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
        languageVersion.set(JavaLanguageVersion.of(25))
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
val distributionTar = tasks.named<Tar>("distTar")

val distributionChecksums = tasks.register("distChecksums") {
    group = "distribution"
    description = "Writes SHA-256 files for the portable release archives."
    dependsOn(distributionZip, distributionTar)

    doLast {
        listOf(distributionZip.get().archiveFile.get().asFile, distributionTar.get().archiveFile.get().asFile)
            .forEach { archive ->
                val digest = MessageDigest.getInstance("SHA-256")
                archive.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val checksum = HexFormat.of().formatHex(digest.digest())
                file(archive.parentFile.resolve(archive.name + ".sha256"))
                    .writeText("$checksum  ${archive.name}\n")
            }
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(distributionChecksums)

    doFirst {
        systemProperty("preview.distribution.zip", distributionZip.get().archiveFile.get().asFile)
        systemProperty(
            "preview.distribution.zip.checksum",
            distributionZip.get().archiveFile.get().asFile.parentFile.resolve(
                distributionZip.get().archiveFile.get().asFile.name + ".sha256"))
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
                exclude("**/build/**", "**/output/**", "**/logs/**", "**/runtime-classpath.txt")
            }
        }
    }
}

distributionTar {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.register("agentVerify") {
    group = "verification"
    description = "Runs the complete verification suite used by coding agents."
    dependsOn(tasks.check)
}
