load("//tools/bzl:maven_jar.bzl", "maven_jar")

# Ensure artifacts compatibility by selecting them from the Bill Of Materials
# https://search.maven.org/artifact/net.openhft/chronicle-bom/2.22.150/pom
def external_plugin_deps():
    maven_jar(
        name = "chronicle-map",
        artifact = "net.openhft:chronicle-map:3.22.8",
        sha1 = "8fcedc5fdefb925fb9f5d955bc0998d6adb973b2",
    )

    maven_jar(
        name = "chronicle-core",
        artifact = "net.openhft:chronicle-core:2.22.34",
        sha1 = "c8832c23dd3524838bce9b699fb5db396a5ea1b5",
    )

    maven_jar(
        name = "chronicle-wire",
        artifact = "net.openhft:chronicle-wire:2.22.21",
	sha1 = "181187a617c8cee763a930e666b0406f0f48ddb9",
    )

    maven_jar(
        name = "chronicle-bytes",
        artifact = "net.openhft:chronicle-bytes:2.22.24",
	sha1 = "0869cc065566e2072c07d6afeff0e9e04ebb8bb8",
    )

    maven_jar(
        name = "chronicle-algo",
        artifact = "net.openhft:chronicle-algorithms:2.22.3",
        sha1 = "046a64262fa2ded35160e4ae36a3b7cdb6bd2e04",
    )

    maven_jar(
        name = "chronicle-values",
        artifact = "net.openhft:chronicle-values:2.22.2",
        sha1 = "cce7f3d9b7c7f5d87b4d1bfd9a4b8183b598637d",
    )

    maven_jar(
        name = "chronicle-threads",
        artifact = "net.openhft:chronicle-threads:2.22.15",
        sha1 = "f475b5a414c8010d187e6f1e4570fa566dbc0067",
    )

    maven_jar(
        name = "javapoet",
        artifact = "com.squareup:javapoet:1.13.0",
        sha1 = "d6562d385049f35eb50403fa86bb11cce76b866a",
    )

#    maven_jar(
#        name = "jna-platform",
#        artifact = "net.java.dev.jna:jna-platform:5.12.1",
#        sha1 = "097406a297c852f4a41e688a176ec675f72e8329",
#    )

#    maven_jar(
#        name = "dev-jna",
#        artifact = "net.java.dev.jna:jna:5.12.1",
#        sha1 = "b1e93a735caea94f503e95e6fe79bf9cdc1e985d",
#    )

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

    maven_jar(
        name = "javamelody-core",
        artifact = "net.bull.javamelody:javamelody-core:1.91.0",
        sha1 = "e708e7850f3a9929c287bc4cd670551ed3af572f",
    )

    maven_jar(
        name = "jrobin",
        artifact = "org.jrobin:jrobin:1.5.9",
        sha1 = "bd9a84484c67de930fa841f23cd6a93108b05cd0",
    )

