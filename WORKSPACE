workspace(name = "gerrit")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "declare_nongoogle_deps")

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
    sha256 = "4d63a36dcca040475bd6deb815b9a600bd686e1413ac1ebd4b04516edd675020",
    urls = ["https://raw.githubusercontent.com/google/closure-compiler/35d2b3340ff23a69441f10fa3bc820691c2942f2/contrib/externs/polymer-1.0.js"],
)

# Check Bazel version when invoked by Bazel directly
load("//tools/bzl:bazelisk_version.bzl", "bazelisk_version")

bazelisk_version(name = "bazelisk_version")

load("@bazelisk_version//:check.bzl", "check_bazel_version")

check_bazel_version()

load("@io_bazel_rules_closure//closure:repositories.bzl", "rules_closure_dependencies", "rules_closure_toolchains")

# Prevent redundant loading of dependencies.
# TODO(davido): Omit re-fetching ancient args4j version when these PRs are merged:
# https://github.com/bazelbuild/rules_closure/pull/262
# https://github.com/google/closure-templates/pull/155
rules_closure_dependencies(
    omit_aopalliance = True,
    omit_bazel_skylib = True,
    omit_javax_inject = True,
    omit_rules_cc = True,
)

rules_closure_toolchains()

# Golang support for PolyGerrit local dev server.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "f04d2373bcaf8aa09bccb08a98a57e721306c8f6043a2a0ee610fd6853dcde3d",
    urls = [
        "https://storage.googleapis.com/bazel-mirror/github.com/bazelbuild/rules_go/releases/download/0.18.6/rules_go-0.18.6.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/0.18.6/rules_go-0.18.6.tar.gz",
    ],
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

# JGit external repository consumed from git submodule
local_repository(
    name = "jgit",
    path = "modules/jgit",
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
    name = "servlet-api",
    artifact = "org.apache.tomcat:tomcat-servlet-api:8.5.23",
    sha1 = "021a212688ec94fe77aff74ab34cc74f6f940e60",
)

# JGit's transitive dependencies
maven_jar(
    name = "hamcrest-library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.1",
    sha1 = "a1551373315ffc2f96130a0e5704f74e151777ba",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    attach_source = False,
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "error-prone-annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.3.3",
    sha1 = "42aa5155a54a87d70af32d4b0d06bf43779de0e2",
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

load("//lib:guava.bzl", "GUAVA_BIN_SHA1", "GUAVA_VERSION")

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:" + GUAVA_VERSION,
    sha1 = GUAVA_BIN_SHA1,
)

CAFFEINE_VERS = "2.8.0"

maven_jar(
    name = "caffeine",
    artifact = "com.github.ben-manes.caffeine:caffeine:" + CAFFEINE_VERS,
    sha1 = "6000774d7f8412ced005a704188ced78beeed2bb",
)

# TODO(davido): Rename guava.jar to caffeine-guava.jar on fetch to prevent potential
# naming collision between caffeine guava adapater and guava library itself.
# Remove this renaming procedure, once this upstream issue is fixed:
# https://github.com/ben-manes/caffeine/issues/364.
http_file(
    name = "caffeine-guava-renamed",
    downloaded_file_path = "caffeine-guava-" + CAFFEINE_VERS + ".jar",
    sha256 = "3a66ee3ec70971dee0bae6e56bda7b8742bc4bedd7489161bfbbaaf7137d89e1",
    urls = [
        "https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/guava/" +
        CAFFEINE_VERS +
        "/guava-" +
        CAFFEINE_VERS +
        ".jar",
    ],
)

maven_jar(
    name = "guava-failureaccess",
    artifact = "com.google.guava:failureaccess:1.0.1",
    sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
)

CAFFEINE_VERS = "2.8.0"

maven_jar(
    name = "caffeine",
    artifact = "com.github.ben-manes.caffeine:caffeine:" + CAFFEINE_VERS,
    sha1 = "6000774d7f8412ced005a704188ced78beeed2bb",
)

# TODO(davido): Rename guava.jar to caffeine-guava.jar on fetch to prevent potential
# naming collision between caffeine guava adapater and guava library itself.
# Remove this renaming procedure, once this upstream issue is fixed:
# https://github.com/ben-manes/caffeine/issues/364.
http_file(
    name = "caffeine-guava-renamed",
    downloaded_file_path = "caffeine-guava-" + CAFFEINE_VERS + ".jar",
    sha256 = "3a66ee3ec70971dee0bae6e56bda7b8742bc4bedd7489161bfbbaaf7137d89e1",
    urls = [
        "https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/guava/" +
        CAFFEINE_VERS +
        "/guava-" +
        CAFFEINE_VERS +
        ".jar",
    ],
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
    artifact = "org.apache.commons:commons-compress:1.18",
    sha1 = "1191f9f2bc0c47a8cce69193feb1ff0a8bcb37d5",
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

AUTO_VALUE_VERSION = "1.7"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "fe8387764ed19460eda4f106849c664f51c07121",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "5be124948ebdc7807df68207f35a0f23ce427f29",
)

declare_nongoogle_deps()

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

GITILES_VERS = "0.3-7"

GITILES_REPO = GERRIT

maven_jar(
    name = "blame-cache",
    artifact = "com.google.gitiles:blame-cache:" + GITILES_VERS,
    attach_source = False,
    repository = GITILES_REPO,
    sha1 = "af6212a62363906c63d367f8276ae1645f83bf93",
)

maven_jar(
    name = "gitiles-servlet",
    artifact = "com.google.gitiles:gitiles-servlet:" + GITILES_VERS,
    repository = GITILES_REPO,
    sha1 = "6a53f722f8572a2f1bcb7d86e5692168844bab76",
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
    artifact = "com.google.template:soy:2019-10-08",
    sha1 = "4518bf8bac2dbbed684849bc209c39c4cb546237",
)

maven_jar(
    name = "html-types",
    artifact = "com.google.common.html.types:types:1.0.8",
    sha1 = "9e9cf7bc4b2a60efeb5f5581fe46d17c068e0777",
)

maven_jar(
    name = "icu4j",
    artifact = "com.ibm.icu:icu4j:57.1",
    sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
)

# When updating Bouncy Castle, also update it in bazlets.
BC_VERS = "1.61"

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BC_VERS,
    sha1 = "00df4b474e71be02c1349c3292d98886f888d1f7",
)

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BC_VERS,
    sha1 = "422656435514ab8a28752b117d5d2646660a0ace",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BC_VERS,
    sha1 = "89bb3aa5b98b48e584eee2a7401b7682a46779b4",
)

maven_jar(
    name = "h2",
    artifact = "com.h2database:h2:1.3.176",
    sha1 = "fd369423346b2f1525c413e33f8cf95b09c92cbd",
)

# Note that all of the following org.apache.httpcomponents have newer versions,
# but 4.4.1 is the only version that is available for all of them.
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

TRUTH_VERS = "1.0"

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:" + TRUTH_VERS,
    sha1 = "998e5fb3fa31df716574b4c9e8d374855e800451",
)

maven_jar(
    name = "truth-java8-extension",
    artifact = "com.google.truth.extensions:truth-java8-extension:" + TRUTH_VERS,
    sha1 = "d85fbc1daf0510821f552f2aa71d9605e97aa438",
)

maven_jar(
    name = "truth-liteproto-extension",
    artifact = "com.google.truth.extensions:truth-liteproto-extension:" + TRUTH_VERS,
    sha1 = "7a279c50a0f93da15533cef4993b45606cf67d72",
)

maven_jar(
    name = "truth-proto-extension",
    artifact = "com.google.truth.extensions:truth-proto-extension:" + TRUTH_VERS,
    sha1 = "8c0c2ea61750f02d0d5ce9c653106b6a5dc82d12",
)

maven_jar(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.3.0",
    sha1 = "7e060dd5b19431e6d198e91ff670644372f60fbd",
)

JETTY_VERS = "9.4.18.v20190429"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
    sha1 = "290f7a88f351950d51ebc9fb4a794752c62d7de5",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
    sha1 = "01aceff3608ca1b223bfd275a497797cfe675ef4",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
    sha1 = "b76ef50e04635f11d4d43bc6ccb7c4482a8384f0",
)

maven_jar(
    name = "jetty-jmx",
    artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
    sha1 = "f4c2654db1a55f0780acdfcee8bb98550f56ca70",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
    sha1 = "c2e73db2db5c369326b717da71b6587b3da11e0e",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
    sha1 = "844af5efe58ab23fd0166a796efef123f4cb06b0",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
    sha1 = "13e6148bfda7ae511f69ae7e5e3ea898bc9b0e33",
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
    name = "javax-activation",
    artifact = "javax.activation:activation:1.1.1",
    sha1 = "485de3a253e23f645037828c07f1d7f1af40763a",
)

maven_jar(
    name = "javax-annotation",
    artifact = "javax.annotation:javax.annotation-api:1.3.2",
    sha1 = "934c04d3cfef185a8008e7bf34331b79730a9d43",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.24.0",
    sha1 = "969a7bcb6f16e076904336ebc7ca171d412cc1f9",
)

BYTE_BUDDY_VERSION = "1.9.7"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "8fea78fea6449e1738b675cb155ce8422661e237",
)

maven_jar(
    name = "bytebuddy-agent",
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
    sha1 = "2f04c7e2a72d462de36093ab2b4889db20f699f6",
    version = "2.2.0",
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
    sha1 = "3902ba164552b1bfc59e6fa692efa4a1fd8dd4ea",
    version = "2.2.1",
)

bower_archive(
    name = "iron-input",
    package = "polymerelements/iron-input",
    sha1 = "f79952ff4f6f103c0a2cbd3dacf25935257ff392",
    version = "2.1.3",
)

bower_archive(
    name = "iron-overlay-behavior",
    package = "polymerelements/iron-overlay-behavior",
    sha1 = "c2d2eac1b162420d9475ade2f16d5db8959b93fc",
    version = "2.3.4",
)

bower_archive(
    name = "iron-selector",
    package = "polymerelements/iron-selector",
    sha1 = "3f3fcb55f6bd606ea493f99eab9daae21f7a6139",
    version = "2.1.0",
)

bower_archive(
    name = "paper-button",
    package = "polymerelements/paper-button",
    sha1 = "bcb783d74e1177c1d0836340e7c0280699d1438c",
    version = "2.1.3",
)

bower_archive(
    name = "paper-input",
    package = "polymerelements/paper-input",
    sha1 = "c1a81a4173d22e72e8ab609eb3715a75273396b3",
    version = "2.2.3",
)

bower_archive(
    name = "paper-tabs",
    package = "polymerelements/paper-tabs",
    sha1 = "589b8e6efa0f171c93233137c8ea013dcea0ffc7",
    version = "2.1.1",
)

bower_archive(
    name = "iron-icon",
    package = "polymerelements/iron-icon",
    sha1 = "d21e7d4f1bdc6de881390f888e28d53155eeb551",
    version = "2.1.0",
)

bower_archive(
    name = "iron-iconset-svg",
    package = "polymerelements/iron-iconset-svg",
    sha1 = "07c0ce02ce6479856758893416a3709009db7f22",
    version = "2.2.1",
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
    sha1 = "4a31889cd75cc5e7f68a4c7f256eecaf27102eee",
    version = "1.11.4",
)

bower_archive(
    name = "paper-item",
    package = "polymerelements/paper-item",
    sha1 = "c3bad022cf182d2bf1c8a44374c7fcb1409afbfa",
    version = "2.1.1",
)

bower_archive(
    name = "paper-listbox",
    package = "polymerelements/paper-listbox",
    sha1 = "78247cc32bb776f204efef17cff3095878036a40",
    version = "2.1.1",
)

bower_archive(
    name = "paper-toggle-button",
    package = "polymerelements/paper-toggle-button",
    sha1 = "9927960afb0062726ec1b585ef3e32764c3bbac9",
    version = "2.1.1",
)

bower_archive(
    name = "polymer",
    package = "polymer/polymer",
    sha1 = "d06e17a1d8dc6187ee5aa8c5b3501da10901c82f",
    version = "2.7.2",
)

bower_archive(
    name = "polymer-resin",
    package = "polymer/polymer-resin",
    sha1 = "94c29926c20ea3a9b636f26b3e0d689ead8137e5",
    version = "2.0.1",
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
    sha1 = "882be2d4c8714b39299b5f7bf25253c4e8a40761",
    version = "2.0.1",
)

bower_archive(
    name = "test-fixture",
    package = "polymerelements/test-fixture",
    sha1 = "7d72ddfebf555a2dd1fc60a85427d9026b509723",
    version = "3.0.0",
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
