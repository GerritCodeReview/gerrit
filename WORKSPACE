workspace(name = "gerrit")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
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
    sha256 = "4d63a36dcca040475bd6deb815b9a600bd686e1413ac1ebd4b04516edd675020",
    urls = ["https://raw.githubusercontent.com/google/closure-compiler/35d2b3340ff23a69441f10fa3bc820691c2942f2/contrib/externs/polymer-1.0.js"],
)

load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "0.22.0")

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

# Prevent redundant loading of dependencies.
# TODO(davido): Omit re-fetching ancient args4j version when these PRs are merged:
# https://github.com/bazelbuild/rules_closure/pull/262
# https://github.com/google/closure-templates/pull/155
closure_repositories(
    omit_aopalliance = True,
    omit_javax_inject = True,
)

# Golang support for PolyGerrit local dev server.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "6776d68ebb897625dead17ae510eac3d5f6342367327875210df44dbe2aeeb19",
    url = "https://github.com/bazelbuild/rules_go/releases/download/0.17.1/rules_go-0.17.1.tar.gz",
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains()

http_archive(
    name = "bazel_gazelle",
    sha256 = "3c681998538231a2d24d0c07ed5a7658cb72bfb5fd4bf9911157c0e9ac6a2687",
    urls = ["https://github.com/bazelbuild/bazel-gazelle/releases/download/0.17.0/bazel-gazelle-0.17.0.tar.gz"],
)

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies", "go_repository")

gazelle_dependencies()

# Dependencies for PolyGerrit local dev server.
go_repository(
    name = "com_github_howeyc_fsnotify",
    commit = "441bbc86b167f3c1f4786afae9931403b99fdacf",
    importpath = "github.com/howeyc/fsnotify",
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

GUICE_VERS = "4.2.2"

maven_jar(
    name = "guice-library",
    artifact = "com.google.inject:guice:" + GUICE_VERS,
    sha1 = "6dacbe18e5eaa7f6c9c36db33b42e7985e94ce77",
)

maven_jar(
    name = "guice-assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERS,
    sha1 = "c33fb10080d58446f752b4fcfff8a5fabb80a449",
)

maven_jar(
    name = "guice-servlet",
    artifact = "com.google.inject.extensions:guice-servlet:" + GUICE_VERS,
    sha1 = "0d0054bdd812224078357a9b11409e43d182a046",
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
    artifact = "org.apache.tomcat:tomcat-servlet-api:8.5.23",
    sha1 = "021a212688ec94fe77aff74ab34cc74f6f940e60",
)

load("//lib/jgit:jgit.bzl", "jgit_repos")

jgit_repos()

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    attach_source = False,
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

FLOGGER_VERS = "0.4"

maven_jar(
    name = "flogger",
    artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
    sha1 = "9c8863dcc913b56291c0c88e6d4ca9715b43df98",
)

maven_jar(
    name = "flogger-log4j-backend",
    artifact = "com.google.flogger:flogger-log4j-backend:" + FLOGGER_VERS,
    sha1 = "17aa5e31daa1354187e14b6978597d630391c028",
)

maven_jar(
    name = "flogger-system-backend",
    artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
    sha1 = "287b569d76abcd82f9de87fe41829fbc7ebd8ac9",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.5",
    sha1 = "f645ed69d595b24d4cf8b3fbb64cc505bede8829",
)

maven_jar(
    name = "protobuf",
    artifact = "com.google.protobuf:protobuf-java:3.7.1",
    sha1 = "0bce1b6dc9e4531169542ab37a1c8641bcaa8afb",
)

load("//lib:guava.bzl", "GUAVA_BIN_SHA1", "GUAVA_VERSION")

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:" + GUAVA_VERSION,
    sha1 = GUAVA_BIN_SHA1,
)

maven_jar(
    name = "guava-failureaccess",
    artifact = "com.google.guava:failureaccess:1.0.1",
    sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
)

maven_jar(
    name = "j2objc",
    artifact = "com.google.j2objc:j2objc-annotations:1.1",
    sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
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
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:" + SLF4J_VERS,
    sha1 = "2b8019b6249bb05d81d3a3094e468753e2b21311",
)

maven_jar(
    name = "log-ext",
    artifact = "org.slf4j:slf4j-ext:" + SLF4J_VERS,
    sha1 = "09a8f58c784c37525d2624062414358acf296717",
)

maven_jar(
    name = "impl-log4j",
    artifact = "org.slf4j:slf4j-log4j12:" + SLF4J_VERS,
    sha1 = "58f588119ffd1702c77ccab6acb54bfb41bed8bd",
)

maven_jar(
    name = "jcl-over-slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
    sha1 = "56003dcd0a31deea6391b9e2ef2f2dc90b205a92",
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
    name = "args4j-intern",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
)

# When upgrading commons-compress, also upgrade tukaani-xz
maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.15",
    sha1 = "b686cd04abaef1ea7bc5e143c080563668eec17e",
)

maven_jar(
    name = "commons-lang",
    artifact = "commons-lang:commons-lang:2.6",
    sha1 = "0ce1edb914c94ebc388f086c6827e8bdeec71ac2",
)

maven_jar(
    name = "commons-lang3",
    artifact = "org.apache.commons:commons-lang3:3.8.1",
    sha1 = "6505a72a097d9270f7a9e7bf42c4238283247755",
)

maven_jar(
    name = "commons-text",
    artifact = "org.apache.commons:commons-text:1.2",
    sha1 = "74acdec7237f576c4803fff0c1008ab8a3808b2b",
)

maven_jar(
    name = "commons-dbcp",
    artifact = "commons-dbcp:commons-dbcp:1.4",
    sha1 = "30be73c965cc990b153a100aaaaafcf239f82d39",
)

# Transitive dependency of commons-dbcp, do not update without
# also updating commons-dbcp
maven_jar(
    name = "commons-pool",
    artifact = "commons-pool:commons-pool:1.5.5",
    sha1 = "7d8ffbdc47aa0c5a8afe5dc2aaf512f369f1d19b",
)

maven_jar(
    name = "commons-net",
    artifact = "commons-net:commons-net:3.6",
    sha1 = "b71de00508dcb078d2b24b5fa7e538636de9b3da",
)

maven_jar(
    name = "commons-validator",
    artifact = "commons-validator:commons-validator:1.6",
    sha1 = "e989d1e87cdd60575df0765ed5bac65c905d7908",
)

maven_jar(
    name = "automaton",
    artifact = "dk.brics:automaton:1.12-1",
    sha1 = "959a0c62f9a5c2309e0ad0b0589c74d69e101241",
)

COMMONMARK_VERS = "0.10.0"

# commonmark must match the version used in Gitiles
maven_jar(
    name = "commonmark",
    artifact = "com.atlassian.commonmark:commonmark:" + COMMONMARK_VERS,
    sha1 = "119cb7bedc3570d9ecb64ec69ab7686b5c20559b",
)

maven_jar(
    name = "cm-autolink",
    artifact = "com.atlassian.commonmark:commonmark-ext-autolink:" + COMMONMARK_VERS,
    sha1 = "a6056a5efbd68f57d420bc51bbc54b28a5d3c56b",
)

maven_jar(
    name = "gfm-strikethrough",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:" + COMMONMARK_VERS,
    sha1 = "40837da951b421b545edddac57012e15fcc9e63c",
)

maven_jar(
    name = "gfm-tables",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-tables:" + COMMONMARK_VERS,
    sha1 = "c075db2a3301100cf70c7dced8ecf86b494458a2",
)

FLEXMARK_VERS = "0.34.18"

maven_jar(
    name = "flexmark",
    artifact = "com.vladsch.flexmark:flexmark:" + FLEXMARK_VERS,
    sha1 = "65cc1489ef8902023140900a3a7fcce89fba678d",
)

maven_jar(
    name = "flexmark-ext-abbreviation",
    artifact = "com.vladsch.flexmark:flexmark-ext-abbreviation:" + FLEXMARK_VERS,
    sha1 = "a0384932801e51f16499358dec69a730739aca3f",
)

maven_jar(
    name = "flexmark-ext-anchorlink",
    artifact = "com.vladsch.flexmark:flexmark-ext-anchorlink:" + FLEXMARK_VERS,
    sha1 = "6df2e23b5c94a5e46b1956a29179eb783f84ea2f",
)

maven_jar(
    name = "flexmark-ext-autolink",
    artifact = "com.vladsch.flexmark:flexmark-ext-autolink:" + FLEXMARK_VERS,
    sha1 = "069f8ff15e5b435cc96b23f31798ce64a7a3f6d3",
)

maven_jar(
    name = "flexmark-ext-definition",
    artifact = "com.vladsch.flexmark:flexmark-ext-definition:" + FLEXMARK_VERS,
    sha1 = "ff177d8970810c05549171e3ce189e2c68b906c0",
)

maven_jar(
    name = "flexmark-ext-emoji",
    artifact = "com.vladsch.flexmark:flexmark-ext-emoji:" + FLEXMARK_VERS,
    sha1 = "410bf7d8e5b8bc2c4a8cff644d1b2bc7b271a41e",
)

maven_jar(
    name = "flexmark-ext-escaped-character",
    artifact = "com.vladsch.flexmark:flexmark-ext-escaped-character:" + FLEXMARK_VERS,
    sha1 = "6f4fb89311b54284a6175341d4a5e280f13b2179",
)

maven_jar(
    name = "flexmark-ext-footnotes",
    artifact = "com.vladsch.flexmark:flexmark-ext-footnotes:" + FLEXMARK_VERS,
    sha1 = "35efe7d9aea97b6f36e09c65f748863d14e1cfe4",
)

maven_jar(
    name = "flexmark-ext-gfm-issues",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-issues:" + FLEXMARK_VERS,
    sha1 = "ec1d660102f6a1d0fbe5e57c13b7ff8bae6cff72",
)

maven_jar(
    name = "flexmark-ext-gfm-strikethrough",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:" + FLEXMARK_VERS,
    sha1 = "6060442b742c9b6d4d83d7dd4f0fe477c4686dd2",
)

maven_jar(
    name = "flexmark-ext-gfm-tables",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tables:" + FLEXMARK_VERS,
    sha1 = "2fe597849e46e02e0c1ea1d472848f74ff261282",
)

maven_jar(
    name = "flexmark-ext-gfm-tasklist",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tasklist:" + FLEXMARK_VERS,
    sha1 = "b3af19ce4efdc980a066c1bf0f5a6cf8c24c487a",
)

maven_jar(
    name = "flexmark-ext-gfm-users",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-users:" + FLEXMARK_VERS,
    sha1 = "7456c5f7272c195ee953a02ebab4f58374fb23ee",
)

maven_jar(
    name = "flexmark-ext-ins",
    artifact = "com.vladsch.flexmark:flexmark-ext-ins:" + FLEXMARK_VERS,
    sha1 = "13fe1a95a8f3be30b574451cfe8d3d5936fa3e94",
)

maven_jar(
    name = "flexmark-ext-jekyll-front-matter",
    artifact = "com.vladsch.flexmark:flexmark-ext-jekyll-front-matter:" + FLEXMARK_VERS,
    sha1 = "e146e2bf3a740d6ef06a33a516c4d1f6d3761109",
)

maven_jar(
    name = "flexmark-ext-superscript",
    artifact = "com.vladsch.flexmark:flexmark-ext-superscript:" + FLEXMARK_VERS,
    sha1 = "02541211e8e4a6c89ce0a68b07b656d8a19ac282",
)

maven_jar(
    name = "flexmark-ext-tables",
    artifact = "com.vladsch.flexmark:flexmark-ext-tables:" + FLEXMARK_VERS,
    sha1 = "775d9587de71fd50573f32eee98ab039b4dcc219",
)

maven_jar(
    name = "flexmark-ext-toc",
    artifact = "com.vladsch.flexmark:flexmark-ext-toc:" + FLEXMARK_VERS,
    sha1 = "85b75fe1ebe24c92b9d137bcbc51d232845b6077",
)

maven_jar(
    name = "flexmark-ext-typographic",
    artifact = "com.vladsch.flexmark:flexmark-ext-typographic:" + FLEXMARK_VERS,
    sha1 = "c1bf0539de37d83aa05954b442f929e204cd89db",
)

maven_jar(
    name = "flexmark-ext-wikilink",
    artifact = "com.vladsch.flexmark:flexmark-ext-wikilink:" + FLEXMARK_VERS,
    sha1 = "400b23b9a4e0c008af0d779f909ee357628be39d",
)

maven_jar(
    name = "flexmark-ext-yaml-front-matter",
    artifact = "com.vladsch.flexmark:flexmark-ext-yaml-front-matter:" + FLEXMARK_VERS,
    sha1 = "491f815285a8e16db1e906f3789a94a8a9836fa6",
)

maven_jar(
    name = "flexmark-formatter",
    artifact = "com.vladsch.flexmark:flexmark-formatter:" + FLEXMARK_VERS,
    sha1 = "d46308006800d243727100ca0f17e6837070fd48",
)

maven_jar(
    name = "flexmark-html-parser",
    artifact = "com.vladsch.flexmark:flexmark-html-parser:" + FLEXMARK_VERS,
    sha1 = "fece2e646d11b6a77fc611b4bd3eb1fb8a635c87",
)

maven_jar(
    name = "flexmark-profile-pegdown",
    artifact = "com.vladsch.flexmark:flexmark-profile-pegdown:" + FLEXMARK_VERS,
    sha1 = "297f723bb51286eaa7029558fac87d819643d577",
)

maven_jar(
    name = "flexmark-util",
    artifact = "com.vladsch.flexmark:flexmark-util:" + FLEXMARK_VERS,
    sha1 = "31e2e1fbe8273d7c913506eafeb06b1a7badb062",
)

# Transitive dependency of flexmark and gitiles
maven_jar(
    name = "autolink",
    artifact = "org.nibor.autolink:autolink:0.7.0",
    sha1 = "649f9f13422cf50c926febe6035662ae25dc89b2",
)

GREENMAIL_VERS = "1.5.5"

maven_jar(
    name = "greenmail",
    artifact = "com.icegreen:greenmail:" + GREENMAIL_VERS,
    sha1 = "9ea96384ad2cb8118c22f493b529eb72c212691c",
)

MAIL_VERS = "1.6.0"

maven_jar(
    name = "mail",
    artifact = "com.sun.mail:javax.mail:" + MAIL_VERS,
    sha1 = "a055c648842c4954c1f7db7254f45d9ad565e278",
)

MIME4J_VERS = "0.8.1"

maven_jar(
    name = "mime4j-core",
    artifact = "org.apache.james:apache-mime4j-core:" + MIME4J_VERS,
    sha1 = "c62dfe18a3b827a2c626ade0ffba44562ddf3f61",
)

maven_jar(
    name = "mime4j-dom",
    artifact = "org.apache.james:apache-mime4j-dom:" + MIME4J_VERS,
    sha1 = "f2d653c617004193f3350330d907f77b60c88c56",
)

maven_jar(
    name = "jsoup",
    artifact = "org.jsoup:jsoup:1.9.2",
    sha1 = "5e3bda828a80c7a21dfbe2308d1755759c2fd7b4",
)

OW2_VERS = "7.0"

maven_jar(
    name = "ow2-asm",
    artifact = "org.ow2.asm:asm:" + OW2_VERS,
    sha1 = "d74d4ba0dee443f68fb2dcb7fcdb945a2cd89912",
)

maven_jar(
    name = "ow2-asm-analysis",
    artifact = "org.ow2.asm:asm-analysis:" + OW2_VERS,
    sha1 = "4b310d20d6f1c6b7197a75f1b5d69f169bc8ac1f",
)

maven_jar(
    name = "ow2-asm-commons",
    artifact = "org.ow2.asm:asm-commons:" + OW2_VERS,
    sha1 = "478006d07b7c561ae3a92ddc1829bca81ae0cdd1",
)

maven_jar(
    name = "ow2-asm-tree",
    artifact = "org.ow2.asm:asm-tree:" + OW2_VERS,
    sha1 = "29bc62dcb85573af6e62e5b2d735ef65966c4180",
)

maven_jar(
    name = "ow2-asm-util",
    artifact = "org.ow2.asm:asm-util:" + OW2_VERS,
    sha1 = "18d4d07010c24405129a6dbb0e92057f8779fb9d",
)

AUTO_VALUE_VERSION = "1.6.5"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "816872c85048f36a67a276ef7a49cc2e4595711c",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "c3dad10377f0e2242c9a4b88e9704eaf79103679",
)

# Transitive dependency of commons-compress
maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.6",
    sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
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

maven_jar(
    name = "mime-util",
    artifact = "eu.medsea.mimeutil:mime-util:2.1.3",
    attach_source = False,
    sha1 = "0c9cfae15c74f62491d4f28def0dff1dabe52a47",
)

PROLOG_VERS = "1.4.4"

PROLOG_REPO = GERRIT

maven_jar(
    name = "prolog-runtime",
    artifact = "com.googlecode.prolog-cafe:prolog-runtime:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "e9a364f4233481cce63239e8e68a6190c8f58acd",
)

maven_jar(
    name = "prolog-compiler",
    artifact = "com.googlecode.prolog-cafe:prolog-compiler:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "570295026f6aa7b905e423d107cb2e081eecdc04",
)

maven_jar(
    name = "prolog-io",
    artifact = "com.googlecode.prolog-cafe:prolog-io:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "1f25c4e27d22bdbc31481ee0c962a2a2853e4428",
)

maven_jar(
    name = "cafeteria",
    artifact = "com.googlecode.prolog-cafe:prolog-cafeteria:" + PROLOG_VERS,
    attach_source = False,
    repository = PROLOG_REPO,
    sha1 = "0e6c2deeaf5054815a561cbd663566fd59b56c6c",
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

GITILES_VERS = "0.2-7"

maven_jar(
    name = "blame-cache",
    artifact = "com.google.gitiles:blame-cache:" + GITILES_VERS,
    attach_source = False,
    repository = GERRIT,
    sha1 = "8170f33b8b1db6f55e41d7069fa050a4d102a62b",
)

maven_jar(
    name = "gitiles-servlet",
    artifact = "com.google.gitiles:gitiles-servlet:" + GITILES_VERS,
    repository = GERRIT,
    sha1 = "f23b22cb27fe5c4a78f761492082159d17873f57",
)

# prettify must match the version used in Gitiles
maven_jar(
    name = "prettify",
    artifact = "com.github.twalcari:java-prettify:1.2.2",
    sha1 = "b8ba1c1eb8b2e45cfd465d01218c6060e887572e",
)

# Keep this version of Soy synchronized with the version used in Gitiles.
maven_jar(
    name = "soy",
    artifact = "com.google.template:soy:2018-03-14",
    sha1 = "76a1322705ba5a6d6329ee26e7387417725ce4b3",
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

maven_jar(
    name = "sshd",
    artifact = "org.apache.sshd:sshd-core:2.0.0",
    sha1 = "f4275079a2463cfd2bf1548a80e1683288a8e86b",
)

maven_jar(
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.2.0",
    sha1 = "0856a92559c4daf744cb27c93cd8b7eb1f8c4780",
)

maven_jar(
    name = "mina-core",
    artifact = "org.apache.mina:mina-core:2.0.17",
    sha1 = "7e10ec974760436d931f3e58be507d1957bcc8db",
)

maven_jar(
    name = "sshd-mina",
    artifact = "org.apache.sshd:sshd-mina:2.0.0",
    sha1 = "50f2669312494f6c1996d8bd0d266c1fca7be6f6",
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
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "hamcrest-core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

TRUTH_VERS = "0.44"

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:" + TRUTH_VERS,
    sha1 = "11eff954c0c14da7d43276d7b3bcf71463105368",
)

maven_jar(
    name = "truth-java8-extension",
    artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
    sha1 = "2081a0721d3101e1cf559f013e59c6129b4b10b0",
)

maven_jar(
    name = "truth-liteproto-extension",
    artifact = "com.google.truth.extensions:truth-liteproto-extension:" + TRUTH_VERS,
    sha1 = "64f47e4e3f79b0a582573098b9c3c6b73599f7c6",
)

maven_jar(
    name = "truth-proto-extension",
    artifact = "com.google.truth.extensions:truth-proto-extension:" + TRUTH_VERS,
    sha1 = "c03fbc16087d8cb3bf0f3265a04566d4beb88a6d",
)

maven_jar(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.3.0",
    sha1 = "7e060dd5b19431e6d198e91ff670644372f60fbd",
)

# When bumping the easymock version number, make sure to also move powermock to a compatible version
maven_jar(
    name = "easymock",
    artifact = "org.easymock:easymock:3.1",
    sha1 = "3e127311a86fc2e8f550ef8ee4abe094bbcf7e7e",
)

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
    artifact = "org.javassist:javassist:3.22.0-GA",
    sha1 = "3e83394258ae2089be7219b971ec21a8288528ad",
)

maven_jar(
    name = "derby",
    artifact = "org.apache.derby:derby:10.12.1.1",
    attach_source = False,
    sha1 = "75070c744a8e52a7d17b8b476468580309d5cd09",
)

JETTY_VERS = "9.4.14.v20181114"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
    sha1 = "96f501462af425190ff7b63e387692c1aa3af2c8",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
    sha1 = "6cbeb2fe9b3cc4f88a7ea040b8a0c4f703cd72ce",
)

maven_jar(
    name = "jetty-servlets",
    artifact = "org.eclipse.jetty:jetty-servlets:" + JETTY_VERS,
    sha1 = "38cfc07b53e5d285bb2fca78bb2531565ed9c9e5",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
    sha1 = "b36a3d52d78a1df6406f6fa236a6eeff48cbfef6",
)

maven_jar(
    name = "jetty-jmx",
    artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
    sha1 = "3e02463d2bff175a3231cd3dc26363eaf76a3b17",
)

maven_jar(
    name = "jetty-continuation",
    artifact = "org.eclipse.jetty:jetty-continuation:" + JETTY_VERS,
    sha1 = "ac4981a61bcaf4e2538de6270300a870224a16b8",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
    sha1 = "6d0c8ac42e9894ae7b5032438eb4579c2a47f4fe",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
    sha1 = "a8c6a705ddb9f83a75777d89b0be59fcef3f7637",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
    sha1 = "5bb3d7a38f7ea54138336591d89dd5867b806c02",
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
    artifact = "org.elasticsearch.client:elasticsearch-rest-client:6.7.1",
    sha1 = "a00aa6cc593a464e4a890d1a226ded5c625659ab",
)

JACKSON_VERSION = "2.9.8"

maven_jar(
    name = "jackson-core",
    artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERSION,
    sha1 = "0f5a654e4675769c716e5b387830d19b501ca191",
)

TESTCONTAINERS_VERSION = "1.10.7"

maven_jar(
    name = "testcontainers",
    artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
    sha1 = "e7575fedfd010ca1ad80c8c9bf971a8057b1ff8a",
)

maven_jar(
    name = "testcontainers-elasticsearch",
    artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
    sha1 = "1ee43ebd81aea1f29bf60a56643bad80c134f998",
)

maven_jar(
    name = "duct-tape",
    artifact = "org.rnorth.duct-tape:duct-tape:1.0.7",
    sha1 = "a26b5d90d88c91321dc7a3734ea72d2fc019ebb6",
)

maven_jar(
    name = "visible-assertions",
    artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.0",
    sha1 = "f2fcff2862860828ac38a5e1f14d941787c06b13",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:4.5.1",
    sha1 = "65bd0cacc9c79a21c6ed8e9f588577cd3c2f85b9",
)

maven_jar(
    name = "javax-activation",
    artifact = "javax.activation:activation:1.1.1",
    sha1 = "485de3a253e23f645037828c07f1d7f1af40763a",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.24.0",
    sha1 = "969a7bcb6f16e076904336ebc7ca171d412cc1f9",
)

BYTE_BUDDY_VERSION = "1.9.7"

maven_jar(
    name = "byte-buddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "8fea78fea6449e1738b675cb155ce8422661e237",
)

maven_jar(
    name = "byte-buddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "8e7d1b599f4943851ffea125fd9780e572727fc0",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
)

load("//tools/bzl:js.bzl", "bower_archive", "npm_binary")

# NPM binaries bundled along with their dependencies.
#
# For full instructions on adding new binaries to the build, see
# http://gerrit-review.googlesource.com/Documentation/dev-bazel.html#npm-binary
npm_binary(
    name = "bower",
)

npm_binary(
    name = "polymer-bundler",
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
    sha1 = "3b01774f58a8085d3c903fc5a32944b26ab7be72",
    version = "2.0.0",
)

bower_archive(
    name = "paper-input",
    package = "polymerelements/paper-input",
    sha1 = "6c934805e80ab201e143406edc73ea0ef35abf80",
    version = "1.1.18",
)

bower_archive(
    name = "paper-tabs",
    package = "polymerelements/paper-tabs",
    sha1 = "b6dd2fbd7ee887534334057a29eb545b940fc5cf",
    version = "2.0.0",
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
    name = "paper-toggle-button",
    package = "polymerelements/paper-toggle-button",
    sha1 = "4a2edbdb52c4531d39fe091f12de650bccda270f",
    version = "1.2.0",
)

bower_archive(
    name = "polymer",
    package = "polymer/polymer",
    sha1 = "158443ab05ade5e2cdc24ebc01f1deef9aebac1b",
    version = "1.11.3",
)

bower_archive(
    name = "polymer-resin",
    package = "polymer/polymer-resin",
    sha1 = "5cb65081d461e710252a1ba1e671fe4c290356ef",
    version = "1.2.8",
)

bower_archive(
    name = "promise-polyfill",
    package = "polymerlabs/promise-polyfill",
    sha1 = "a3b598c06cbd7f441402e666ff748326030905d6",
    version = "1.0.0",
)

bower_archive(
    name = "resemblejs",
    package = "rsmbl/Resemble.js",
    sha1 = "49d5f022417c389b630d6f7ee667aa9540075c42",
    version = "2.10.1",
)

bower_archive(
    name = "codemirror-minified",
    package = "Dominator008/codemirror-minified",
    sha1 = "e6bda82afc7cf3493f4282c6f17265d40e1485e5",
    version = "5.43.0",
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
    package = "polymer/web-component-tester",
    sha1 = "62739cb633fccfddc5eeed98e9e3f69cd0388b5b",
    version = "6.5.0",
)

# Bower component transitive dependencies.
load("//lib/js:bower_archives.bzl", "load_bower_archives")

load_bower_archives()

external_plugin_deps()
