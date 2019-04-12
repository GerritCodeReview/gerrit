"""Combined external plugin dependencies."""

load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    # automerger
    maven_jar(
        name = "re2j",
        artifact = "com.google.re2j:re2j:1.0",
        sha1 = "d24ac5f945b832d93a55343cd1645b1ba3eca7c3",
    )

    #uploadvalidator
    maven_jar(
        name = "commons_io",
        artifact = "commons-io:commons-io:1.4",
        sha1 = "a8762d07e76cfde2395257a5da47ba7c1dbd3dce",
    )
