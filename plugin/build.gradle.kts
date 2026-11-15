plugins { java }

group = "dev.luci"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.253-stable")
    compileOnly("io.github.toxicity188:bettermodel-api:3.6.0")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.6.0")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
    from("../models") { include("*.bbmodel"); into("models") }   // bundled stand models, installed into BetterModel on first run
}

// Build the jar and drop it straight into the test server.
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../server/plugins"))
}
