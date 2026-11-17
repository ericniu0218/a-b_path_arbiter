// Replay, path simulation, metrics and the command-line report.
plugins {
    `java-library`
    application
}

dependencies {
    api(project(":core"))
    api("org.hdrhistogram:HdrHistogram:2.2.2")
}

application {
    mainClass.set("feedlab.sim.Main")
}
