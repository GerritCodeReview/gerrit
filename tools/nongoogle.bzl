"""
Dependencies that are exempted from requiring a Library-Compliance approval
from a Googler.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("//tools/bzl:maven_jar.bzl", "maven_jar")

AUTO_COMMON_VERSION = "1.2.2"

AUTO_FACTORY_VERSION = "1.0.1"

AUTO_VALUE_VERSION = "1.11.0"

GUAVA_VERSION = "33.4.8-jre"

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
        artifact = "org.tukaani:xz:1.10",
        sha1 = "1be8166f89e035a56c6bfc67dbc423996fe577e2",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.2.37",
        sha1 = "2d7ecb8e2b4d292c7eb87ab28cda586cb9773056",
    )

    SSHD_VERS = "2.16.0"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "87cab2aaa6e06c5d48d746e90f0b3635f8c06419",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "09d9e7024535fb4a3f74367ba7e0a2f5093af638",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.2.4",
        sha1 = "f76b231c8a332640a4b1deef5262c603b088be02",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:" + SSHD_VERS,
        sha1 = "9247372c4b7fc88d69d4e1bd7de281b3b74f1b3f",
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
        artifact = "commons-io:commons-io:2.20.0",
        sha1 = "36f3474daec2849c149e877614e7f979b2082cd2",
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
        artifact = "com.google.errorprone:error_prone_annotations:2.36.0",
        sha1 = "227d4d4957ccc3dc5761bd897e3a0ee587e750a7",
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
        sha1 = "e70a3268e6cd3e7d458aa15787ce6811c34e96ae",
    )

    maven_jar(
        name = "guava-testlib",
        artifact = "com.google.guava:guava-testlib:" + GUAVA_VERSION,
        sha1 = "7443cfac765d74b8a31bbe6c49357715e32f714c",
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
        artifact = "com.google.code.gson:gson:2.12.1",
        sha1 = "4e773a317740b83b43cfc3d652962856041697cb",
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
        artifact = "org.hamcrest:hamcrest:2.2",
        sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
    )
