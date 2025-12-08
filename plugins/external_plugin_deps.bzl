"""
External dependencies of the supermanifest plugin
"""

load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "jaxb-api",
        artifact = "javax.xml.bind:jaxb-api:2.3.1",
    )

    maven_jar(
        name = "istack-commons-runtime",
        artifact = "com.sun.istack:istack-commons-runtime:3.0.11",
    )

    maven_jar(
        name = "jaxb-runtime",
        artifact = "org.glassfish.jaxb:jaxb-runtime:2.3.3",
    )
