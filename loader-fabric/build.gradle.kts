plugins {
    java
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion = providers.gradleProperty("minecraftVersion")
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion")
val fabricApiVersion = providers.gradleProperty("fabricApiVersion")
val clientMcpToken = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_TOKEN")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.token"))
    .orElse("")
val clientMcpPort = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_PORT")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.port"))
    .orElse("39095")
val clientMcpGameDir = providers.gradleProperty("krasnokamen.clientMcp.gameDir")
    .orElse(rootProject.file(".analysis/minecraft-loader-mcp/fabric").absolutePath)
val clientMcpUsername = providers.gradleProperty("krasnokamen.clientMcp.username").orElse("KrasnoLoader")

loom {
    runs {
        create("clientMcp") {
            client()
            runDirectory.set(file(clientMcpGameDir.get()))
            property(
                "krasnokamen.loader.cacheDir",
                file(clientMcpGameDir.get()).resolve("krasnokamen-loader-cache").absolutePath
            )
        }
    }
}

dependencies {
    implementation(project(":loader-core"))
    implementation(project(":bridge-core"))
    compileOnly("org.ow2.asm:asm:9.9")
    minecraft("com.mojang:minecraft:${minecraftVersion.get()}")
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion.get()}")
    include(project(":loader-core"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    named("main") {
        java.srcDir(rootProject.file("loader-platform-common/src/main/java"))
        java.srcDir(rootProject.file("loader-platform-common/src/minecraft-${minecraftVersion.get()}/java"))
        resources.srcDir(rootProject.file("loader-platform-common/src/main/resources"))
        java.srcDir(project(":java-agent").layout.projectDirectory.dir("src/main/java"))
        java.srcDir(project(":java-agent").layout.projectDirectory.dir("src/minecraft-${minecraftVersion.get()}/java"))
    }
    named("test") {
        java.srcDir(rootProject.file("loader-platform-common/src/test/java"))
        java.srcDir("src/minecraft-${minecraftVersion.get()}/test/java")
    }
}

tasks.processResources {
    inputs.property("fabricLoaderVersion", fabricLoaderVersion)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("fabricLoaderVersion" to fabricLoaderVersion.get(), "minecraftVersion" to minecraftVersion.get())
    }
}

tasks.named<JavaExec>("runClientMcp") {
    group = "verification"
    description = "Runs the Fabric Loader client with the embedded Krasnokamen MCP server."
    dependsOn(rootProject.tasks.named("validateLoaderClientMcpRunProperties"))
    workingDir(file(clientMcpGameDir.get()))
    args("--username", clientMcpUsername.get())
    environment("KRASNOKAMEN_CLIENT_MCP_TOKEN", clientMcpToken.get())
    environment("KRASNOKAMEN_CLIENT_MCP_PORT", clientMcpPort.get())
    systemProperty(
        "krasnokamen.loader.cacheDir",
        file(clientMcpGameDir.get()).resolve("krasnokamen-loader-cache").absolutePath
    )
    notCompatibleWithConfigurationCache("The Loader MCP profile uses per-run credentials and game directories.")
}
