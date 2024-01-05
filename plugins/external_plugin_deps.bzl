load("//tools/bzl:maven_jar.bzl", "maven_jar")

# Ensure artifacts compatibility by selecting them from the Bill Of Materials
# https://search.maven.org/artifact/net.openhft/chronicle-bom/2.20.191/pom
def external_plugin_deps():
    maven_jar(
        name = "chronicle-map",
        artifact = "net.openhft:chronicle-map:3.20.84",
        sha1 = "a4549f64d41e7f379d48cfee432f210c0ed563e1",
    )

    maven_jar(
        name = "chronicle-core",
        artifact = "net.openhft:chronicle-core:2.20.122",
        sha1 = "aa9dcde008938f5c845b98a6b8f74b25a4689c7c",
    )

    maven_jar(
        name = "chronicle-wire",
        artifact = "net.openhft:chronicle-wire:2.20.111",
        sha1 = "4002820daefe5694ecd73b640afd26fa32534959",
    )

    maven_jar(
        name = "chronicle-bytes",
        artifact = "net.openhft:chronicle-bytes:2.20.106",
        sha1 = "6e4c01ea06ec005ca79ee694efa0a90634b6169e",
    )

    maven_jar(
        name = "chronicle-algo",
        artifact = "net.openhft:chronicle-algorithms:2.20.80",
        sha1 = "60b86a584d272aae6b7a80f6c7859c689a7199be",
    )

    maven_jar(
        name = "chronicle-values",
        artifact = "net.openhft:chronicle-values:2.20.80",
        sha1 = "2cd2bceaa3f0bcdd4470311c05daafbc188b57e2",
    )

    maven_jar(
        name = "chronicle-threads",
        artifact = "net.openhft:chronicle-threads:2.20.104",
        sha1 = "53295d10b1eb63c1f6bb1a8a58e6889567ae6355",
    )

    maven_jar(
        name = "javapoet",
        artifact = "com.squareup:javapoet:1.13.0",
        sha1 = "d6562d385049f35eb50403fa86bb11cce76b866a",
    )

    maven_jar(
        name = "jna-platform",
        artifact = "net.java.dev.jna:jna-platform:5.6.0",
        sha1 = "d18424ffb8bbfd036d71bcaab9b546858f2ef986",
    )

    maven_jar(
        name = "dev-jna",
        artifact = "net.java.dev.jna:jna:5.6.0",
        sha1 = "330f2244e9030119ab3030fc3fededc86713d9cc",
    )
