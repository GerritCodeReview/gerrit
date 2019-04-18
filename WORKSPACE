workspace(name = "gerrit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//lib/codemirror:cm.bzl", "CM_VERSION", "DIFF_MATCH_PATCH_VERSION")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")

http_archive(
    name = "bazel_skylib",
    sha256 = "bbccf674aa441c266df9894182d80de104cabd19be98be002f6d478aaa31574d",
    strip_prefix = "bazel-skylib-2169ae1c374aab4a09aa90e65efe1a3aad4e279b",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/2169ae1c374aab4a09aa90e65efe1a3aad4e279b.tar.gz"],
)

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "34abd9170fdbfdfc6f3b63f2c18cee3cbcb2ddbd5e3c97324add0aa7809ed875",
    strip_prefix = "rules_closure-9d543facf886631e4ed379996e60ce3533188adc",
    urls = ["https://github.com/bazelbuild/rules_closure/archive/9d543facf886631e4ed379996e60ce3533188adc.tar.gz"],
)

# Transitive dependency of rules_closure and protobuf
http_archive(
    name = "net_zlib",
    build_file = "//:lib/zlib/BUILD",
    sha256 = "c3e5e9fdd5004dcb542feda5ee4f0ff0744628baf8ed2dd5d66f8ca1197cb1a1",
    strip_prefix = "zlib-1.2.11",
    urls = ["https://zlib.net/zlib-1.2.11.tar.gz"],
)

bind(
    name = "zlib",
    actual = "@net_zlib//:zlib",
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

load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "0.22.0")

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

# Prevent redundant loading of dependencies.
closure_repositories(
    omit_aopalliance = True,
    omit_args4j = True,
    omit_javax_inject = True,
)

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
    name = "protobuf",
    artifact = "com.google.protobuf:protobuf-java:3.0.0-beta-2",
    sha1 = "de80fe047052445869b96f6def6baca7182c95af",
)

maven_jar(
    name = "joda-time",
    artifact = "joda-time:joda-time:2.9.4",
    sha1 = "1c295b462f16702ebe720bbb08f62e1ba80da41b",
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
    artifact = "com.googlecode.juniversalchardet:juniversalchardet:1.0.3",
    sha1 = "cd49678784c46aa8789c060538e0154013bb421b",
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
    artifact = "commons-codec:commons-codec:1.4",
    sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
)

maven_jar(
    name = "commons-collections",
    artifact = "commons-collections:commons-collections:3.2.2",
    sha1 = "8ad72fe39fa8c91eaaf12aadb21e0c3661fe26d5",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.12",
    sha1 = "84caa68576e345eb5e7ae61a0e5a9229eb100d7b",
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
    artifact = "commons-validator:commons-validator:1.5.1",
    sha1 = "86d05a46e8f064b300657f751b5a98c62807e2a0",
)

maven_jar(
    name = "automaton",
    artifact = "dk.brics.automaton:automaton:1.11-8",
    sha1 = "6ebfa65eb431ff4b715a23be7a750cbc4cc96d0f",
)

maven_jar(
    name = "pegdown",
    artifact = "org.pegdown:pegdown:1.4.2",
    sha1 = "d96db502ed832df867ff5d918f05b51ba3879ea7",
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
    artifact = "com/google/gitiles:blame-cache:0.2-1",
    attach_source = False,
    repository = GERRIT,
    sha1 = "da7977e8b140b63f18054214c1d1b86ffa6896cb",
)

# Keep this version of Soy synchronized with the version used in Gitiles.
maven_jar(
    name = "soy",
    artifact = "com.google.template:soy:2017-02-01",
    sha1 = "8638940b207779fe3b75e55b6e65abbefb6af678",
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
    artifact = "io.dropwizard.metrics:metrics-core:4.0.3",
    sha1 = "bb562ee73f740bb6b2bf7955f97be6b870d9e9f0",
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
    artifact = "org.apache.sshd:sshd-core:1.4.0",
    exclude = ["META-INF/services/java.nio.file.spi.FileSystemProvider"],
    sha1 = "c8f3d7457fc9979d1b9ec319f0229b89793c8e56",
)

maven_jar(
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.1.0",
    sha1 = "8f5a3b165164e222da048d8136b21428ee0b9122",
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
    artifact = "org.apache.httpcomponents:httpasyncclient:4.1.2",
    sha1 = "95aa3e6fb520191a0970a73cf09f62948ee614be",
)

# elasticsearch-rest-client explicitly depends on this version
maven_jar(
    name = "httpcore-nio",
    artifact = "org.apache.httpcomponents:httpcore-nio:4.4.5",
    sha1 = "f4be009e7505f6ceddf21e7960c759f413f15056",
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

TRUTH_VERS = "0.32"

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:" + TRUTH_VERS,
    sha1 = "e996fb4b41dad04365112786796c945f909cfdf7",
)

maven_jar(
    name = "truth-java8-extension",
    artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
    sha1 = "2862787ce34cb6f385ada891e36ec7f9e7bd0902",
)

maven_jar(
    name = "easymock",
    artifact = "org.easymock:easymock:3.1",  # When bumping the version
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
    artifact = "org.elasticsearch.client:elasticsearch-rest-client:6.4.3",
    sha1 = "5c24325430971ba2fa4769eb446f026b7680d5e7",
)

JACKSON_VERSION = "2.9.8"

maven_jar(
    name = "jackson-core",
    artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERSION,
    sha1 = "0f5a654e4675769c716e5b387830d19b501ca191",
)

TESTCONTAINERS_VERSION = "1.11.2"

maven_jar(
    name = "testcontainers",
    artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
    sha1 = "eae47ed24bb07270d4b60b5e2c3444c5bf3c8ea9",
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
    sha1 = "b9b6874c9a2b5be435557a827ff8bd6661672ee3",
    version = "1.0.12",
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
    sha1 = "63e3d669a09edaa31c4f05afc76b53b919ef0595",
    version = "1.4.0",
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
    sha1 = "83181085fda59446ce74fd0d5ca30c223f38ee4a",
    version = "1.7.6",
)

bower_archive(
    name = "iron-selector",
    package = "polymerelements/iron-selector",
    sha1 = "c57235dfda7fbb987c20ad0e97aac70babf1a1bf",
    version = "1.5.2",
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
    name = "polymer",
    package = "polymer/polymer",
    sha1 = "62ce80a5079c1b97f6c5c6ebf6b350e741b18b9c",
    version = "1.11.0",
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
    sha1 = "a4a9bc7815a22d143e8f8593e37b3c2028b8c20f",
    version = "5.0.0",
)

# Bower component transitive dependencies.
load("//lib/js:bower_archives.bzl", "load_bower_archives")

load_bower_archives()

external_plugin_deps()
