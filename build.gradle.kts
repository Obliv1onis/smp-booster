import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.3")
val paperApiVersion = if (minecraftVersion == "1.21.11") {
    "1.21.11-R0.1-SNAPSHOT"
} else {
    "$minecraftVersion.build.+"
}
val targetJavaVersion = if (minecraftVersion == "1.21.11") 21 else 25
val toolchains = project.extensions.getByType(JavaToolchainService::class.java)

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

tasks {
    runServer {
        minecraftVersion(minecraftVersion)
        runDirectory(layout.projectDirectory.dir("run-compat/$minecraftVersion").asFile)
        javaLauncher.set(toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        })
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "apiVersion" to minecraftVersion,
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveFileName = "SMP-Booster-${project.version}-paper-$minecraftVersion.jar"
    }
}
