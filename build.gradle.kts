plugins {
    id("docscanner.quality") apply false
    id("docscanner.coverage")
}

subprojects {
    apply(plugin = "docscanner.quality")
}
