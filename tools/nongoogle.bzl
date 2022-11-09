load("//tools/bzl:maven_jar.bzl", "maven_jar")

GUAVA_VERSION = "30.1-jre"

GUAVA_BIN_SHA1 = "00d0c3ce2311c9e36e73228da25a6e99b2ab826f"

GUAVA_DOC_URL = "https://google.github.io/guava/releases/" + GUAVA_VERSION + "/api/docs/"

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    maven_jar(
        name = "log4j",
        artifact = "ch.qos.reload4j:reload4j:1.2.19",
        sha1 = "4eae9978468c5e885a6fb44df7e2bbc07a20e6ce",
    )

    SLF4J_VERS = "1.7.36"

    maven_jar(
        name = "log-api",
        artifact = "org.slf4j:slf4j-api:" + SLF4J_VERS,
        sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
    )

    maven_jar(
        name = "log-ext",
        artifact = "org.slf4j:slf4j-ext:" + SLF4J_VERS,
        sha1 = "99f282aea4b6dbca04d00f0ade6e5ed61ee7091a",
    )

    maven_jar(
        name = "impl-log4j",
        artifact = "org.slf4j:slf4j-reload4j:" + SLF4J_VERS,
        sha1 = "db708f7d959dee1857ac524636e85ecf2e1781c1",
    )

    maven_jar(
        name = "jcl-over-slf4j",
        artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
        sha1 = "d877e195a05aca4a2f1ad2ff14bfec1393af4b5e",
    )

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

    SSHD_VERS = "2.9.1"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "9ed1a653da98a1aabe3ae092ee8310299718e914",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "6d01cb8138e60e97e3de08e96cc5a094c8ce2cac",
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
        sha1 = "5ab797b99630bb0c3e9ebcd8a3a6cad46408a79a",
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
        name = "jruby",
        artifact = "org.jruby:jruby-complete:9.1.17.0",
        sha1 = "76716d529710fc03d1d429b43e3cedd4419f78d4",
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

    # JGit's transitive dependencies
    maven_jar(
        name = "hamcrest",
        artifact = "org.hamcrest:hamcrest:2.2",
        sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
    )
