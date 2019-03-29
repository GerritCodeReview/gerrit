load("//tools/bzl:maven_jar.bzl", "maven_jar")

GUAVA_VERSION = "30.1-jre"

GUAVA_BIN_SHA1 = "00d0c3ce2311c9e36e73228da25a6e99b2ab826f"

GUAVA_TESTLIB_BIN_SHA1 = "798c3827308605cd69697d8f1596a1735d3ef6e2"

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
        artifact = "org.tukaani:xz:1.9",
        sha1 = "1ea4bec1a921180164852c65006d928617bd2caf",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.1.12.1",
        sha1 = "cb2f351bf4463751201f43bb99865235d5ba07ca",
    )

    SSHD_VERS = "2.8.0"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "b2a59b73c045f40d5722b9160d4f909a646d86c9",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "d3cd9bc8d335b3ed1a86d2965deb4d202de27442",
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
        sha1 = "02f78100cce376198be798a37c84aaf945e8a0f7",
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

    maven_jar(
        name = "error-prone-annotations",
        artifact = "com.google.errorprone:error_prone_annotations:2.10.0",
        sha1 = "9bc20b94d3ac42489cf6ce1e42509c86f6f861a1",
    )

    FLOGGER_VERS = "0.7.4"

    maven_jar(
        name = "flogger",
        artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
        sha1 = "cec29ed8b58413c2e935d86b12d6b696dc285419",
    )

    maven_jar(
        name = "flogger-log4j-backend",
        artifact = "com.google.flogger:flogger-log4j-backend:" + FLOGGER_VERS,
        sha1 = "7486b1c0138647cd7714eccb8ce37b5f2ae20a76",
    )

    maven_jar(
        name = "flogger-system-backend",
        artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
        sha1 = "4bee7ebbd97c63ca7fb17529aeb49a57b670d061",
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

    LUCENE_VERS = "8.11.1"

    maven_jar(
        name = "lucene-core",
        artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
        sha1 = "75dcf930ece95581af3f31af5692e4963fc7ad8e",
    )

    maven_jar(
        name = "lucene-analyzers-common",
        artifact = "org.apache.lucene:lucene-analyzers-common:" + LUCENE_VERS,
        sha1 = "e50af506f271a3f7246da054a2569b42ff73abb2",
    )

    maven_jar(
        name = "backward-codecs",
        artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
        sha1 = "04fafe3e150805be67778ba5cdbae2560c893ad8",
    )

    maven_jar(
        name = "lucene-misc",
        artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
        sha1 = "c412eafb48b9623d1046d72d7a2139d69c80548b",
    )

    maven_jar(
        name = "lucene-queryparser",
        artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
        sha1 = "5a44df2cb26fa1e0e64be53fe474c7f1d5a3e634",
    )

    # JGit's transitive dependencies
    maven_jar(
        name = "hamcrest",
        artifact = "org.hamcrest:hamcrest:2.2",
        sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
    )
