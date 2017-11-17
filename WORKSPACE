# npm packages are split into different node_modules directories based on their
# usage.
# 1. @npm (node_modules) - contains packages to run tests, check code, etc...
#    It is expected that @npm is used ONLY to run tools. No packages from @npm
#    are used by other code in gerrit.
# 2. @tools_npm (tools/node_tools/node_modules) - the tools/node_tools folder
#    contains self-written tools which are run for building and/or testing. The
#    @tools_npm directory contains all the packages needed to run this tools.
# 3. @ui_npm (polygerrit-ui/app/node_modules) - packages with source code which
#    are necessary to run polygerrit and to bundle it. Only code from these
#    packages can be included in the final bundle for polygerrit. @ui_npm folder
#    must not have devDependencies. All devDependencies must be placed in
#    @ui_dev_npm.
# 4. @ui_dev_npm (polygerrit-ui/node_modules) - devDependencies for polygerrit.
#    The packages from these folder can be used for testing, but must not be
#    included in the final bundle.
# 5. @plugins_npm (plugins/node_modules) - plugin dependencies for polygerrit
#    plugins. The packages here are expected to be used in plugins.
# Note: separation between @ui_npm and @ui_dev_npm is necessary because with
#    rules_nodejs we can't generate two external repositories from the same
#    package.json. At the same time we want to avoid accidental usages of code
#    from devDependencies in polygerrit bundle.
workspace(
    name = "gerrit",
)

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "declare_nongoogle_deps")
load("//tools:deps.bzl", "CAFFEINE_VERS", "java_dependencies")

http_archive(
    name = "platforms",
    sha256 = "379113459b0feaf6bfbb584a91874c065078aa673222846ac765f86661c27407",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.5/platforms-0.0.5.tar.gz",
        "https://github.com/bazelbuild/platforms/releases/download/0.0.5/platforms-0.0.5.tar.gz",
    ],
)

http_archive(
    name = "rbe_jdk11",
    sha256 = "dbcfd6f26589ef506b91fe03a12dc559ca9c84699e4cf6381150522287f0e6f6",
    strip_prefix = "rbe_autoconfig-3.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v3.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/archive/v3.1.0.tar.gz",
    ],
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "3bd7828aa5af4b13b99c191e8b1e884ebfa9ad371b0ce264605d347f135d2568",
    strip_prefix = "protobuf-3.19.4",
    urls = [
        "https://github.com/protocolbuffers/protobuf/archive/v3.19.4.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "94070eff79305be05b7699207fbac5d2608054dd53e6109f7d00d923919ff45a",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.2/rules_nodejs-5.8.2.tar.gz"],
)

load("@build_bazel_rules_nodejs//:repositories.bzl", "build_bazel_rules_nodejs_dependencies")

build_bazel_rules_nodejs_dependencies()

# This is required just because we have a dependency on @bazel/concatjs.
# We don't actually use any of this web_testing stuff.
# TODO: Remove this dependency.
http_archive(
    name = "io_bazel_rules_webtesting",
    sha256 = "e9abb7658b6a129740c0b3ef6f5a2370864e102a5ba5ffca2cea565829ed825a",
    urls = [
        "https://github.com/bazelbuild/rules_webtesting/releases/download/0.3.5/rules_webtesting.tar.gz",
    ],
)

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
load("@io_bazel_rules_webtesting//web:repositories.bzl", "web_test_repositories")

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
web_test_repositories()

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
load("@io_bazel_rules_webtesting//web/versioned:browsers-0.3.3.bzl", "browser_repositories")

# TODO: Remove this, see comments on `io_bazel_rules_webtesting`.
browser_repositories(
    chromium = True,
    firefox = True,
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

# JGit external repository consumed from git submodule
local_repository(
    name = "jgit",
    path = "modules/jgit",
)

java_dependencies()

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
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

FLOGGER_VERS = "0.5"

maven_jar(
    name = "flogger",
    artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
    sha1 = "dd7449aa4aba72969ed409b96996d19906d534d7",
)

maven_jar(
    name = "flogger-log4j2-backend",
    artifact = "com.google.flogger:flogger-log4j2-backend:" + FLOGGER_VERS,
    sha1 = "045635c3ec081b67c7a5d48098477c1233abb458",
)

maven_jar(
    name = "flogger-system-backend",
    artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
    sha1 = "b66d3bedb14da604828a8693bb24fd78e36b0e9e",
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
=======
CAFFEINE_GUAVA_SHA256 = "6e48965614557ba4d3c55a197e20c38f23a20032ef8aace37e95ed64d2ebc9a6"
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

# TODO(davido): Rename guava.jar to caffeine-guava.jar on fetch to prevent potential
# naming collision between caffeine guava adapter and guava library itself.
# Remove this renaming procedure, once this upstream issue is fixed:
# https://github.com/ben-manes/caffeine/issues/364.
http_file(
    name = "caffeine-guava-renamed",
    canonical_id = "caffeine-guava-" + CAFFEINE_VERS + ".jar-" + CAFFEINE_GUAVA_SHA256,
    downloaded_file_path = "caffeine-guava-" + CAFFEINE_VERS + ".jar",
    sha256 = CAFFEINE_GUAVA_SHA256,
    urls = [
        "https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/guava/" +
        CAFFEINE_VERS +
        "/guava-" +
        CAFFEINE_VERS +
        ".jar",
    ],
)

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
maven_jar(
    name = "guava-failureaccess",
    artifact = "com.google.guava:failureaccess:1.0.1",
    sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
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
    name = "jcl-over-slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:" + SLF4J_VERS,
    sha1 = "33fbc2d93de829fa5e263c5ce97f5eab8f57d53e",
)

LOG4J_VERS = "2.11.2"

maven_jar(
    name = "impl-log4j",
    artifact = "org.apache.logging.log4j:log4j-slf4j-impl:" + LOG4J_VERS,
    sha1 = "4d44e4edc4a7fb39f09b95b09f560a15976fa1ba",
)

maven_jar(
    name = "log4j-api",
    artifact = "org.apache.logging.log4j:log4j-api:" + LOG4J_VERS,
    sha1 = "f5e9a2ffca496057d6891a3de65128efc636e26e",
)

maven_jar(
    name = "log4j-core",
    artifact = "org.apache.logging.log4j:log4j-core:" + LOG4J_VERS,
    sha1 = "6c2fb3f5b7cd27504726aef1b674b542a0c9cf53",
)

maven_jar(
    name = "log4j-web",
    artifact = "org.apache.logging.log4j:log4j-web:" + LOG4J_VERS,
    sha1 = "d11ebc03fdf773d32143e0f7ea0fc131c21311e7",
)

maven_jar(
    name = "log4j1.2-api",
    artifact = "org.apache.logging.log4j:log4j-1.2-api:" + LOG4J_VERS,
    sha1 = "afb9ef0baba766725c3733e6a2626877dba72715",
)

maven_jar(
    name = "disruptor",
    artifact = "com.lmax:disruptor:3.3.7",
    sha1 = "8549fdcbda5d64db98871e64a4cd0045611686d1",
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

OW2_VERS = "7.2"

maven_jar(
    name = "ow2-asm",
    artifact = "org.ow2.asm:asm:" + OW2_VERS,
    sha1 = "fa637eb67eb7628c915d73762b681ae7ff0b9731",
)

maven_jar(
    name = "ow2-asm-analysis",
    artifact = "org.ow2.asm:asm-analysis:" + OW2_VERS,
    sha1 = "b6e6abe057f23630113f4167c34bda7086691258",
)

maven_jar(
    name = "ow2-asm-commons",
    artifact = "org.ow2.asm:asm-commons:" + OW2_VERS,
    sha1 = "ca2954e8d92a05bacc28ff465b25c70e0f512497",
)

maven_jar(
    name = "ow2-asm-tree",
    artifact = "org.ow2.asm:asm-tree:" + OW2_VERS,
    sha1 = "3a23cc36edaf8fc5a89cb100182758ccb5991487",
)

maven_jar(
    name = "ow2-asm-util",
    artifact = "org.ow2.asm:asm-util:" + OW2_VERS,
    sha1 = "a3ae34e57fa8a4040e28247291d0cc3d6b8c7bcf",
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

=======
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
declare_nongoogle_deps()

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")

node_repositories(
    node_version = "17.9.1",
    yarn_version = "1.22.19",
)

yarn_install(
    name = "npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:package.json",
    package_path = "",
    symlink_node_modules = True,
    yarn_lock = "//:yarn.lock",
)

yarn_install(
    name = "ui_npm",
    args = [
        "--prod",
        # By default, yarn install all optional dependencies.
        # In some cases, it installs a lot of additional dependencies which
        # are not required (for example, "resemblejs" has one optional
        # dependencies "canvas" that leads to tens of additional dependencies).
        # Each additional dependency requires a license even if it is not used
        # in our code.  We want to ensure that all optional dependencies are
        # explicitly added to package.json.
        "--ignore-optional",
    ],
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/app/package.json",
    package_path = "polygerrit-ui/app",
    symlink_node_modules = True,
    yarn_lock = "//:polygerrit-ui/app/yarn.lock",
)

yarn_install(
    name = "ui_dev_npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/package.json",
    package_path = "polygerrit-ui",
    symlink_node_modules = True,
    yarn_lock = "//:polygerrit-ui/yarn.lock",
)

yarn_install(
    name = "tools_npm",
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:tools/node_tools/package.json",
    package_path = "tools/node_tools",
    symlink_node_modules = True,
    yarn_lock = "//:tools/node_tools/yarn.lock",
)

yarn_install(
    name = "plugins_npm",
    args = ["--prod"],
    exports_directories_only = False,
    frozen_lockfile = False,
    package_json = "//:plugins/package.json",
    package_path = "plugins",
    symlink_node_modules = True,
    yarn_lock = "//:plugins/yarn.lock",
)

external_plugin_deps()
