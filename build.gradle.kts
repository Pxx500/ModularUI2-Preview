plugins {
    java
}

group = "dev.modularui.preview"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://nexus.gtnewhorizons.com/repository/releases/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val preview by sourceSets.creating {
    java.srcDir("src/preview/java")
    resources.srcDir("src/preview/resources")
}

dependencies {
    add(preview.implementationConfigurationName, "com.github.GTNewHorizons:ModularUI2:2.3.84-1.7.10:dev") {
        isTransitive = false
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    compileClasspath += preview.output + preview.compileClasspath
    runtimeClasspath += preview.output + preview.runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("preview") {
    group = "application"
    description = "Renders a production-shaped ModularUI2 screen to preview.png and bounds.json."
    classpath = files(sourceSets.main.get().output, preview.output) +
        (preview.runtimeClasspath - preview.output)
    mainClass.set("dev.modularui.preview.UiPreviewMain")

    doFirst {
        val previewClass = providers.gradleProperty("previewClass").orNull
            ?: throw GradleException("Missing preview class. Use -PpreviewClass=fully.qualified.ClassName")
        val outputDirectory = providers.gradleProperty("previewOutput")
            .orElse("output/${previewClass.substringAfterLast('.')}")
            .get()
        val configuration = providers.gradleProperty("previewConfig")
            .orElse("preview.properties")
            .get()
        args(previewClass, outputDirectory, configuration)
    }
}

tasks.register("agentVerify") {
    group = "verification"
    description = "Runs the complete verification suite used by coding agents."
    dependsOn(tasks.check)
}
