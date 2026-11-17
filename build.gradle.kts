plugins {
    java
}

allprojects {
    group = "feedlab"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all,-processing")
    }

    // The service module takes its test libraries from Spring Boot's dependency management instead.
    if (name != "service") {
        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testImplementation"("org.assertj:assertj-core:3.27.3")
            "testImplementation"("net.jqwik:jqwik:1.9.2")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            showStandardStreams = false
        }
    }
}
