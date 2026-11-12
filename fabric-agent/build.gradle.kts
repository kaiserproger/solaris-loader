import java.nio.charset.StandardCharsets
import java.security.MessageDigest

plugins {
    java
    id("net.neoforged.moddev")
}

val neoVersion = providers.gradleProperty("neoVersion")
val clientAgentSecret = providers.gradleProperty("krasnokamen.clientAgent.secret").orElse("")
val clientAgentPort = providers.gradleProperty("krasnokamen.clientAgent.port").orElse("39094")
val clientAgentRunDir = providers.gradleProperty("krasnokamen.clientAgent.runDir").orElse(".")
val clientAgentRuntimeProvenanceFile = providers.gradleProperty(
    "krasnokamen.clientAgent.runtimeProvenanceFile"
).orElse("")
val clientAgentGameDir = providers.gradleProperty("krasnokamen.clientAgent.gameDir")
    .orElse(layout.projectDirectory.dir("run").asFile.absolutePath)
val clientAgentUsername = providers.gradleProperty("krasnokamen.clientAgent.username").orElse("KrasnoClient")
val clientAgentConfigurationCacheReason =
    "runClientAgent uses per-run bridge credentials and game directories."
val clientMcpToken = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_TOKEN")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.token"))
    .orElse("")
val clientMcpPort = providers.environmentVariable("KRASNOKAMEN_CLIENT_MCP_PORT")
    .orElse(providers.gradleProperty("krasnokamen.clientMcp.port"))
    .orElse("39095")
val clientMcpGameDir = providers.gradleProperty("krasnokamen.clientMcp.gameDir")
    .orElse(layout.projectDirectory.dir("run-mcp").asFile.absolutePath)
val clientMcpUsername = providers.gradleProperty("krasnokamen.clientMcp.username").orElse("KrasnoMcp")
val clientMcpConfigurationCacheReason =
    "runClientMcp uses per-run bearer credentials and game directories."

dependencies {
    implementation(project(":bridge-core"))
}

sourceSets.main {
    java {
        srcDir(project(":java-agent").layout.projectDirectory.dir("src/main/java"))
    }
}

fun writeClientAgentRuntimeProvenance() {
    val provenancePath = clientAgentRuntimeProvenanceFile.get().trim()
    if (provenancePath.isEmpty()) {
        return
    }

    val classesDirectory = layout.buildDirectory.dir("classes/java/main").get().asFile
    if (!classesDirectory.isDirectory) {
        throw GradleException("runClientAgent runtime classes directory is missing: $classesDirectory")
    }
    val classFiles = classesDirectory.walkTopDown()
        .filter { it.isFile }
        .sortedBy { it.relativeTo(classesDirectory).invariantSeparatorsPath }
        .toList()
    if (classFiles.isEmpty()) {
        throw GradleException("runClientAgent runtime classes directory is empty: $classesDirectory")
    }

    val digest = MessageDigest.getInstance("SHA-256")
    for (classFile in classFiles) {
        digest.update(
            classFile.relativeTo(classesDirectory).invariantSeparatorsPath
                .toByteArray(StandardCharsets.UTF_8)
        )
        digest.update(0.toByte())
        classFile.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
    }
    val digestHex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    val provenanceFile = file(provenancePath)
    provenanceFile.parentFile.mkdirs()
    provenanceFile.writeText(
        "runtime_artifact_kind=compiled-classes\n" +
            "runtime_artifact_path=${classesDirectory.canonicalPath}\n" +
            "runtime_artifact_sha256=$digestHex\n" +
            "runtime_artifact_file_count=${classFiles.size}\n",
        StandardCharsets.UTF_8
    )
}

neoForge {
    version = neoVersion.get()

    runs {
        create("client") {
            client()
            gameDirectory.set(file(clientAgentGameDir.get()))
            systemProperty("krasnokamen.clientAgent.secret", clientAgentSecret.get())
            systemProperty("krasnokamen.clientAgent.port", clientAgentPort.get())
            systemProperty("krasnokamen.clientAgent.runDir", clientAgentRunDir.get())
            programArgument("--username")
            programArgument(clientAgentUsername.get())
        }
        create("clientAgent") {
            client()
            gameDirectory.set(file(clientAgentGameDir.get()))
            systemProperty("krasnokamen.clientAgent.secret", clientAgentSecret.get())
            systemProperty("krasnokamen.clientAgent.port", clientAgentPort.get())
            systemProperty("krasnokamen.clientAgent.runDir", clientAgentRunDir.get())
            programArgument("--username")
            programArgument(clientAgentUsername.get())
        }
        create("clientMcp") {
            client()
            gameDirectory.set(file(clientMcpGameDir.get()))
            environment("KRASNOKAMEN_CLIENT_MCP_TOKEN", clientMcpToken.get())
            environment("KRASNOKAMEN_CLIENT_MCP_PORT", clientMcpPort.get())
            programArgument("--username")
            programArgument(clientMcpUsername.get())
        }
    }

    mods {
        create("krasnokamen_client_agent") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.register("validateClientAgentRunProperties") {
    group = "verification"
    description = "Validates per-run Krasnokamen client-agent Gradle runClient properties."
    doLast {
        val secret = clientAgentSecret.get().trim()
        if (secret.isEmpty()) {
            throw GradleException("Missing -Pkrasnokamen.clientAgent.secret for runClientAgent.")
        }
        val port = clientAgentPort.get().trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            throw GradleException("Invalid -Pkrasnokamen.clientAgent.port '${clientAgentPort.get()}'. Use 1..65535.")
        }
        val gameDir = file(clientAgentGameDir.get())
        if (!gameDir.isAbsolute) {
            throw GradleException("Invalid -Pkrasnokamen.clientAgent.gameDir '${clientAgentGameDir.get()}'. Use an absolute path.")
        }
    }
}

tasks.register("validateClientMcpRunProperties") {
    group = "verification"
    description = "Validates the embedded Minecraft MCP Gradle run properties."
    doLast {
        val token = clientMcpToken.get().trim()
        if (token.isEmpty()) {
            throw GradleException(
                "Missing KRASNOKAMEN_CLIENT_MCP_TOKEN or -Pkrasnokamen.clientMcp.token for runClientMcp."
            )
        }
        val port = clientMcpPort.get().trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            throw GradleException(
                "Invalid MCP port '${clientMcpPort.get()}'. Use KRASNOKAMEN_CLIENT_MCP_PORT=1..65535."
            )
        }
        val username = clientMcpUsername.get()
        if (!username.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            throw GradleException(
                "Invalid -Pkrasnokamen.clientMcp.username '$username'. Use 1..16 ASCII letters, digits, or underscores."
            )
        }
        val gameDir = file(clientMcpGameDir.get())
        if (!gameDir.isAbsolute) {
            throw GradleException(
                "Invalid -Pkrasnokamen.clientMcp.gameDir '${clientMcpGameDir.get()}'. Use an absolute path."
            )
        }
    }
}

tasks.named("runClientAgent") {
    group = "verification"
    description = "Runs the Krasnokamen real-client gate through the repo-native Gradle runClient adapter."
    dependsOn(tasks.named("validateClientAgentRunProperties"))
    notCompatibleWithConfigurationCache(clientAgentConfigurationCacheReason)
    doFirst {
        writeClientAgentRuntimeProvenance()
    }
}

tasks.named("runClientMcp") {
    group = "verification"
    description = "Runs Minecraft 26.1.2 with the reusable in-client Streamable HTTP MCP server."
    dependsOn(tasks.named("validateClientMcpRunProperties"))
    notCompatibleWithConfigurationCache(clientMcpConfigurationCacheReason)
}
