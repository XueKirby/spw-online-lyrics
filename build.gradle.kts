import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

group = "com.xuekirby.spw"
version = "0.1.0"

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

    project(":api").let {
        compileOnly(it)
        kapt(it)
    }

    implementation(libs.kotlinx.serialization.json)
}

val pluginClass = "com.xuekirby.spw.OnlineLyricsPlugin"
val pluginId = "com.xuekirby.spw.online-lyrics"
val pluginName = "在线歌词"
val pluginDescription = "当歌词不存在时，从在线服务兜底获取"
val pluginVersion = "0.1.0"
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
    archiveFileName.set("$pluginName-$pluginVersion.zip")

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
tasks.register("prepareKotlinBuildScriptModel") {}
