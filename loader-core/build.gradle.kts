plugins {
    java
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation("io.github.jaredmdobson:concentus:1.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

val krasnokamenRepoRoot = providers.environmentVariable("KRASNOKAMEN_REPO_ROOT")
    .orElse(providers.gradleProperty("krasnokamen.repoRoot"))
    .orElse(rootProject.file("../solaris").absolutePath)

tasks.test {
    maxHeapSize = "1g"
    systemProperty("krasnokamen.repoRoot", krasnokamenRepoRoot.get())
    inputs.files(
        file("${krasnokamenRepoRoot.get()}/examples/loader-live-gate/plugins/ruby-live/client/rich-content.zip"),
        file("${krasnokamenRepoRoot.get()}/examples/loader-live-gate/plugins/sapphire-live/client/rich-content.zip"),
    )
}
