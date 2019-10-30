workspace(name = "gerrit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")

http_archive(
    name = "bazel_toolchains",
    sha256 = "88e818f9f03628eef609c8429c210ecf265ffe46c2af095f36c7ef8b1855fef5",
    strip_prefix = "bazel-toolchains-92dd8a7a518a2fb7ba992d47c8b38299fe0be825",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/92dd8a7a518a2fb7ba992d47c8b38299fe0be825.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/92dd8a7a518a2fb7ba992d47c8b38299fe0be825.tar.gz",
    ],
)

load("@bazel_toolchains//rules:rbe_repo.bzl", "rbe_autoconfig")

# Creates a default toolchain config for RBE.
# Use this as is if you are using the rbe_ubuntu16_04 container,
# otherwise refer to RBE docs.
rbe_autoconfig(name = "rbe_default")

http_archive(
    name = "bazel_skylib",
    sha256 = "2ea8a5ed2b448baf4a6855d3ce049c4c452a6470b1efd1504fdb7c1c134d220a",
    strip_prefix = "bazel-skylib-0.8.0",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/0.8.0.tar.gz"],
)

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "0409f8bd2a8b6fd1db289cdc0acb394dafd69f60a86d0169bc6495e648e01587",
    strip_prefix = "rules_closure-18f8acf24ae0d03a9c3ee872ff91dcfbf383d69e",
    urls = ["https://github.com/bazelbuild/rules_closure/archive/18f8acf24ae0d03a9c3ee872ff91dcfbf383d69e.tar.gz"],
)

# File is specific to Polymer and copied from the Closure Github -- should be
# synced any time there are major changes to Polymer.
# https://github.com/google/closure-compiler/blob/master/contrib/externs/polymer-1.0.js
http_file(
    name = "polymer_closure",
    downloaded_file_path = "polymer_closure.js",
    sha256 = "5a589bdba674e1fec7188e9251c8624ebf2d4d969beb6635f9148f420d1e08b1",
    urls = ["https://raw.githubusercontent.com/google/closure-compiler/775609aad61e14aef289ebec4bfc09ad88877f9e/contrib/externs/polymer-1.0.js"],
)

# Check Bazel version when invoked by Bazel directly
load("//tools/bzl:bazelisk_version.bzl", "bazelisk_version")

bazelisk_version(name = "bazelisk_version")

load("@bazelisk_version//:check.bzl", "check_bazel_version")

check_bazel_version()

load("@io_bazel_rules_closure//closure:repositories.bzl", "rules_closure_dependencies", "rules_closure_toolchains")

# Prevent redundant loading of dependencies.
rules_closure_dependencies(
    omit_aopalliance = True,
    omit_args4j = True,
    omit_bazel_skylib = True,
    omit_javax_inject = True,
    omit_rules_cc = True,
)

rules_closure_toolchains()

# This has to be done after loading of rules_closure, because it loads rules_java
load("//lib/codemirror:cm.bzl", "CM_VERSION", "DIFF_MATCH_PATCH_VERSION")

ANTLR_VERS = "3.5.2"

maven_jar(
    name = "java-runtime",
    artifact = "org.antlr:antlr-runtime:" + ANTLR_VERS,
    sha1 = "cd9cd41361c155f3af0f653009dcecb08d8b4afd",
)

maven_jar(
    name = "stringtemplate",
    artifact = "org.antlr:stringtemplate:4.0.2",
    sha1 = "e28e09e2d44d60506a7bcb004d6c23ff35c6ac08",
)

maven_jar(
    name = "org-antlr",
    artifact = "org.antlr:antlr:" + ANTLR_VERS,
    sha1 = "c4a65c950bfc3e7d04309c515b2177c00baf7764",
)

maven_jar(
    name = "antlr27",
    artifact = "antlr:antlr:2.7.7",
    attach_source = False,
    sha1 = "83cd2cd674a217ade95a4bb83a8a14f351f48bd0",
)

GUICE_VERS = "4.2.0"

maven_jar(
    name = "guice-library",
    artifact = "com.google.inject:guice:" + GUICE_VERS,
    sha1 = "25e1f4c1d528a1cffabcca0d432f634f3132f6c8",
)

maven_jar(
    name = "guice-assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERS,
    sha1 = "e7270305960ad7db56f7e30cb9df6be9ff1cfb45",
)

maven_jar(
    name = "guice-servlet",
    artifact = "com.google.inject.extensions:guice-servlet:" + GUICE_VERS,
    sha1 = "f57581625c36c148f088d9f52a568d5bdf12c61d",
)

maven_jar(
    name = "aopalliance",
    artifact = "aopalliance:aopalliance:1.0",
    sha1 = "0235ba8b489512805ac13a8f9ea77a1ca5ebe3e8",
)

maven_jar(
    name = "javax_inject",
    artifact = "javax.inject:javax.inject:1",
    sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
)

maven_jar(
    name = "servlet-api-3_1",
    artifact = "org.apache.tomcat:tomcat-servlet-api:8.0.24",
    sha1 = "5d9e2e895e3111622720157d0aa540066d5fce3a",
)

GWT_VERS = "2.8.2"

maven_jar(
    name = "user",
    artifact = "com.google.gwt:gwt-user:" + GWT_VERS,
    sha1 = "a2b9be2c996a658c4e009ba652a9c6a81c88a797",
)

maven_jar(
    name = "dev",
    artifact = "com.google.gwt:gwt-dev:" + GWT_VERS,
    sha1 = "7a87e060bbf129386b7ae772459fb9f87297c332",
)

maven_jar(
    name = "javax-validation",
    artifact = "javax.validation:validation-api:1.0.0.GA",
    sha1 = "b6bd7f9d78f6fdaa3c37dae18a4bd298915f328e",
    src_sha1 = "7a561191db2203550fbfa40d534d4997624cd369",
)

maven_jar(
    name = "jsinterop-annotations",
    artifact = "com.google.jsinterop:jsinterop-annotations:1.0.2",
    sha1 = "abd7319f53d018e11108a88f599bd16492448dd2",
    src_sha1 = "33716f8aef043f2f02b78ab4a1acda6cd90a7602",
)

maven_jar(
    name = "ant",
    artifact = "ant:ant:1.6.5",
    attach_source = False,
    sha1 = "7d18faf23df1a5c3a43613952e0e8a182664564b",
)

maven_jar(
    name = "colt",
    artifact = "colt:colt:1.2.0",
    attach_source = False,
    sha1 = "0abc984f3adc760684d49e0f11ddf167ba516d4f",
)

maven_jar(
    name = "tapestry",
    artifact = "tapestry:tapestry:4.0.2",
    attach_source = False,
    sha1 = "e855a807425d522e958cbce8697f21e9d679b1f7",
)

maven_jar(
    name = "w3c-css-sac",
    artifact = "org.w3c.css:sac:1.3",
    sha1 = "cdb2dcb4e22b83d6b32b93095f644c3462739e82",
)

load("//lib/jgit:jgit.bzl", "jgit_repos")

jgit_repos()

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    attach_source = False,
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "gwtjsonrpc",
    artifact = "com.google.gerrit:gwtjsonrpc:1.11",
    sha1 = "0990e7eec9eec3a15661edcf9232acbac4aeacec",
    src_sha1 = "a682afc46284fb58197a173cb5818770a1e7834a",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.0",
    sha1 = "c4ba5371a29ac9b2ad6129b1d39ea38750043eff",
)

maven_jar(
    name = "gwtorm-client",
    artifact = "com.google.gerrit:gwtorm:1.18",
    sha1 = "f326dec463439a92ccb32f05b38345e21d0b5ecf",
    src_sha1 = "e0b973d5cafef3d145fa80cdf032fcead1186d29",
)

maven_jar(
    name = "joda-time",
    artifact = "joda-time:joda-time:2.9.9",
    sha1 = "f7b520c458572890807d143670c9b24f4de90897",
)

maven_jar(
    name = "joda-convert",
    artifact = "org.joda:joda-convert:1.8.1",
    sha1 = "675642ac208e0b741bc9118dcbcae44c271b992a",
)

load("//lib:guava.bzl", "GUAVA_BIN_SHA1", "GUAVA_VERSION")

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:" + GUAVA_VERSION,
    sha1 = GUAVA_BIN_SHA1,
)

maven_jar(
    name = "j2objc",
    artifact = "com.google.j2objc:j2objc-annotations:1.1",
    sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
)

maven_jar(
    name = "velocity",
    artifact = "org.apache.velocity:velocity:1.7",
    sha1 = "2ceb567b8f3f21118ecdec129fe1271dbc09aa7a",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.54",
    sha1 = "da3584329a263616e277e15462b387addd1b208d",
)

maven_jar(
    name = "juniversalchardet",
    artifact = "com.github.albfernandez:juniversalchardet:2.0.0",
    sha1 = "28c59f58f5adcc307604602e2aa89e2aca14c554",
)

SLF4J_VERS = "1.7.26"

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:" + SLF4J_VERS,
    sha1 = "77100a62c2e6f04b53977b9f541044d7d722693d",
)

maven_jar(
    name = "log-nop",
    artifact = "org.slf4j:slf4j-nop:" + SLF4J_VERS,
    sha1 = "6e211fdfb9a8723677031b95ac075ac54c879a0e",
)

maven_jar(
    name = "log-ext",
    artifact = "org.slf4j:slf4j-ext:" + SLF4J_VERS,
    sha1 = "31cdf122e000322e9efcb38913e9ab07825b17ef",
)

maven_jar(
    name = "impl-log4j",
    artifact = "org.slf4j:slf4j-log4j12:" + SLF4J_VERS,
    sha1 = "12f5c685b71c3027fd28bcf90528ec4ec74bf818",
)

maven_jar(
    name = "jcl-over-slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
    sha1 = "33fbc2d93de829fa5e263c5ce97f5eab8f57d53e",
)

maven_jar(
    name = "log4j",
    artifact = "log4j:log4j:1.2.17",
    sha1 = "5af35056b4d257e4b64b9e8069c0746e8b08629f",
)

maven_jar(
    name = "jsonevent-layout",
    artifact = "net.logstash.log4j:jsonevent-layout:1.7",
    sha1 = "507713504f0ddb75ba512f62763519c43cf46fde",
)

maven_jar(
    name = "json-smart",
    artifact = "net.minidev:json-smart:1.1.1",
    sha1 = "24a2f903d25e004de30ac602c5b47f2d4e420a59",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.0.26",
    sha1 = "01ebb18ebb3b379a74207d5af4ea7c8338ebd78b",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
)

maven_jar(
    name = "commons-collections",
    artifact = "commons-collections:commons-collections:3.2.2",
    sha1 = "8ad72fe39fa8c91eaaf12aadb21e0c3661fe26d5",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.13",
    sha1 = "15c5e9584200122924e50203ae210b57616b75ee",
)

maven_jar(
    name = "commons-lang",
    artifact = "commons-lang:commons-lang:2.6",
    sha1 = "0ce1edb914c94ebc388f086c6827e8bdeec71ac2",
)

maven_jar(
    name = "commons-dbcp",
    artifact = "commons-dbcp:commons-dbcp:1.4",
    sha1 = "30be73c965cc990b153a100aaaaafcf239f82d39",
)

maven_jar(
    name = "commons-pool",
    artifact = "commons-pool:commons-pool:1.5.5",
    sha1 = "7d8ffbdc47aa0c5a8afe5dc2aaf512f369f1d19b",
)

maven_jar(
    name = "commons-net",
    artifact = "commons-net:commons-net:3.5",
    sha1 = "342fc284019f590e1308056990fdb24a08f06318",
)

maven_jar(
    name = "commons-oro",
    artifact = "oro:oro:2.0.8",
    sha1 = "5592374f834645c4ae250f4c9fbb314c9369d698",
)

maven_jar(
    name = "commons-validator",
    artifact = "commons-validator:commons-validator:1.6",
    sha1 = "e989d1e87cdd60575df0765ed5bac65c905d7908",
)

maven_jar(
    name = "automaton",
    artifact = "dk.brics.automaton:automaton:1.11-8",
    sha1 = "6ebfa65eb431ff4b715a23be7a750cbc4cc96d0f",
)

maven_jar(
    name = "pegdown",
    artifact = "org.pegdown:pegdown:1.6.0",
    sha1 = "231ae49d913467deb2027d0b8a0b68b231deef4f",
)

maven_jar(
    name = "grappa",
    artifact = "com.github.parboiled1:grappa:1.0.4",
    sha1 = "ad4b44b9c305dad7aa1e680d4b5c8eec9c4fd6f5",
)

maven_jar(
    name = "jitescript",
    artifact = "me.qmx.jitescript:jitescript:0.4.0",
    sha1 = "2e35862b0435c1b027a21f3d6eecbe50e6e08d54",
)

GREENMAIL_VERS = "1.5.3"

maven_jar(
    name = "greenmail",
    artifact = "com.icegreen:greenmail:" + GREENMAIL_VERS,
    sha1 = "afabf8178312f7f220f74f1558e457bf54fa4253",
)

MAIL_VERS = "1.5.6"

maven_jar(
    name = "mail",
    artifact = "com.sun.mail:javax.mail:" + MAIL_VERS,
    sha1 = "ab5daef2f881c42c8e280cbe918ec4d7fdfd7efe",
)

MIME4J_VERS = "0.8.0"

maven_jar(
    name = "mime4j-core",
    artifact = "org.apache.james:apache-mime4j-core:" + MIME4J_VERS,
    sha1 = "d54f45fca44a2f210569656b4ca3574b42911c95",
)

maven_jar(
    name = "mime4j-dom",
    artifact = "org.apache.james:apache-mime4j-dom:" + MIME4J_VERS,
    sha1 = "6720c93d14225c3e12c4a69768a0370c80e376a3",
)

maven_jar(
    name = "jsoup",
    artifact = "org.jsoup:jsoup:1.9.2",
    sha1 = "5e3bda828a80c7a21dfbe2308d1755759c2fd7b4",
)

OW2_VERS = "5.1"

maven_jar(
    name = "ow2-asm",
    artifact = "org.ow2.asm:asm:" + OW2_VERS,
    sha1 = "5ef31c4fe953b1fd00b8a88fa1d6820e8785bb45",
)

maven_jar(
    name = "ow2-asm-analysis",
    artifact = "org.ow2.asm:asm-analysis:" + OW2_VERS,
    sha1 = "6d1bf8989fc7901f868bee3863c44f21aa63d110",
)

maven_jar(
    name = "ow2-asm-commons",
    artifact = "org.ow2.asm:asm-commons:" + OW2_VERS,
    sha1 = "25d8a575034dd9cfcb375a39b5334f0ba9c8474e",
)

maven_jar(
    name = "ow2-asm-tree",
    artifact = "org.ow2.asm:asm-tree:" + OW2_VERS,
    sha1 = "87b38c12a0ea645791ead9d3e74ae5268d1d6c34",
)

maven_jar(
    name = "ow2-asm-util",
    artifact = "org.ow2.asm:asm-util:" + OW2_VERS,
    sha1 = "b60e33a6bd0d71831e0c249816d01e6c1dd90a47",
)

AUTO_VALUE_VERSION = "1.6.2"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "e7eae562942315a983eea3e191b72d755c153620",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "ed193d86e0af90cc2342aedbe73c5d86b03fa09b",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.4",
    sha1 = "18a9a2ce6abf32ea1b5fd31dae5210ad93f4e5e3",
)

LUCENE_VERS = "5.5.5"

maven_jar(
    name = "lucene-core",
    artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
    sha1 = "c34bcd9274859dc07cfed2a935aaca90c4f4b861",
)

maven_jar(
    name = "lucene-analyzers-common",
    artifact = "org.apache.lucene:lucene-analyzers-common:" + LUCENE_VERS,
    sha1 = "e6b3f5d1b33ed24da7eef0a72f8062bd4652700c",
)

maven_jar(
    name = "backward-codecs",
    artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
    sha1 = "d1dee5c7676a313758adb30d7b0bd4c69a4cd214",
)

maven_jar(
    name = "lucene-misc",
    artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
    sha1 = "bc0eb46ba0377594cac7b0cdaab35562d7877521",
)

maven_jar(
    name = "lucene-queryparser",
    artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
    sha1 = "6c965eb5838a2ba58b0de0fd860a420dcda11937",
)

maven_jar(
    name = "mime-util",
    artifact = "eu.medsea.mimeutil:mime-util:2.1.3",
    attach_source = False,
    sha1 = "0c9cfae15c74f62491d4f28def0dff1dabe52a47",
)

PROLOG_VERS = "1.4.3"

PROLOG_REPO = GERRIT

maven_jar(
    name = "prolog-runtime",
    artifact = "com.googlecode.prolog-cafe:prolog-runtime:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "d5206556cbc76ffeab21313ffc47b586a1efbcbb",
)

maven_jar(
    name = "prolog-compiler",
    artifact = "com.googlecode.prolog-cafe:prolog-compiler:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "f37032cf1dec3e064427745bc59da5a12757a3b2",
)

maven_jar(
    name = "prolog-io",
    artifact = "com.googlecode.prolog-cafe:prolog-io:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "d02b2640b26f64036b6ba2b45e4acc79281cea17",
)

maven_jar(
    name = "cafeteria",
    artifact = "com.googlecode.prolog-cafe:prolog-cafeteria:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "e3b1860c63e57265e5435f890263ad82dafa724f",
)

maven_jar(
    name = "guava-retrying",
    artifact = "com.github.rholder:guava-retrying:2.0.0",
    sha1 = "974bc0a04a11cc4806f7c20a34703bd23c34e7f4",
)

maven_jar(
    name = "jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.1",
    sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
)

maven_jar(
    name = "blame-cache",
    artifact = "com/google/gitiles:blame-cache:0.2-5",
    attach_source = False,
    repository = GERRIT,
    sha1 = "50861b114350c598579ba66f99285e692e3c8d45",
)

# Keep this version of Soy synchronized with the version used in Gitiles.
maven_jar(
    name = "soy",
    artifact = "com.google.template:soy:2017-04-23",
    sha1 = "52f32a5a3801ab97e0909373ef7f73a3460d0802",
)

maven_jar(
    name = "html-types",
    artifact = "com.google.common.html.types:types:1.0.4",
    sha1 = "2adf4c8bfccc0ff7346f9186ac5aa57d829ad065",
)

maven_jar(
    name = "icu4j",
    artifact = "com.ibm.icu:icu4j:57.1",
    sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
)

maven_jar(
    name = "dropwizard-core",
    artifact = "io.dropwizard.metrics:metrics-core:4.0.5",
    sha1 = "b81ef162970cdb9f4512ee2da09715a856ff4c4c",
)

# When updating Bouncy Castle, also update it in bazlets.
BC_VERS = "1.60"

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BC_VERS,
    sha1 = "bd47ad3bd14b8e82595c7adaa143501e60842a84",
)

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BC_VERS,
    sha1 = "13c7a199c484127daad298996e95818478431a2c",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BC_VERS,
    sha1 = "d0c46320fbc07be3a24eb13a56cee4e3d38e0c75",
)

# TODO(davido): Remove exlusion of file system provider, when this issue is fixed:
# https://issues.apache.org/jira/browse/SSHD-736
maven_jar(
    name = "sshd",
    artifact = "org.apache.sshd:sshd-core:1.6.0",
    exclude = ["META-INF/services/java.nio.file.spi.FileSystemProvider"],
    sha1 = "548e2da643e88cda9d313efb2564a74f9943e491",
)

maven_jar(
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.2.0",
    sha1 = "0856a92559c4daf744cb27c93cd8b7eb1f8c4780",
)

maven_jar(
    name = "mina-core",
    artifact = "org.apache.mina:mina-core:2.0.16",
    sha1 = "f720f17643eaa7b0fec07c1d7f6272972c02bba4",
)

maven_jar(
    name = "h2",
    artifact = "com.h2database:h2:1.3.176",
    sha1 = "fd369423346b2f1525c413e33f8cf95b09c92cbd",
)

# Note that all of the following org.apache.httpcomponents have newer versions,
# but 4.4.1 is the only version that is available for all of them.
# TODO: Check what combination of new versions are compatible.
HTTPCOMP_VERS = "4.4.1"

maven_jar(
    name = "fluent-hc",
    artifact = "org.apache.httpcomponents:fluent-hc:" + HTTPCOMP_VERS,
    sha1 = "96fb842b68a44cc640c661186828b60590c71261",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:" + HTTPCOMP_VERS,
    sha1 = "016d0bc512222f1253ee6b64d389c84e22f697f0",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:" + HTTPCOMP_VERS,
    sha1 = "f5aa318bda4c6c8d688c9d00b90681dcd82ce636",
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
    artifact = "org.apache.httpcomponents:httpcore-nio:4.4.11",
    sha1 = "7d0a97d01d39cff9aa3e6db81f21fddb2435f4e6",
)

# Test-only dependencies below.

maven_jar(
    name = "jimfs",
    artifact = "com.google.jimfs:jimfs:1.1",
    sha1 = "8fbd0579dc68aba6186935cc1bee21d2f3e7ec1c",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

maven_jar(
    name = "hamcrest-core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

TRUTH_VERS = "0.35"

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:" + TRUTH_VERS,
    sha1 = "c08a7fde45e058323bcfa3f510d4fe1e2b028f37",
)

maven_jar(
    name = "truth-java8-extension",
    artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
    sha1 = "5457fdf91b1e954b070ad7f2db9bea5505da4bca",
)

# When bumping the easymock version number, make sure to also move powermock to a compatible version
maven_jar(
    name = "easymock",
    artifact = "org.easymock:easymock:3.1",
    sha1 = "3e127311a86fc2e8f550ef8ee4abe094bbcf7e7e",
)

maven_jar(
    name = "cglib-3_2",
    artifact = "cglib:cglib-nodep:3.2.0",
    sha1 = "cf1ca207c15b04ace918270b6cb3f5601160cdfd",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:1.3",
    sha1 = "dc13ae4faca6df981fc7aeb5a522d9db446d5d50",
)

POWERM_VERS = "1.6.1"

maven_jar(
    name = "powermock-module-junit4",
    artifact = "org.powermock:powermock-module-junit4:" + POWERM_VERS,
    sha1 = "ea8530b2848542624f110a393513af397b37b9cf",
)

maven_jar(
    name = "powermock-module-junit4-common",
    artifact = "org.powermock:powermock-module-junit4-common:" + POWERM_VERS,
    sha1 = "7222ced54dabc310895d02e45c5428ca05193cda",
)

maven_jar(
    name = "powermock-reflect",
    artifact = "org.powermock:powermock-reflect:" + POWERM_VERS,
    sha1 = "97d25eda8275c11161bcddda6ef8beabd534c878",
)

maven_jar(
    name = "powermock-api-easymock",
    artifact = "org.powermock:powermock-api-easymock:" + POWERM_VERS,
    sha1 = "aa740ecf89a2f64d410b3d93ef8cd6833009ef00",
)

maven_jar(
    name = "powermock-api-support",
    artifact = "org.powermock:powermock-api-support:" + POWERM_VERS,
    sha1 = "592ee6d929c324109d3469501222e0c76ccf0869",
)

maven_jar(
    name = "powermock-core",
    artifact = "org.powermock:powermock-core:" + POWERM_VERS,
    sha1 = "5afc1efce8d44ed76b30af939657bd598e45d962",
)

maven_jar(
    name = "javassist",
    artifact = "org.javassist:javassist:3.20.0-GA",
    sha1 = "a9cbcdfb7e9f86fbc74d3afae65f2248bfbf82a0",
)

maven_jar(
    name = "derby",
    artifact = "org.apache.derby:derby:10.12.1.1",
    attach_source = False,
    sha1 = "75070c744a8e52a7d17b8b476468580309d5cd09",
)

JETTY_VERS = "9.3.24.v20180605"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
    sha1 = "db09c8e226c07c46dc3d84626fc97955ec6bf8bf",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
    sha1 = "dfc4e2169f3dd91954804e7fdff9c4f67c63f385",
)

maven_jar(
    name = "jetty-servlets",
    artifact = "org.eclipse.jetty:jetty-servlets:" + JETTY_VERS,
    sha1 = "189db52691aacab9e13546429583765d143faf81",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
    sha1 = "0e629740cf0a08b353ec07c35eeab8fd06590041",
)

maven_jar(
    name = "jetty-jmx",
    artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
    sha1 = "aaeda444192a42389d2ac17a786329a1b6f4cf68",
)

maven_jar(
    name = "jetty-continuation",
    artifact = "org.eclipse.jetty:jetty-continuation:" + JETTY_VERS,
    sha1 = "44d7b4a9aef498abef268f3aade92daa459050f6",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
    sha1 = "f3d614a7c82b5ee028df78bdb3cdadb6c3be89bc",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
    sha1 = "f12a02ab2cb79eb9c3fa01daf28a58e8ea7cbea9",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
    sha1 = "f74fb3f999e658a2ddea397155e20da5b9126b5d",
)

maven_jar(
    name = "openid-consumer",
    artifact = "org.openid4java:openid4java:0.9.8",
    sha1 = "de4f1b33d3b0f0b2ab1d32834ec1190b39db4160",
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
    name = "postgresql",
    artifact = "org.postgresql:postgresql:42.2.5",
    sha1 = "951b7eda125f3137538a94e2cbdcf744088ad4c2",
)

maven_jar(
    name = "codemirror-minified",
    artifact = "org.webjars.npm:codemirror-minified:" + CM_VERSION,
    sha1 = "f84c178b11a188f416b4380bfb2b24f126453d28",
)

maven_jar(
    name = "codemirror-original",
    artifact = "org.webjars.npm:codemirror:" + CM_VERSION,
    sha1 = "5a1f6c10d5aef0b9d2ce513dcc1e2657e4af730d",
)

maven_jar(
    name = "diff-match-patch",
    artifact = "org.webjars:google-diff-match-patch:" + DIFF_MATCH_PATCH_VERSION,
    attach_source = False,
    sha1 = "0cf1782dbcb8359d95070da9176059a5a9d37709",
)

maven_jar(
    name = "commons-io",
    artifact = "commons-io:commons-io:2.2",
    sha1 = "83b5b8a7ba1c08f9e8c8ff2373724e33d3c1e22a",
)

maven_jar(
    name = "asciidoctor",
    artifact = "org.asciidoctor:asciidoctorj:1.5.7",
    sha1 = "8e8c1d8fc6144405700dd8df3b177f2801ac5987",
)

maven_jar(
    name = "jruby",
    artifact = "org.jruby:jruby-complete:9.1.17.0",
    sha1 = "76716d529710fc03d1d429b43e3cedd4419f78d4",
)

# When upgrading elasticsearch-rest-client, also upgrade httpcore-nio
# and httpasyncclient as necessary.
maven_jar(
    name = "elasticsearch-rest-client",
    artifact = "org.elasticsearch.client:elasticsearch-rest-client:7.4.1",
    sha1 = "b4e00ab47019103d69b6c9dcfdcbd3bfda00f86e",
)

maven_jar(
    name = "jackson-core",
    artifact = "com.fasterxml.jackson.core:jackson-core:2.10.0",
    sha1 = "4e2c5fa04648ec9772c63e2101c53af6504e624e",
)

TESTCONTAINERS_VERSION = "1.12.3"

maven_jar(
    name = "testcontainers",
    artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
    sha1 = "e424a4549640e120acceac641ac909fcda58bf62",
)

maven_jar(
    name = "testcontainers-elasticsearch",
    artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
    sha1 = "c0796de5032070b8768ce78c78949b48f13c30db",
)

maven_jar(
    name = "duct-tape",
    artifact = "org.rnorth.duct-tape:duct-tape:1.0.7",
    sha1 = "a26b5d90d88c91321dc7a3734ea72d2fc019ebb6",
)

maven_jar(
    name = "visible-assertions",
    artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.2",
    sha1 = "20d31a578030ec8e941888537267d3123c2ad1c1",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.2.0",
    sha1 = "ed8b772eb077a9cb50e44e90899c66a9a6c00e67",
)

load("//tools/bzl:js.bzl", "bower_archive", "npm_binary")

npm_binary(
    name = "bower",
)

npm_binary(
    name = "vulcanize",
    repository = GERRIT,
)

npm_binary(
    name = "crisper",
    repository = GERRIT,
)

# bower_archive() seed components.
bower_archive(
    name = "iron-autogrow-textarea",
    package = "polymerelements/iron-autogrow-textarea",
    sha1 = "68f0ece9b1e56ac26f8ce31d9938c504f6951bca",
    version = "2.1.0",
)

bower_archive(
    name = "es6-promise",
    package = "stefanpenner/es6-promise",
    sha1 = "a3a797bb22132f1ef75f9a2556173f81870c2e53",
    version = "3.3.0",
)

bower_archive(
    name = "fetch",
    package = "fetch",
    sha1 = "1b05a2bb40c73232c2909dc196de7519fe4db7a9",
    version = "1.0.0",
)

bower_archive(
    name = "iron-dropdown",
    package = "polymerelements/iron-dropdown",
    sha1 = "ac96fe31cdf203a63426fa75131b43c98c0597d3",
    version = "1.5.5",
)

bower_archive(
    name = "iron-input",
    package = "polymerelements/iron-input",
    sha1 = "9bc0c8e81de2527125383cbcf74dd9f27e7fa9ac",
    version = "1.0.10",
)

bower_archive(
    name = "iron-overlay-behavior",
    package = "polymerelements/iron-overlay-behavior",
    sha1 = "74cda9d7bf98e7a5e5004bc7ebdb6d208d49e11e",
    version = "2.0.0",
)

bower_archive(
    name = "iron-selector",
    package = "polymerelements/iron-selector",
    sha1 = "e0ee46c28523bf17730318c3b481a8ed4331c3b2",
    version = "2.0.0",
)

bower_archive(
    name = "paper-button",
    package = "polymerelements/paper-button",
    sha1 = "41a8fec68d93dad223ad2076d68515334b2c8d7b",
    version = "1.0.11",
)

bower_archive(
    name = "paper-input",
    package = "polymerelements/paper-input",
    sha1 = "6c934805e80ab201e143406edc73ea0ef35abf80",
    version = "1.1.18",
)

bower_archive(
    name = "iron-icon",
    package = "polymerelements/iron-icon",
    sha1 = "7da49a0d33cd56017740e0dbcf41d2b71532023f",
    version = "2.0.0",
)

bower_archive(
    name = "iron-iconset-svg",
    package = "polymerelements/iron-iconset-svg",
    sha1 = "4d0c406239cad2ff2975c6dd95fa189de0fe6b50",
    version = "2.1.0",
)

bower_archive(
    name = "moment",
    package = "moment/moment",
    sha1 = "fc8ce2c799bab21f6ced7aff928244f4ca8880aa",
    version = "2.13.0",
)

bower_archive(
    name = "page",
    package = "visionmedia/page.js",
    sha1 = "51a05428dd4f68fae1df5f12d0e2b61ba67f7757",
    version = "1.7.1",
)

bower_archive(
    name = "paper-item",
    package = "polymerelements/paper-item",
    sha1 = "803273ceb9ffebec8ecc9373ea638af4cd34af58",
    version = "1.1.4",
)

bower_archive(
    name = "paper-listbox",
    package = "polymerelements/paper-listbox",
    sha1 = "ccc1a90ab0a96878c7bf7c9c4cfe47c85b09c8e3",
    version = "2.0.0",
)

bower_archive(
    name = "polymer",
    package = "polymer/polymer",
    sha1 = "62ce80a5079c1b97f6c5c6ebf6b350e741b18b9c",
    version = "1.11.0",
)

bower_archive(
    name = "polymer-resin",
    package = "polymer/polymer-resin",
    sha1 = "94c29926c20ea3a9b636f26b3e0d689ead8137e5",
    version = "2.0.1",
)

bower_archive(
    name = "promise-polyfill",
    package = "polymerlabs/promise-polyfill",
    sha1 = "a3b598c06cbd7f441402e666ff748326030905d6",
    version = "1.0.0",
)

# bower test stuff

bower_archive(
    name = "iron-test-helpers",
    package = "polymerelements/iron-test-helpers",
    sha1 = "433b03b106f5ff32049b84150cd70938e18b67ac",
    version = "1.2.5",
)

bower_archive(
    name = "test-fixture",
    package = "polymerelements/test-fixture",
    sha1 = "e373bd21c069163c3a754e234d52c07c77b20d3c",
    version = "1.1.1",
)

bower_archive(
    name = "web-component-tester",
    package = "web-component-tester",
    sha1 = "4e778f8b7d784ba2a069d83d0cd146125c5c4fcb",
    version = "5.0.1",
)

# Bower component transitive dependencies.
load("//lib/js:bower_archives.bzl", "load_bower_archives")

load_bower_archives()

external_plugin_deps()
