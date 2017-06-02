workspace(name = "gerrit")

load("//tools/bzl:maven_jar.bzl", "maven_jar", "GERRIT", "MAVEN_LOCAL")
load("//lib/codemirror:cm.bzl", "CM_VERSION", "DIFF_MATCH_PATCH_VERSION")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "af1f5a31b8306faed9d09a38c8e2c1d6afc4c4a2dada3b5de11cceae8c7f4596",
    strip_prefix = "rules_closure-f68d4b5a55c04ee50a3196590dce1ca8e7dbf438",
    url = "https://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/f68d4b5a55c04ee50a3196590dce1ca8e7dbf438.tar.gz",  # 2017-05-05
)

# File is specific to Polymer and copied from the Closure Github -- should be
# synced any time there are major changes to Polymer.
# https://github.com/google/closure-compiler/blob/master/contrib/externs/polymer-1.0.js
http_file(
    name = "polymer_closure",
    sha256 = "5a589bdba674e1fec7188e9251c8624ebf2d4d969beb6635f9148f420d1e08b1",
    url = "https://raw.githubusercontent.com/google/closure-compiler/775609aad61e14aef289ebec4bfc09ad88877f9e/contrib/externs/polymer-1.0.js",
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

# Prevent redundant loading of dependencies.
closure_repositories(
    omit_aopalliance = True,
    omit_args4j = True,
    omit_javax_inject = True,
)

ANTLR_VERS = "3.5.2"

maven_jar(
    name = "java_runtime",
    artifact = "org.antlr:antlr-runtime:" + ANTLR_VERS,
    sha1 = "cd9cd41361c155f3af0f653009dcecb08d8b4afd",
)

maven_jar(
    name = "stringtemplate",
    artifact = "org.antlr:stringtemplate:4.0.2",
    sha1 = "e28e09e2d44d60506a7bcb004d6c23ff35c6ac08",
)

maven_jar(
    name = "org_antlr",
    artifact = "org.antlr:antlr:" + ANTLR_VERS,
    sha1 = "c4a65c950bfc3e7d04309c515b2177c00baf7764",
)

maven_jar(
    name = "antlr27",
    artifact = "antlr:antlr:2.7.7",
    attach_source = False,
    sha1 = "83cd2cd674a217ade95a4bb83a8a14f351f48bd0",
)

GUICE_VERS = "4.1.0"

maven_jar(
    name = "guice_library",
    artifact = "com.google.inject:guice:" + GUICE_VERS,
    sha1 = "eeb69005da379a10071aa4948c48d89250febb07",
)

maven_jar(
    name = "guice_assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERS,
    sha1 = "af799dd7e23e6fe8c988da12314582072b07edcb",
)

maven_jar(
    name = "guice_servlet",
    artifact = "com.google.inject.extensions:guice-servlet:" + GUICE_VERS,
    sha1 = "90ac2db772d9b85e2b05417b74f7464bcc061dcb",
)

maven_jar(
    name = "multibindings",
    artifact = "com.google.inject.extensions:guice-multibindings:" + GUICE_VERS,
    sha1 = "3b27257997ac51b0f8d19676f1ea170427e86d51",
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
    name = "servlet_api_3_1",
    artifact = "org.apache.tomcat:tomcat-servlet-api:8.0.24",
    sha1 = "5d9e2e895e3111622720157d0aa540066d5fce3a",
)

GWT_VERS = "2.8.1"

maven_jar(
    name = "user",
    artifact = "com.google.gwt:gwt-user:" + GWT_VERS,
    sha1 = "9a13fbee70848f1f1cddd3ae33ad180af3392d9e",
)

maven_jar(
    name = "dev",
    artifact = "com.google.gwt:gwt-dev:" + GWT_VERS,
    sha1 = "c7e88c07e9cda90cc623b4451d0d9713ae03aa53",
)

maven_jar(
    name = "javax_validation",
    artifact = "javax.validation:validation-api:1.0.0.GA",
    sha1 = "b6bd7f9d78f6fdaa3c37dae18a4bd298915f328e",
    src_sha1 = "7a561191db2203550fbfa40d534d4997624cd369",
)

maven_jar(
    name = "jsinterop_annotations",
    artifact = "com.google.jsinterop:jsinterop-annotations:1.0.0",
    sha1 = "23c3a3c060ffe4817e67673cc8294e154b0a4a95",
    src_sha1 = "5d7c478efbfccc191430d7c118d7bd2635e43750",
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
    name = "w3c_css_sac",
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
    name = "gwtorm_client",
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
    name = "joda_time",
    artifact = "joda-time:joda-time:2.9.9",
    sha1 = "f7b520c458572890807d143670c9b24f4de90897",
)

maven_jar(
    name = "joda_convert",
    artifact = "org.joda:joda-convert:1.8.1",
    sha1 = "675642ac208e0b741bc9118dcbcae44c271b992a",
)

load("//lib:guava.bzl", "GUAVA_VERSION", "GUAVA_BIN_SHA1")

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:" + GUAVA_VERSION,
    sha1 = GUAVA_BIN_SHA1,
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

SLF4J_VERS = "1.7.7"

maven_jar(
    name = "log_api",
    artifact = "org.slf4j:slf4j-api:" + SLF4J_VERS,
    sha1 = "2b8019b6249bb05d81d3a3094e468753e2b21311",
)

maven_jar(
    name = "log_nop",
    artifact = "org.slf4j:slf4j-nop:" + SLF4J_VERS,
    sha1 = "6cca9a3b999ff28b7a35ca762b3197cd7e4c2ad1",
)

maven_jar(
    name = "impl_log4j",
    artifact = "org.slf4j:slf4j-log4j12:" + SLF4J_VERS,
    sha1 = "58f588119ffd1702c77ccab6acb54bfb41bed8bd",
)

maven_jar(
    name = "jcl_over_slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
    sha1 = "56003dcd0a31deea6391b9e2ef2f2dc90b205a92",
)

maven_jar(
    name = "log4j",
    artifact = "log4j:log4j:1.2.17",
    sha1 = "5af35056b4d257e4b64b9e8069c0746e8b08629f",
)

maven_jar(
    name = "jsonevent_layout",
    artifact = "net.logstash.log4j:jsonevent-layout:1.7",
    sha1 = "507713504f0ddb75ba512f62763519c43cf46fde",
)

maven_jar(
    name = "json_smart",
    artifact = "net.minidev:json-smart:1.1.1",
    sha1 = "24a2f903d25e004de30ac602c5b47f2d4e420a59",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.0.26",
    sha1 = "01ebb18ebb3b379a74207d5af4ea7c8338ebd78b",
)

maven_jar(
    name = "commons_codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
)

maven_jar(
    name = "commons_collections",
    artifact = "commons-collections:commons-collections:3.2.2",
    sha1 = "8ad72fe39fa8c91eaaf12aadb21e0c3661fe26d5",
)

maven_jar(
    name = "commons_compress",
    artifact = "org.apache.commons:commons-compress:1.13",
    sha1 = "15c5e9584200122924e50203ae210b57616b75ee",
)

maven_jar(
    name = "commons_lang",
    artifact = "commons-lang:commons-lang:2.6",
    sha1 = "0ce1edb914c94ebc388f086c6827e8bdeec71ac2",
)

maven_jar(
    name = "commons_lang3",
    artifact = "org.apache.commons:commons-lang3:3.3.2",
    sha1 = "90a3822c38ec8c996e84c16a3477ef632cbc87a3",
)

maven_jar(
    name = "commons_dbcp",
    artifact = "commons-dbcp:commons-dbcp:1.4",
    sha1 = "30be73c965cc990b153a100aaaaafcf239f82d39",
)

maven_jar(
    name = "commons_pool",
    artifact = "commons-pool:commons-pool:1.5.5",
    sha1 = "7d8ffbdc47aa0c5a8afe5dc2aaf512f369f1d19b",
)

maven_jar(
    name = "commons_net",
    artifact = "commons-net:commons-net:3.5",
    sha1 = "342fc284019f590e1308056990fdb24a08f06318",
)

maven_jar(
    name = "commons_oro",
    artifact = "oro:oro:2.0.8",
    sha1 = "5592374f834645c4ae250f4c9fbb314c9369d698",
)

maven_jar(
    name = "commons_validator",
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
    name = "mime4j_core",
    artifact = "org.apache.james:apache-mime4j-core:" + MIME4J_VERS,
    sha1 = "d54f45fca44a2f210569656b4ca3574b42911c95",
)

maven_jar(
    name = "mime4j_dom",
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
    name = "ow2_asm",
    artifact = "org.ow2.asm:asm:" + OW2_VERS,
    sha1 = "5ef31c4fe953b1fd00b8a88fa1d6820e8785bb45",
)

maven_jar(
    name = "ow2_asm_analysis",
    artifact = "org.ow2.asm:asm-analysis:" + OW2_VERS,
    sha1 = "6d1bf8989fc7901f868bee3863c44f21aa63d110",
)

maven_jar(
    name = "ow2_asm_commons",
    artifact = "org.ow2.asm:asm-commons:" + OW2_VERS,
    sha1 = "25d8a575034dd9cfcb375a39b5334f0ba9c8474e",
)

maven_jar(
    name = "ow2_asm_tree",
    artifact = "org.ow2.asm:asm-tree:" + OW2_VERS,
    sha1 = "87b38c12a0ea645791ead9d3e74ae5268d1d6c34",
)

maven_jar(
    name = "ow2_asm_util",
    artifact = "org.ow2.asm:asm-util:" + OW2_VERS,
    sha1 = "b60e33a6bd0d71831e0c249816d01e6c1dd90a47",
)

maven_jar(
    name = "auto_value",
    artifact = "com.google.auto.value:auto-value:1.4.1",
    sha1 = "8172ebbd7970188aff304c8a420b9f17168f6f48",
)

maven_jar(
    name = "tukaani_xz",
    artifact = "org.tukaani:xz:1.4",
    sha1 = "18a9a2ce6abf32ea1b5fd31dae5210ad93f4e5e3",
)

LUCENE_VERS = "5.5.4"

maven_jar(
    name = "lucene_core",
    artifact = "org.apache.lucene:lucene-core:" + LUCENE_VERS,
    sha1 = "ab9c77e75cf142aa6e284b310c8395617bd9b19b",
)

maven_jar(
    name = "lucene_analyzers_common",
    artifact = "org.apache.lucene:lucene-analyzers-common:" + LUCENE_VERS,
    sha1 = "08ce9d34c8124c80e176e8332ee947480bbb9576",
)

maven_jar(
    name = "lucene_codecs",
    artifact = "org.apache.lucene:lucene-codecs:" + LUCENE_VERS,
    sha1 = "afdad570668469b1734fbd32b8f98561561bed48",
)

maven_jar(
    name = "backward_codecs",
    artifact = "org.apache.lucene:lucene-backward-codecs:" + LUCENE_VERS,
    sha1 = "a933f42e758c54c43083398127ea7342b54d8212",
)

maven_jar(
    name = "lucene_misc",
    artifact = "org.apache.lucene:lucene-misc:" + LUCENE_VERS,
    sha1 = "a74388857f73614e528ae44d742c60187cb55a5a",
)

maven_jar(
    name = "lucene_queryparser",
    artifact = "org.apache.lucene:lucene-queryparser:" + LUCENE_VERS,
    sha1 = "8a06fad4675473d98d93b61fea529e3f464bf69e",
)

maven_jar(
    name = "lucene_highlighter",
    artifact = "org.apache.lucene:lucene-highlighter:" + LUCENE_VERS,
    sha1 = "433f53f03f1b14337c08d54e507a5410905376fa",
)

maven_jar(
    name = "lucene_join",
    artifact = "org.apache.lucene:lucene-join:" + LUCENE_VERS,
    sha1 = "23f9a909a244ed3b28b37c5bb21a6e33e6c0a339",
)

maven_jar(
    name = "lucene_memory",
    artifact = "org.apache.lucene:lucene-memory:" + LUCENE_VERS,
    sha1 = "4dbdc2e1a24837722294762a9edb479f79092ab9",
)

maven_jar(
    name = "lucene_sandbox",
    artifact = "org.apache.lucene:lucene-sandbox:" + LUCENE_VERS,
    sha1 = "49498bbb2adc333e98bdca4bf6170ae770cbad11",
)

maven_jar(
    name = "lucene_spatial",
    artifact = "org.apache.lucene:lucene-spatial:" + LUCENE_VERS,
    sha1 = "0217d302dc0ef4d9b8b475ffe327d83c1e0ceba5",
)

maven_jar(
    name = "lucene_suggest",
    artifact = "org.apache.lucene:lucene-suggest:" + LUCENE_VERS,
    sha1 = "0f46dbb3229eed62dff10d008172c885e0e028c8",
)

maven_jar(
    name = "lucene_queries",
    artifact = "org.apache.lucene:lucene-queries:" + LUCENE_VERS,
    sha1 = "f915357b8b4b43742ab48f1401dedcaa12dfa37a",
)

maven_jar(
    name = "mime_util",
    artifact = "eu.medsea.mimeutil:mime-util:2.1.3",
    attach_source = False,
    sha1 = "0c9cfae15c74f62491d4f28def0dff1dabe52a47",
)

PROLOG_VERS = "1.4.3"

PROLOG_REPO = GERRIT

maven_jar(
    name = "prolog_runtime",
    artifact = "com.googlecode.prolog-cafe:prolog-runtime:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "d5206556cbc76ffeab21313ffc47b586a1efbcbb",
)

maven_jar(
    name = "prolog_compiler",
    artifact = "com.googlecode.prolog-cafe:prolog-compiler:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "f37032cf1dec3e064427745bc59da5a12757a3b2",
)

maven_jar(
    name = "prolog_io",
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
    name = "guava_retrying",
    artifact = "com.github.rholder:guava-retrying:2.0.0",
    sha1 = "974bc0a04a11cc4806f7c20a34703bd23c34e7f4",
)

maven_jar(
    name = "jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.1",
    sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
)

maven_jar(
    name = "blame_cache",
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
    name = "html_types",
    artifact = "com.google.common.html.types:types:1.0.4",
    sha1 = "2adf4c8bfccc0ff7346f9186ac5aa57d829ad065",
)

maven_jar(
    name = "icu4j",
    artifact = "com.ibm.icu:icu4j:57.1",
    sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
)

maven_jar(
    name = "dropwizard_core",
    artifact = "io.dropwizard.metrics:metrics-core:3.2.2",
    sha1 = "cd9886f498ee2ab2d994f0c779e5553b2c450416",
)

BC_VERS = "1.56"

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BC_VERS,
    sha1 = "a153c6f9744a3e9dd6feab5e210e1c9861362ec7",
)

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BC_VERS,
    sha1 = "9c3f2e7072c8cc1152079b5c25291a9f462631f1",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BC_VERS,
    sha1 = "4648af70268b6fdb24674fb1fd7c1fcc73db1231",
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
    name = "mina_core",
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
    name = "fluent_hc",
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

maven_jar(
    name = "httpmime",
    artifact = "org.apache.httpcomponents:httpmime:" + HTTPCOMP_VERS,
    sha1 = "2f8757f5ac5e38f46c794e5229d1f3c522e9b1df",
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
    name = "hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

# Only needed when jgit is built from the development tree
maven_jar(
    name = "hamcrest_library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
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

# When bumping the easymock version number, make sure to also move powermock to a compatible version
maven_jar(
    name = "easymock",
    artifact = "org.easymock:easymock:3.1",
    sha1 = "3e127311a86fc2e8f550ef8ee4abe094bbcf7e7e",
)

maven_jar(
    name = "cglib_3_2",
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
    name = "powermock_module_junit4",
    artifact = "org.powermock:powermock-module-junit4:" + POWERM_VERS,
    sha1 = "ea8530b2848542624f110a393513af397b37b9cf",
)

maven_jar(
    name = "powermock_module_junit4_common",
    artifact = "org.powermock:powermock-module-junit4-common:" + POWERM_VERS,
    sha1 = "7222ced54dabc310895d02e45c5428ca05193cda",
)

maven_jar(
    name = "powermock_reflect",
    artifact = "org.powermock:powermock-reflect:" + POWERM_VERS,
    sha1 = "97d25eda8275c11161bcddda6ef8beabd534c878",
)

maven_jar(
    name = "powermock_api_easymock",
    artifact = "org.powermock:powermock-api-easymock:" + POWERM_VERS,
    sha1 = "aa740ecf89a2f64d410b3d93ef8cd6833009ef00",
)

maven_jar(
    name = "powermock_api_support",
    artifact = "org.powermock:powermock-api-support:" + POWERM_VERS,
    sha1 = "592ee6d929c324109d3469501222e0c76ccf0869",
)

maven_jar(
    name = "powermock_core",
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
    artifact = "org.apache.derby:derby:10.11.1.1",
    attach_source = False,
    sha1 = "df4b50061e8e4c348ce243b921f53ee63ba9bbe1",
)

JETTY_VERS = "9.3.18.v20170406"

maven_jar(
    name = "jetty_servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
    sha1 = "534e7fa0e4fb6e08f89eb3f6a8c48b4f81ff5738",
)

maven_jar(
    name = "jetty_security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
    sha1 = "16b900e91b04511f42b706c925c8af6023d2c05e",
)

maven_jar(
    name = "jetty_servlets",
    artifact = "org.eclipse.jetty:jetty-servlets:" + JETTY_VERS,
    sha1 = "f9311d1d8e6124d2792f4db5b29514d0ecf46812",
)

maven_jar(
    name = "jetty_server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
    sha1 = "0a32feea88cba2d43951d22b60861c643454bb3f",
)

maven_jar(
    name = "jetty_jmx",
    artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
    sha1 = "f988136dc5aa634afed6c5a35d910ee9599c6c23",
)

maven_jar(
    name = "jetty_continuation",
    artifact = "org.eclipse.jetty:jetty-continuation:" + JETTY_VERS,
    sha1 = "3c5d89c8204d4a48a360087f95e4cbd4520b5de0",
)

maven_jar(
    name = "jetty_http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
    sha1 = "30ece6d732d276442d513b94d914de6fa1075fae",
)

maven_jar(
    name = "jetty_io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
    sha1 = "36cb411ee89be1b527b0c10747aa3153267fc3ec",
)

maven_jar(
    name = "jetty_util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
    sha1 = "8600b7d028a38cb462eff338de91390b3ff5040e",
)

maven_jar(
    name = "openid_consumer",
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
    artifact = "org.postgresql:postgresql:9.4.1211",
    sha1 = "721e3017fab68db9f0b08537ec91b8d757973ca8",
)

maven_jar(
    name = "codemirror_minified",
    artifact = "org.webjars.npm:codemirror-minified:" + CM_VERSION,
    sha1 = "a62e6dc43acc09df962edeb8013c987da7739e7a",
)

maven_jar(
    name = "codemirror_original",
    artifact = "org.webjars.npm:codemirror:" + CM_VERSION,
    sha1 = "7a5ae457aa3bc0023d21bc75793bac86b83b0c51",
)

maven_jar(
    name = "diff_match_patch",
    artifact = "org.webjars:google-diff-match-patch:" + DIFF_MATCH_PATCH_VERSION,
    attach_source = False,
    sha1 = "0cf1782dbcb8359d95070da9176059a5a9d37709",
)

maven_jar(
    name = "commons_io",
    artifact = "commons-io:commons-io:1.4",
    sha1 = "a8762d07e76cfde2395257a5da47ba7c1dbd3dce",
)

maven_jar(
    name = "asciidoctor",
    artifact = "org.asciidoctor:asciidoctorj:1.5.4.1",
    sha1 = "f7ddfb2bbed2f8da3f9ad0d1a5514f04b4274a5a",
)

maven_jar(
    name = "jruby",
    artifact = "org.jruby:jruby-complete:9.1.5.0",
    sha1 = "00d0003e99da3c4d830b12c099691ce910c84e39",
)

maven_jar(
    name = "elasticsearch",
    artifact = "org.elasticsearch:elasticsearch:2.4.5",
    sha1 = "daafe48ae06592029a2fedca1fe2ac0f5eec3185",
)

# Java REST client for Elasticsearch.
JEST_VERSION = "2.4.0"

maven_jar(
    name = "jest_common",
    artifact = "io.searchbox:jest-common:" + JEST_VERSION,
    sha1 = "ea779ebe7c438a53dce431f85b0d4e1d8faee2ac",
)

maven_jar(
    name = "jest",
    artifact = "io.searchbox:jest:" + JEST_VERSION,
    sha1 = "e2a604a584e6633545ac6b1fe99ef888ab96dae9",
)

maven_jar(
    name = "compress_lzf",
    artifact = "com.ning:compress-lzf:1.0.2",
    sha1 = "62896e6fca184c79cc01a14d143f3ae2b4f4b4ae",
)

maven_jar(
    name = "hppc",
    artifact = "com.carrotsearch:hppc:0.7.1",
    sha1 = "8b5057f74ea378c0150a1860874a3ebdcb713767",
)

maven_jar(
    name = "jsr166e",
    artifact = "com.twitter:jsr166e:1.1.0",
    sha1 = "233098147123ee5ddcd39ffc57ff648be4b7e5b2",
)

maven_jar(
    name = "netty",
    artifact = "io.netty:netty:3.10.0.Final",
    sha1 = "ad61cd1bba067e6634ddd3e160edf0727391ac30",
)

maven_jar(
    name = "t_digest",
    artifact = "com.tdunning:t-digest:3.0",
    sha1 = "84ccf145ac2215e6bfa63baa3101c0af41017cfc",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:4.1.0",
    sha1 = "1c12d070e602efd8021891cdd7fd18bc129372d4",
)

JACKSON_VERSION = "2.6.6"

maven_jar(
    name = "jackson_core",
    artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERSION,
    sha1 = "02eb801df67aacaf5b1deb4ac626e1964508e47b",
)

maven_jar(
    name = "jackson_dataformat_smile",
    artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-smile:" + JACKSON_VERSION,
    sha1 = "ccbfc948748ed2754a58c1af9e0a02b5cc1aed69",
)

maven_jar(
    name = "jackson_dataformat_cbor",
    artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:" + JACKSON_VERSION,
    sha1 = "34c7b7ff495fc6b049612bdc9db0900a68e112f8",
)

maven_jar(
    name = "httpasyncclient",
    artifact = "org.apache.httpcomponents:httpasyncclient:4.1.2",
    sha1 = "95aa3e6fb520191a0970a73cf09f62948ee614be",
)

maven_jar(
    name = "httpcore_nio",
    artifact = "org.apache.httpcomponents:httpcore-nio:" + HTTPCOMP_VERS,
    sha1 = "a8c5e3c3bfea5ce23fb647c335897e415eb442e3",
)

maven_jar(
    name = "httpcore_niossl",
    artifact = "org.apache.httpcomponents:httpcore-niossl:4.0-alpha6",
    attach_source = False,
    sha1 = "9c662e7247ca8ceb1de5de629f685c9ef3e4ab58",
)

load("//tools/bzl:js.bzl", "npm_binary", "bower_archive")

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
    sha1 = "2c7dd638d55ea91242525139cba18a308b9426d5",
    version = "1.9.1",
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
