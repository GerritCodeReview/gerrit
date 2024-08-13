"""
Dependencies that are exempted from requiring a Library-Compliance approval
from a Googler.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("//tools/bzl:maven_jar.bzl", "maven_jar")

AUTO_COMMON_VERSION = "1.2.2"

AUTO_FACTORY_VERSION = "1.0.1"

AUTO_VALUE_VERSION = "1.10.4"

GUAVA_VERSION = "33.0.0-jre"

GUAVA_BIN_SHA1 = "161ba27964a62f241533807a46b8711b13c1d94b"

GUAVA_TESTLIB_BIN_SHA1 = "cf21e00fcc92786094fb5b376500f50d06878b0b"

GUAVA_DOC_URL = "https://google.github.io/guava/releases/" + GUAVA_VERSION + "/api/docs/"

def archive_dependencies():
    return [
        {
            "name": "platforms",
            "urls": [
                "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
                "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
            ],
            "sha256": "218efe8ee736d26a3572663b374a253c012b716d8af0c07e842e82f238a0a7ee",
        },
        {
            "name": "bazel_features",
            "strip_prefix": "bazel_features-1.11.0",
            "urls": [
                "https://github.com/bazel-contrib/bazel_features/releases/download/v1.11.0/bazel_features-v1.11.0.tar.gz",
            ],
            "sha256": "2cd9e57d4c38675d321731d65c15258f3a66438ad531ae09cb8bb14217dc8572",
        },
        {
            "name": "rules_java",
            "urls": [
                "https://github.com/bazelbuild/rules_java/releases/download/7.6.1/rules_java-7.6.1.tar.gz",
            ],
            "sha256": "f8ae9ed3887df02f40de9f4f7ac3873e6dd7a471f9cddf63952538b94b59aeb3",
        },
        {
            "name": "rules_proto",
            "strip_prefix": "rules_proto-6.0.0",
            "urls": [
                "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz",
            ],
            "sha256": "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
        },
        {
            "name": "toolchains_protoc",
            "strip_prefix": "toolchains_protoc-0.3.0",
            "urls": [
                "https://github.com/aspect-build/toolchains_protoc/releases/download/v0.3.0/toolchains_protoc-v0.3.0.tar.gz",
            ],
            "sha256": "117af61ee2f1b9b014dcac7c9146f374875551abb8a30e51d1b3c5946d25b142",
        },
        {
            "name": "ubuntu2204_jdk17",
            "strip_prefix": "rbe_autoconfig-5.1.0",
            "urls": [
                "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v5.1.0.tar.gz",
                "https://github.com/davido/rbe_autoconfig/releases/download/v5.1.0/v5.1.0.tar.gz",
            ],
            "sha256": "8ea82b81c9707e535ff93ef5349d11e55b2a23c62bcc3b0faaec052144aed87d",
        },
    ]

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    for dependency in archive_dependencies():
        params = {}
        params.update(**dependency)
        maybe(http_archive, params.pop("name"), **params)

    maven_jar(
        name = "log4j",
        artifact = "ch.qos.reload4j:reload4j:1.2.25",
        sha1 = "45921e383a1001c2a599fc4c6cf59af80cdd1cf1",
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

    SSHD_VERS = "2.14.0"

    maven_jar(
        name = "sshd-osgi",
        artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
        sha1 = "6ef66228a088f8ac1383b2ff28f3102f80ebc01a",
    )

    maven_jar(
        name = "sshd-sftp",
        artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
        sha1 = "c070ac920e72023ae9ab0a3f3a866bece284b470",
    )

    maven_jar(
        name = "eddsa",
        artifact = "net.i2p.crypto:eddsa:0.3.0",
        sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.0.23",
        sha1 = "391228b25d3a24434b205444cd262780a9ea61e7",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:" + SSHD_VERS,
        sha1 = "05e1293af53a196ac3c5a4b01dd88985e8672e9e",
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
        sha1 = "90f9629eaa123f88551cc26a64bc386967ee24cc",
    )

    maven_jar(
        name = "auto-value-annotations",
        artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
        sha1 = "9679de8286eb0a151db6538ba297a8951c4a1224",
    )

    maven_jar(
        name = "error-prone-annotations",
        artifact = "com.google.errorprone:error_prone_annotations:2.22.0",
        sha1 = "bfb9e4281a4cea34f0ec85b3acd47621cfab35b4",
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
        name = "flogger-google-extensions",
        artifact = "com.google.flogger:google-extensions:" + FLOGGER_VERS,
        sha1 = "c49493bd815e3842b8406e21117119d560399977",
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
        artifact = "com.google.code.gson:gson:2.10.1",
        sha1 = "b3add478d4382b78ea20b1671390a858002feb6c",
    )

    maven_jar(
        name = "protobuf-java",
        artifact = "com.google.protobuf:protobuf-java:3.25.3",
        sha1 = "d3200261955f3298e0d85c9892201e70492ce8eb",
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

    TRUTH_VERS = "1.4.2"

    maven_jar(
        name = "truth",
        artifact = "com.google.truth:truth:" + TRUTH_VERS,
        sha1 = "2322d861290bd84f84cbb178e43539725a4588fd",
    )

    maven_jar(
        name = "truth-java8-extension",
        artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
        sha1 = "bfa44a01e1bb5a1df50bc9c678d6588b4d9eb73a",
    )

    maven_jar(
        name = "truth-liteproto-extension",
        artifact = "com.google.truth.extensions:truth-liteproto-extension:" + TRUTH_VERS,
        sha1 = "062a2716b3b0ba9d8e72c913dad43a8139b12202",
    )

    maven_jar(
        name = "truth-proto-extension",
        artifact = "com.google.truth.extensions:truth-proto-extension:" + TRUTH_VERS,
        sha1 = "53cfc94dfa435c5dcd6f8b6844b82b423ea0a5af",
    )

    LUCENE_VERS = "9.8.0"

    maven_jar(
        name = "lucene-core",
        artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
        sha1 = "5e8421c5f8573bcf22e9265fc7e19469545a775a",
    )

    maven_jar(
        name = "lucene-analyzers-common",
        artifact = "org.apache.lucene:lucene-analysis-common:" + LUCENE_VERS,
        sha1 = "36f0363325ca7bf62c180160d1ed5165c7c37795",
    )

    maven_jar(
        name = "lucene-backward-codecs",
        artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
        sha1 = "e98fb408028f40170e6d87c16422bfdc0bb2e392",
    )

    maven_jar(
        name = "lucene-misc",
        artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
        sha1 = "9a57b049cf51a5e9c9c1909c420f645f1b6f9a54",
    )

    maven_jar(
        name = "lucene-queryparser",
        artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
        sha1 = "982faf2bfa55542bf57fbadef54c19ac00f57cae",
    )

    # JGit's transitive dependencies
    maven_jar(
        name = "hamcrest",
        artifact = "org.hamcrest:hamcrest:2.2",
        sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
    )
