import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // -PlocalIdePath=/path/to/IntelliJ IDEA.app runs the build against an installed IDE,
        // which is how a "works in runIde, not in my IDE" difference gets reproduced.
        val localIdePath = providers.gradleProperty("localIdePath").orNull
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }

        // Rendered doc comments for Java and Kotlin come from these plugins' providers;
        // we decorate whatever they produce.
        // Mermaid is bundled in IDEA: it gives the ```mermaid fence a Language, which changes the
        // HTML the doc renderer produces - so tests must load it to match a real IDE.
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin", "com.intellij.mermaid")

        // JCEF moved into a bundled plugin; its content modules hold JBCefBrowser and org.cef.
        bundledPlugin("com.intellij.modules.jcef")
        bundledModules("intellij.platform.ui.jcef", "intellij.libraries.jcef")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(libs.junit)
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The platform we build against; swap for recommended() to widen the matrix.
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion").get())
        }

        // Rewriting rendered documentation has no public API: the seam (DocRendererProvider,
        // DocRenderItem, DocRenderItemUpdater) is @Internal. That is deliberate and fails soft - a
        // diagram just does not appear, and MermaidSeamCheck reports it. Everything else, including
        // @OverrideOnly misuse, still fails the build.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )
    }
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Stay within the API surface of the Kotlin stdlib bundled with the target IDE.
        apiVersion = KotlinVersion.KOTLIN_2_1
        languageVersion = KotlinVersion.KOTLIN_2_1
    }
}

sourceSets {
    main {
        // Expose the vendored mermaid version to the code without duplicating it.
        resources.srcDir(layout.buildDirectory.dir("generated/mermaid-resources"))
    }
}

val generateMermaidVersionResource = tasks.register("generateMermaidVersionResource") {
    val mermaidVersion = providers.gradleProperty("mermaidVersion")
    val outputDir = layout.buildDirectory.dir("generated/mermaid-resources/mermaid")
    inputs.property("mermaidVersion", mermaidVersion)
    outputs.dir(outputDir)
    doLast {
        outputDir.get().asFile.resolve("version.txt").writeText(mermaidVersion.get())
    }
}

tasks {
    processResources {
        dependsOn(generateMermaidVersionResource)
    }

    test {
        useJUnit()
        // Opt-in: `./gradlew test -Dmermaid.jcef.test=true` also exercises the real browser.
        systemProperty(
            "mermaid.jcef.test",
            providers.systemProperty("mermaid.jcef.test").getOrElse("false"),
        )
    }
}
