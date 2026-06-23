plugins {
    alias(libs.plugins.fabric.loom)
}

val productionMods by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.meteor.client)
    modImplementation(libs.baritone)

    productionMods(libs.baritone)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }

    withSourcesJar()
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "jdk_version" to libs.versions.jdk.get()
        )

        inputs.properties(propertyMap)

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(libs.versions.jdk.get().toInt())
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }

    withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }

    val runPureTests by registering(JavaExec::class) {
        dependsOn(testClasses)
        description = "Runs dependency-free parser and planner regression tests."
        group = "verification"

        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.watchmenbot.modules.stash.StashKitbotParserPlannerTest")
    }

    val runPlanePureTests by registering(JavaExec::class) {
        dependsOn(testClasses)
        description = "Runs dependency-free Plane Builder regression tests."
        group = "verification"

        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.watchmenbot.modules.planebuilder.PlaneBuilderPureTest")
    }

    val runInventoryPureTests by registering(JavaExec::class) {
        dependsOn(testClasses)
        description = "Runs dependency-free inventory regression tests."
        group = "verification"

        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.watchmenbot.modules.inventory.AutoEatStockerPureTest")
    }

    check {
        dependsOn(runPureTests)
        dependsOn(runPlanePureTests)
        dependsOn(runInventoryPureTests)
    }

    val stageProductionMods by registering(Copy::class) {
        dependsOn(remapJar)
        description = "Copies WMBot and required external mod jars into build/production-mods."
        group = "build"

        into(layout.buildDirectory.dir("production-mods"))
        from(remapJar.flatMap { it.archiveFile })
        from(productionMods)
    }

    build {
        dependsOn(stageProductionMods)
    }
}
