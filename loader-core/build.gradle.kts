plugins {
    java
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

val solarisRepoRoot = providers.environmentVariable("SOLARIS_REPO_ROOT")
    .orElse(providers.gradleProperty("solaris.repoRoot"))
    .orElse(rootProject.file("../solaris").absolutePath)

tasks.test {
    systemProperty("solaris.repoRoot", solarisRepoRoot.get())
    inputs.files(
        file("${solarisRepoRoot.get()}/examples/loader-live-gate/plugins/ruby-live/client/rich-content.zip"),
        file("${solarisRepoRoot.get()}/examples/loader-live-gate/plugins/sapphire-live/client/rich-content.zip"),
    )
}
