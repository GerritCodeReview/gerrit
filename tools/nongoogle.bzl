load("//tools/bzl:maven_jar.bzl", "maven_jar")

GUAVA_VERSION = "30.1-jre"

GUAVA_BIN_SHA1 = "00d0c3ce2311c9e36e73228da25a6e99b2ab826f"

GUAVA_TESTLIB_BIN_SHA1 = "798c3827308605cd69697d8f1596a1735d3ef6e2"

GUAVA_DOC_URL = "https://google.github.io/guava/releases/" + GUAVA_VERSION + "/api/docs/"

TESTCONTAINERS_VERSION = "1.15.3"

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    maven_jar(
        name = "j2objc",
        artifact = "com.google.j2objc:j2objc-annotations:1.1",
        sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
    )

    # Transitive dependency of commons-compress
    maven_jar(
        name = "tukaani-xz",
        artifact = "org.tukaani:xz:1.8",
        sha1 = "c4f7d054303948eb6a4066194253886c8af07128",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.1.12.1",
        sha1 = "cb2f351bf4463751201f43bb99865235d5ba07ca",
    )

    SSHD_VERS = "2.6.0"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "40e365bb799e1bff3d31dc858b1e59a93c123f29",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "6eddfe8fdf59a3d9a49151e4177f8c1bebeb30c9",
    )

    maven_jar(
        name = "eddsa",
        artifact = "net.i2p.crypto:eddsa:0.3.0",
        sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.0.21",
        sha1 = "e1a317689ecd438f54e863747e832f741ef8e092",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:" + SSHD_VERS,
        sha1 = "d22138ba75dee95e2123f0e53a9c514b2a766da9",
    )

    # elasticsearch-rest-client explicitly depends on this version
    maven_jar(
        name = "httpasyncclient",
        artifact = "org.apache.httpcomponents:httpasyncclient:4.1.4",
        sha1 = "f3a3240681faae3fa46b573a4c7e50cec9db0d86",
    )

    # elasticsearch-rest-client explicitly depends on this version
    maven_jar(
        name = "httpcore-nio",
        artifact = "org.apache.httpcomponents:httpcore-nio:4.4.12",
        sha1 = "84cd29eca842f31db02987cfedea245af020198b",
    )

    maven_jar(
        name = "openid-consumer",
        artifact = "org.openid4java:openid4java:1.0.0",
        sha1 = "541091bb49f2c0d583544c5bb1e6df7612d31e3e",
    )

    maven_jar(
        name = "nekohtml",
        artifact = "net.sourceforge.nekohtml:nekohtml:1.9.10",
        sha1 = "14052461031a7054aa094f5573792feb6686d3de",
    )

    maven_jar(
        name = "xerces",
        artifact = "xerces:xercesImpl:2.8.1",
        attach_source = False,
        sha1 = "25101e37ec0c907db6f0612cbf106ee519c1aef1",
    )

    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:2.12.0",
        sha1 = "afe52c6947d9939170da7989612cef544115511a",
    )

    maven_jar(
        name = "commons-io",
        artifact = "commons-io:commons-io:2.4",
        sha1 = "b1b6ea3b7e4aa4f492509a4952029cd8e48019ad",
    )

    # Google internal dependencies: these are developed at Google, so there is
    # no concern about version skew.

    FLOGGER_VERS = "0.6"

    maven_jar(
        name = "flogger",
        artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
        sha1 = "155dc6e303a58f7bbff5d2cd1a259de86827f4fe",
    )

    maven_jar(
        name = "flogger-log4j-backend",
        artifact = "com.google.flogger:flogger-log4j-backend:" + FLOGGER_VERS,
        sha1 = "9743841bf10309163effd8ddf882b5d5190cc9d9",
    )

    maven_jar(
        name = "flogger-system-backend",
        artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
        sha1 = "0f0ccf8923c6c315f2f57b108bcc6e46ccd88777",
    )

    maven_jar(
        name = "guava",
        artifact = "com.google.guava:guava:" + GUAVA_VERSION,
        sha1 = GUAVA_BIN_SHA1,
    )

    maven_jar(
        name = "guava-testlib",
        artifact = "com.google.guava:guava-testlib:" + GUAVA_VERSION,
        sha1 = GUAVA_TESTLIB_BIN_SHA1,
    )

    GUICE_VERS = "5.0.1"

    maven_jar(
        name = "guice-library",
        artifact = "com.google.inject:guice:" + GUICE_VERS,
        sha1 = "0dae7556b441cada2b4f0a2314eb68e1ff423429",
    )

    maven_jar(
        name = "guice-assistedinject",
        artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERS,
        sha1 = "62e02f2aceb7d90ba354584dacc018c1e94ff01c",
    )

    maven_jar(
        name = "guice-servlet",
        artifact = "com.google.inject.extensions:guice-servlet:" + GUICE_VERS,
        sha1 = "f527009d51f172a2e6937bfb55fcb827e2e2386b",
    )

    # Keep this version of Soy synchronized with the version used in Gitiles.
    maven_jar(
        name = "soy",
        artifact = "com.google.template:soy:2021-02-01",
        sha1 = "8e833744832ba88059205a1e30e0898f925d8cb5",
    )

    # Test-only dependencies below.
    maven_jar(
        name = "cglib-3_2",
        artifact = "cglib:cglib-nodep:3.2.6",
        sha1 = "92bf48723d277d6efd1150b2f7e9e1e92cb56caf",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:1.3",
        sha1 = "dc13ae4faca6df981fc7aeb5a522d9db446d5d50",
    )

    DOCKER_JAVA_VERS = "3.2.8"

    maven_jar(
        name = "docker-java-api",
        artifact = "com.github.docker-java:docker-java-api:" + DOCKER_JAVA_VERS,
        sha1 = "4ac22a72d546a9f3523cd4b5fabffa77c4a6ec7c",
    )

    maven_jar(
        name = "docker-java-transport",
        artifact = "com.github.docker-java:docker-java-transport:" + DOCKER_JAVA_VERS,
        sha1 = "c3b5598c67d0a5e2e780bf48f520da26b9915eab",
    )

    # https://github.com/docker-java/docker-java/blob/3.2.8/pom.xml#L61
    # <=> DOCKER_JAVA_VERS
    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:2.10.3",
        sha1 = "0f63b3b1da563767d04d2e4d3fc1ae0cdeffebe7",
    )

    maven_jar(
        name = "testcontainers",
        artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
        sha1 = "95c6cfde71c2209f0c29cb14e432471e0b111880",
    )

    maven_jar(
        name = "duct-tape",
        artifact = "org.rnorth.duct-tape:duct-tape:1.0.8",
        sha1 = "92edc22a9ab2f3e17c9bf700aaee377d50e8b530",
    )

    maven_jar(
        name = "visible-assertions",
        artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.2",
        sha1 = "20d31a578030ec8e941888537267d3123c2ad1c1",
    )

    maven_jar(
        name = "jna",
        artifact = "net.java.dev.jna:jna:5.5.0",
        sha1 = "0e0845217c4907822403912ad6828d8e0b256208",
    )

    maven_jar(
        name = "jimfs",
        artifact = "com.google.jimfs:jimfs:1.2",
        sha1 = "48462eb319817c90c27d377341684b6b81372e08",
    )

    TRUTH_VERS = "1.1"

    maven_jar(
        name = "truth",
        artifact = "com.google.truth:truth:" + TRUTH_VERS,
        sha1 = "6a096a16646559c24397b03f797d0c9d75ee8720",
    )

    maven_jar(
        name = "truth-java8-extension",
        artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
        sha1 = "258db6eb8df61832c5c059ed2bc2e1c88683e92f",
    )

    maven_jar(
        name = "truth-liteproto-extension",
        artifact = "com.google.truth.extensions:truth-liteproto-extension:" + TRUTH_VERS,
        sha1 = "bf65afa13aa03330e739bcaa5d795fe0f10fbf20",
    )

    maven_jar(
        name = "truth-proto-extension",
        artifact = "com.google.truth.extensions:truth-proto-extension:" + TRUTH_VERS,
        sha1 = "64cba89cf87c1d84cb8c81d06f0b9c482f10b4dc",
    )

    LUCENE_VERS = "6.6.5"

    maven_jar(
        name = "lucene-core",
        artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
        sha1 = "2983f80b1037e098209657b0ca9176827892d0c0",
    )

    maven_jar(
        name = "lucene-analyzers-common",
        artifact = "org.apache.lucene:lucene-analyzers-common:" + LUCENE_VERS,
        sha1 = "6094f91071d90570b7f5f8ce481d5de7d2d2e9d5",
    )

    maven_jar(
        name = "backward-codecs",
        artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
        sha1 = "460a19e8d1aa7d31e9614cf528a6cb508c9e823d",
    )

    maven_jar(
        name = "lucene-misc",
        artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
        sha1 = "ce3a1b7b6a92b9af30791356a4bd46d1cea6cc1e",
    )

    maven_jar(
        name = "lucene-queryparser",
        artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
        sha1 = "2db9ca0086a4b8e0b9bc9f08a9b420303168e37c",
    )
