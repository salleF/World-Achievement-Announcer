plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "br.sinosdomundo"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Compila contra a 1.21.4 para rodar em servidores 1.21.4+ e 26.x
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Estatísticas anônimas de uso (bStats), embutidas no jar
    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Servidores 1.21.x rodam em Java 21
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// O jar final é o do Shadow (com o bStats dentro, em outro pacote para não conflitar com outros plugins)
tasks.shadowJar {
    archiveBaseName.set("WorldAchievementAnnouncer")
    archiveClassifier.set("")
    configurations = project.configurations.runtimeClasspath.map { setOf(it) }
    dependencies {
        exclude { it.moduleGroup != "org.bstats" }
    }
    relocate("org.bstats", "br.sinosdomundo.bstats")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
