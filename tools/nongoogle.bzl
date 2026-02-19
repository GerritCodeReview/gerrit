"""
Dependencies that are exempted from requiring a Library-Compliance approval
from a Googler.
"""

load("//tools/bzl:maven_jar.bzl", "maven_jar")

AUTO_COMMON_VERSION = "1.2.2"

AUTO_FACTORY_VERSION = "1.0.1"

AUTO_VALUE_VERSION = "1.11.0"

GUAVA_VERSION = "33.5.0-jre"

GUAVA_DOC_URL = "https://guava.dev/releases/" + GUAVA_VERSION + "/api/docs/"

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    maven_jar(
        name = "log4j",
        artifact = "ch.qos.reload4j:reload4j:1.2.26",
        sha1 = "f9a29cea570c15844d2ec98bf8e2e523017a6a53",
    )

    SLF4J_VERS = "2.0.17"

    maven_jar(
        name = "log-api",
        artifact = "org.slf4j:slf4j-api:" + SLF4J_VERS,
        sha1 = "d9e58ac9c7779ba3bf8142aff6c830617a7fe60f",
    )

    maven_jar(
        name = "log-ext",
        artifact = "org.slf4j:slf4j-ext:" + SLF4J_VERS,
        sha1 = "2038418e2312c3559629841f100d19bd3e02483b",
    )

    maven_jar(
        name = "impl-log4j",
        artifact = "org.slf4j:slf4j-reload4j:" + SLF4J_VERS,
        sha1 = "334b175c8ce44b3a6815fc92971916f36e15d000",
    )

    maven_jar(
        name = "jcl-over-slf4j",
        artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
        sha1 = "76ea503eb688f06556a9ba69995d7eab63e34531",
    )

    maven_jar(
        name = "j2objc",
        artifact = "com.google.j2objc:j2objc-annotations:1.1",
        sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
    )

    # Transitive dependency of commons-compress
    maven_jar(
        name = "tukaani-xz",
        artifact = "org.tukaani:xz:1.11",
        sha1 = "bdfd1774efb216f506f4f3c5b08c205b308c50aa",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.2.37",
        sha1 = "2d7ecb8e2b4d292c7eb87ab28cda586cb9773056",
    )

    SSHD_VERS = "2.17.1"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "3d159a03c93fbc6e9022742d7a8616abc50dd0fa",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "91085ab6f205ad68007a8034e58d46a82a0d126e",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.2.4",
        sha1 = "f76b231c8a332640a4b1deef5262c603b088be02",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:" + SSHD_VERS,
        sha1 = "8c4a151822a6741a2b53c36eee9c0e2d4d70e447",
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
        artifact = "xerces:xercesImpl:2.12.2",
        attach_source = False,
        sha1 = "f051f988aa2c9b4d25d05f95742ab0cc3ed789e2",
    )

    maven_jar(
        name = "jruby",
        artifact = "org.jruby:jruby-complete:9.1.17.0",
        sha1 = "76716d529710fc03d1d429b43e3cedd4419f78d4",
    )

    maven_jar(
        name = "commons-io",
        artifact = "commons-io:commons-io:2.21.0",
        sha1 = "52a6f68fe5afe335cde95461dd5c3412f04996f7",
    )

    # Google internal dependencies: these are developed at Google, so there is
    # no concern about version skew.

    maven_jar(
        name = "auto-common",
        artifact = "com.google.auto:auto-common:" + AUTO_COMMON_VERSION,
        sha1 = "9d38f10e22411681cf1d1ee3727e002af19f2c9e",
    )

    maven_jar(
        name = "auto-factory",
        artifact = "com.google.auto.factory:auto-factory:" + AUTO_FACTORY_VERSION,
        sha1 = "f81ece06b6525085da217cd900116f44caafe877",
    )

    maven_jar(
        name = "auto-service-annotations",
        artifact = "com.google.auto.service:auto-service-annotations:" + AUTO_FACTORY_VERSION,
        sha1 = "ac86dacc0eb9285ea9d42eee6aad8629ca3a7432",
    )

    maven_jar(
        name = "auto-value",
        artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
        sha1 = "d1fd0e74d20e922145c3fede3f05e246bb6be281",
    )

    maven_jar(
        name = "auto-value-annotations",
        artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
        sha1 = "f0d047931d07cfbc6fa4079854f181ff62891d6f",
    )

    maven_jar(
        name = "error-prone-annotations",
        artifact = "com.google.errorprone:error_prone_annotations:2.46.0",
        sha1 = "4ecb5d2392c38c46e6cb65e1bf60be708d97005d",
    )

    FLOGGER_VERS = "0.8"

    maven_jar(
        name = "flogger",
        artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
        sha1 = "753f5ef5b084dbff3ab3030158ed128711745b06",
    )

    maven_jar(
        name = "flogger-log4j-backend",
        artifact = "com.google.flogger:flogger-log4j-backend:" + FLOGGER_VERS,
        sha1 = "7486b1c0138647cd7714eccb8ce37b5f2ae20a76",
    )

    maven_jar(
        name = "flogger-google-extensions",
        artifact = "com.google.flogger:google-extensions:" + FLOGGER_VERS,
        sha1 = "42781a3d970e18c96bb0a8d3ddd94d6237aa0612",
    )

    maven_jar(
        name = "flogger-system-backend",
        artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
        sha1 = "24b2a20600b1f313540ead4b393813efa13ce14a",
    )

    maven_jar(
        name = "guava",
        artifact = "com.google.guava:guava:" + GUAVA_VERSION,
        sha1 = "8699de25f2f979108d6c1b804a7ba38cda1116bc",
    )

    maven_jar(
        name = "guava-testlib",
        artifact = "com.google.guava:guava-testlib:" + GUAVA_VERSION,
        sha1 = "a2a266c8d13c78a6828dc077d6a46f34956378a9",
    )

    GUICE_VERS = "6.0.0"

    maven_jar(
        name = "guice-library",
        artifact = "com.google.inject:guice:" + GUICE_VERS,
        sha1 = "9b422c69c4fa1ea95b2615444a94fede9b02fc40",
    )

    maven_jar(
        name = "guice-assistedinject",
        artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERS,
        sha1 = "849d991e4adf998cb9877124fe74b063c88726cf",
    )

    maven_jar(
        name = "guice-servlet",
        artifact = "com.google.inject.extensions:guice-servlet:" + GUICE_VERS,
        sha1 = "1a505f5f1a269e01946790e863178a5055de4fa0",
    )

    # Keep this version of Soy synchronized with the version used in Gitiles.
    maven_jar(
        name = "soy",
        artifact = "com.google.template:soy:2024-01-30",
        sha1 = "6e9ccb00926325c7a9293ed05a2eaf56ea15d60e",
    )

    maven_jar(
        name = "gson",
        artifact = "com.google.code.gson:gson:2.13.2",
        sha1 = "48b8230771e573b54ce6e867a9001e75977fe78e",
    )

    maven_jar(
        name = "protobuf-java",
        artifact = "com.google.protobuf:protobuf-java:4.33.2",
        sha1 = "c85bf5de1ad10453792675f6515401f7b8eb6860",
    )

    maven_jar(
        name = "jimfs",
        artifact = "com.google.jimfs:jimfs:1.2",
        sha1 = "48462eb319817c90c27d377341684b6b81372e08",
    )

    TRUTH_VERS = "1.4.4"

    maven_jar(
        name = "truth",
        artifact = "com.google.truth:truth:" + TRUTH_VERS,
        sha1 = "33810058273a2a3b6ce6d1f8c8621bfc85493f67",
    )

    maven_jar(
        name = "truth-java8-extension",
        artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
        sha1 = "49129ba5889b6811e96a9d49af61122f21314670",
    )

    maven_jar(
        name = "truth-liteproto-extension",
        artifact = "com.google.truth.extensions:truth-liteproto-extension:" + TRUTH_VERS,
        sha1 = "b6282dbc163474900ac914c2dbeca101008f72da",
    )

    maven_jar(
        name = "truth-proto-extension",
        artifact = "com.google.truth.extensions:truth-proto-extension:" + TRUTH_VERS,
        sha1 = "4b88990178086ffdd482246b35a5a48b4d26896c",
    )

    LUCENE_VERS = "10.2.2"

    maven_jar(
        name = "lucene-core",
        artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
        sha1 = "336a9c4b24e5704bd5fd71af794cce80f479a3ae",
    )

    maven_jar(
        name = "lucene-analyzers-common",
        artifact = "org.apache.lucene:lucene-analysis-common:" + LUCENE_VERS,
        sha1 = "2c35eb96330d96b6ffb61856ce2cd886a5656c81",
    )

    maven_jar(
        name = "lucene-backward-codecs",
        artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
        sha1 = "848ccaaadbcc97c84c09ad808fe4354af00449d9",
    )

    maven_jar(
        name = "lucene-misc",
        artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
        sha1 = "047de3cefc3aa78ba11593d72c60f5b17a611c73",
    )

    maven_jar(
        name = "lucene-queryparser",
        artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
        sha1 = "bb94dc5a00f01ccc7dc6804388bc7fe9f0070c75",
    )

    maven_jar(
        name = "h2",
        artifact = "com.h2database:h2:2.4.240",
        sha1 = "686180ad33981ad943fdc0ab381e619b2c2fdfe5",
    )

    # JGit's transitive dependencies
    maven_jar(
        name = "hamcrest",
        artifact = "org.hamcrest:hamcrest:3.0",
        sha1 = "8fd9b78a8e6a6510a078a9e30e9e86a6035cfaf7",
    )
