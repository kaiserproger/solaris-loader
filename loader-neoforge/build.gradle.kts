plugins {
    java
    id("net.neoforged.moddev")
}

val neoVersion = providers.gradleProperty("neoVersion")
val clientMcpToken = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_TOKEN")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.token"))
    .orElse("")
val clientMcpPort = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_PORT")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.port"))
    .orElse("39095")
val clientMcpGameDir = providers.gradleProperty("krasnokamen.clientMcp.gameDir")
    .orElse(rootProject.file(".analysis/minecraft-loader-mcp/neoforge").absolutePath)
val clientMcpUsername = providers.gradleProperty("krasnokamen.clientMcp.username").orElse("KrasnoLoader")
dependencies {
    implementation(project(":loader-core"))
    implementation(project(":bridge-core"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    named("main") {
        java.srcDir(rootProject.file("loader-platform-common/src/main/java"))
        java.srcDir(rootProject.file("loader-platform-common/src/minecraft-${providers.gradleProperty("minecraftVersion").get()}/java"))
        resources.srcDir(rootProject.file("loader-platform-common/src/main/resources"))
        java.srcDir(project(":java-agent").layout.projectDirectory.dir("src/main/java"))
        java.srcDir(project(":java-agent").layout.projectDirectory.dir("src/minecraft-${providers.gradleProperty("minecraftVersion").get()}/java"))
    }
    named("test") {
        java.srcDir(rootProject.file("loader-platform-common/src/test/java"))
    }
}

tasks.processResources {
    val minecraftVersion = providers.gradleProperty("minecraftVersion")
    inputs.property("minecraftVersion", minecraftVersion)
    inputs.property("neoVersion", neoVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("minecraftVersion" to minecraftVersion.get(), "neoVersion" to neoVersion.get())
    }
}

neoForge {
    version = neoVersion.get()
    runs {
        create("clientMcp") {
            client()
            gameDirectory.set(file(clientMcpGameDir.get()))
            environment("KRASNOKAMEN_CLIENT_MCP_TOKEN", clientMcpToken.get())
            environment("KRASNOKAMEN_CLIENT_MCP_PORT", clientMcpPort.get())
            systemProperty(
                "krasnokamen.loader.cacheDir",
                file(clientMcpGameDir.get()).resolve("krasnokamen-loader-cache").absolutePath
            )
            programArgument("--username")
            programArgument(clientMcpUsername.get())
        }
    }
    mods {
        create("krasnokamenLoader") {
            sourceSet(sourceSets.main.get())
        }
    }
    unitTest {
        enable()
        testedMod = mods["krasnokamenLoader"]
    }
}

tasks.jar {
    dependsOn(project(":loader-core").tasks.named("classes"))
    from(project(":loader-core").layout.buildDirectory.dir("classes/java/main"))
}

tasks.named<JavaExec>("runClientMcp") {
    group = "verification"
    description = "Runs the NeoForge Loader client with the embedded Krasnokamen MCP server."
    dependsOn(rootProject.tasks.named("validateLoaderClientMcpRunProperties"))
    notCompatibleWithConfigurationCache("The Loader MCP profile uses per-run credentials and game directories.")
}
