import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.jetbrains.kotlin.kapt)
}

group = "com.xuekirby.spw"

val releaseVersion = providers.gradleProperty("releaseVersion")
    .orElse(
        providers.environmentVariable("GITHUB_REF_NAME")
            .map { it.removePrefix("v") }
    )
    .orElse("0.1.1")
    .get()

version = releaseVersion

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))

    rootProject.findProject(":api")?.let { apiProject ->
        compileOnly(apiProject)
        kapt(apiProject)
    } ?: run {
        compileOnly(libs.spw.workshop.api)
        kapt(libs.spw.workshop.api)
    }

    implementation(libs.kotlinx.serialization.json)
}

val pluginClass = "com.xuekirby.spw.OnlineLyricsPlugin"
val pluginId = "com.xuekirby.spw.online-lyrics"
val pluginName = "在线歌词"
val pluginDescription = "本地没有歌词时，从在线服务获取歌词"
val pluginVersion = releaseVersion
val pluginProvider = "XueKirby"
val pluginRepository = "https://github.com/XueKirby/spw-online-lyrics"

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Plugin-Class" to pluginClass,
            "Plugin-Id" to pluginId,
            "Plugin-Name" to pluginName,
            "Plugin-Description" to pluginDescription,
            "Plugin-Version" to pluginVersion,
            "Plugin-Provider" to pluginProvider,
            "Plugin-Has-Config" to "true",
            "Plugin-Open-Source-Url" to pluginRepository,
        )
    }
}

tasks.register<Jar>("plugin") {
    destinationDirectory.set(
        file(System.getenv("APPDATA") + "/Salt Player for Windows/workshop/plugins/")
    )
    archiveFileName.set("v$pluginVersion.zip")

    into("classes") {
        with(tasks.named<Jar>("jar").get())
    }
    dependsOn(configurations.runtimeClasspath)
    into("lib") {
        from({
            configurations.runtimeClasspath
                .get()
                .filter { it.name.endsWith("jar") }
        })
    }
    archiveExtension.set("zip")
}

// For IDE import compatibility (some IDEs invoke this task per-module).
if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
    tasks.register("prepareKotlinBuildScriptModel") {}
}
