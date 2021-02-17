# npm packages are split into different node_modules directories based on their usage.
# 1. /node_modules (referenced as @npm) - contains packages to run tests, check code, etc...
#    It is expected that @npm is used ONLY to run tools. No packages from @npm are used by
#    other code in gerrit.
# 2. @tools_npm (tools/node_tools/node_modules) - the tools/node_tools folder contains self-written tools
#    which are run for building and/or testing. The @tools_npm directory contains all the packages needed to
#    run this tools.
# 3. @ui_npm (polygerrit-ui/app/node_modules) - packages with source code which are necessary to run polygerrit
#    and to bundle it. Only code from these packages can be included in the final bundle for polygerrit.
#    @ui_npm folder must not have devDependencies. All dev dependencies must be placed in @ui_dev_npm
# 4. @ui_dev_npm (polygerrit-ui/node_modules) - devDependencies for polygerrit. The packages from these
#    folder can be used for testing, but must not be included in the final bundle.
# 5. @plugins_npm (plugins/node_modules) - plugin dependencies for polygerrit plugins.
#    The packages here are expected to be used in plugins.
# Note: separation between @ui_npm and @ui_dev_npm is necessary because with bazel we can't generate
#    two managed directories from the same package.json. At the same time we want to avoid accidental
#    usages of code from devDependencies in polygerrit bundle.
workspace(
    name = "gerrit",
    managed_directories = {
        "@npm": ["node_modules"],
        "@ui_npm": ["polygerrit-ui/app/node_modules"],
        "@ui_dev_npm": ["polygerrit-ui/node_modules"],
        "@tools_npm": ["tools/node_tools/node_modules"],
        "@plugins_npm": ["plugins/node_modules"],
    },
)

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")
load("//plugins:external_plugin_deps.bzl", "external_plugin_deps")
load("//tools:nongoogle.bzl", "TESTCONTAINERS_VERSION", "declare_nongoogle_deps")

http_archive(
    name = "bazel_toolchains",
    sha256 = "1adf7a8e9901287c644dcf9ca08dd8d67a69df94bedbd57a841490a84dc1e9ed",
    strip_prefix = "bazel-toolchains-5.0.0",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/v5.0.0.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/v5.0.0.tar.gz",
    ],
)

load("@bazel_toolchains//rules:rbe_repo.bzl", "rbe_autoconfig")

# Creates a default toolchain config for RBE.
rbe_autoconfig(
    name = "rbe_jdk11",
    java_home = "/usr/lib/jvm/11.29.3-ca-jdk11.0.2/reduced",
    use_checked_in_confs = "Force",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "d0f5f605d0d656007ce6c8b5a82df3037e1d8fe8b121ed42e536f569dec16113",
    strip_prefix = "protobuf-3.14.0",
    urls = [
        "https://github.com/protocolbuffers/protobuf/archive/v3.14.0.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "fcc6dccb39ca88d481224536eb8f9fa754619676c6163f87aa6af94059b02b12",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/3.2.0/rules_nodejs-3.2.0.tar.gz"],
)

# Golang support for PolyGerrit local dev server.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "4d838e2d70b955ef9dd0d0648f673141df1bc1d7ecf5c2d621dcc163f47dd38a",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.24.12/rules_go-v0.24.12.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.24.12/rules_go-v0.24.12.tar.gz",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains()

http_archive(
    name = "bazel_gazelle",
    sha256 = "222e49f034ca7a1d1231422cdb67066b885819885c356673cb1f72f748a3c9d4",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.3/bazel-gazelle-v0.22.3.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.3/bazel-gazelle-v0.22.3.tar.gz",
    ],
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

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.5",
    sha1 = "f645ed69d595b24d4cf8b3fbb64cc505bede8829",
)

CAFFEINE_VERS = "2.8.5"

maven_jar(
    name = "caffeine",
    artifact = "com.github.ben-manes.caffeine:caffeine:" + CAFFEINE_VERS,
    sha1 = "f0eafef6e1529a44e36549cd9d1fc06d3a57f384",
)

CAFFEINE_GUAVA_SHA256 = "a7ce6d29c40bccd688815a6734070c55b20cd326351a06886a6144005aa32299"

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
    name = "json-smart",
    artifact = "net.minidev:json-smart:1.1.1",
    sha1 = "24a2f903d25e004de30ac602c5b47f2d4e420a59",
)

maven_jar(
    name = "args4j",
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

FLEXMARK_VERS = "0.50.42"

maven_jar(
    name = "flexmark",
    artifact = "com.vladsch.flexmark:flexmark:" + FLEXMARK_VERS,
    sha1 = "ed537d7bc31883b008cc17d243a691c7efd12a72",
)

maven_jar(
    name = "flexmark-ext-abbreviation",
    artifact = "com.vladsch.flexmark:flexmark-ext-abbreviation:" + FLEXMARK_VERS,
    sha1 = "dc27c3e7abbc8d2cfb154f41c68645c365bb9d22",
)

maven_jar(
    name = "flexmark-ext-anchorlink",
    artifact = "com.vladsch.flexmark:flexmark-ext-anchorlink:" + FLEXMARK_VERS,
    sha1 = "6a8edb0165f695c9c19b7143a7fbd78c25c3b99c",
)

maven_jar(
    name = "flexmark-ext-autolink",
    artifact = "com.vladsch.flexmark:flexmark-ext-autolink:" + FLEXMARK_VERS,
    sha1 = "5da7a4d009ea08ef2d8714cc73e54a992c6d2d9a",
)

maven_jar(
    name = "flexmark-ext-definition",
    artifact = "com.vladsch.flexmark:flexmark-ext-definition:" + FLEXMARK_VERS,
    sha1 = "862d17812654624ed81ce8fc89c5ef819ff45f87",
)

maven_jar(
    name = "flexmark-ext-emoji",
    artifact = "com.vladsch.flexmark:flexmark-ext-emoji:" + FLEXMARK_VERS,
    sha1 = "f0d7db64cb546798742b1ffc6db316a33f6acd76",
)

maven_jar(
    name = "flexmark-ext-escaped-character",
    artifact = "com.vladsch.flexmark:flexmark-ext-escaped-character:" + FLEXMARK_VERS,
    sha1 = "6fd9ab77619df417df949721cb29c45914b326f8",
)

maven_jar(
    name = "flexmark-ext-footnotes",
    artifact = "com.vladsch.flexmark:flexmark-ext-footnotes:" + FLEXMARK_VERS,
    sha1 = "e36bd69e43147cc6e19c3f55e4b27c0fc5a3d88c",
)

maven_jar(
    name = "flexmark-ext-gfm-issues",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-issues:" + FLEXMARK_VERS,
    sha1 = "5c825dd4e4fa4f7ccbe30dc92d7e35cdcb8a8c24",
)

maven_jar(
    name = "flexmark-ext-gfm-strikethrough",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:" + FLEXMARK_VERS,
    sha1 = "3256735fd77e7228bf40f7888b4d3dc56787add4",
)

maven_jar(
    name = "flexmark-ext-gfm-tables",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tables:" + FLEXMARK_VERS,
    sha1 = "62f0efcfb974756940ebe749fd4eb01323babc29",
)

maven_jar(
    name = "flexmark-ext-gfm-tasklist",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tasklist:" + FLEXMARK_VERS,
    sha1 = "76d4971ad9ce02f0e70351ab6bd06ad8e405e40d",
)

maven_jar(
    name = "flexmark-ext-gfm-users",
    artifact = "com.vladsch.flexmark:flexmark-ext-gfm-users:" + FLEXMARK_VERS,
    sha1 = "7b0fc7e42e4da508da167fcf8e1cbf9ba7e21147",
)

maven_jar(
    name = "flexmark-ext-ins",
    artifact = "com.vladsch.flexmark:flexmark-ext-ins:" + FLEXMARK_VERS,
    sha1 = "9e51809867b9c4db0fb1c29599b4574e3d2a78e9",
)

maven_jar(
    name = "flexmark-ext-jekyll-front-matter",
    artifact = "com.vladsch.flexmark:flexmark-ext-jekyll-front-matter:" + FLEXMARK_VERS,
    sha1 = "44eb6dbb33b3831d3b40af938ddcd99c9c16a654",
)

maven_jar(
    name = "flexmark-ext-superscript",
    artifact = "com.vladsch.flexmark:flexmark-ext-superscript:" + FLEXMARK_VERS,
    sha1 = "35815b8cb91000344d1fe5df21cacde8553d2994",
)

maven_jar(
    name = "flexmark-ext-tables",
    artifact = "com.vladsch.flexmark:flexmark-ext-tables:" + FLEXMARK_VERS,
    sha1 = "f6768e98c7210b79d5e8bab76fff27eec6db51e6",
)

maven_jar(
    name = "flexmark-ext-toc",
    artifact = "com.vladsch.flexmark:flexmark-ext-toc:" + FLEXMARK_VERS,
    sha1 = "1968d038fc6c8156f244f5a7eecb34e7e2f33705",
)

maven_jar(
    name = "flexmark-ext-typographic",
    artifact = "com.vladsch.flexmark:flexmark-ext-typographic:" + FLEXMARK_VERS,
    sha1 = "6549b9862b61c4434a855a733237103df9162849",
)

maven_jar(
    name = "flexmark-ext-wikilink",
    artifact = "com.vladsch.flexmark:flexmark-ext-wikilink:" + FLEXMARK_VERS,
    sha1 = "e105b09dd35aab6e6f5c54dfe062ee59bd6f786a",
)

maven_jar(
    name = "flexmark-ext-yaml-front-matter",
    artifact = "com.vladsch.flexmark:flexmark-ext-yaml-front-matter:" + FLEXMARK_VERS,
    sha1 = "b2d3a1e7f3985841062e8d3203617e29c6c21b52",
)

maven_jar(
    name = "flexmark-formatter",
    artifact = "com.vladsch.flexmark:flexmark-formatter:" + FLEXMARK_VERS,
    sha1 = "a50c6cb10f6d623fc4354a572c583de1372d217f",
)

maven_jar(
    name = "flexmark-html-parser",
    artifact = "com.vladsch.flexmark:flexmark-html-parser:" + FLEXMARK_VERS,
    sha1 = "46c075f30017e131c1ada8538f1d8eacf652b044",
)

maven_jar(
    name = "flexmark-profile-pegdown",
    artifact = "com.vladsch.flexmark:flexmark-profile-pegdown:" + FLEXMARK_VERS,
    sha1 = "d9aafd47629959cbeddd731f327ae090fc92b60f",
)

maven_jar(
    name = "flexmark-util",
    artifact = "com.vladsch.flexmark:flexmark-util:" + FLEXMARK_VERS,
    sha1 = "417a9821d5d80ddacbfecadc6843ae7b259d5112",
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

AUTO_VALUE_VERSION = "1.7.4"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "6b126cb218af768339e4d6e95a9b0ae41f74e73d",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "eff48ed53995db2dadf0456426cc1f8700136f86",
)

AUTO_VALUE_GSON_VERSION = "1.3.0"

maven_jar(
    name = "auto-value-gson-runtime",
    artifact = "com.ryanharter.auto.value:auto-value-gson-runtime:" + AUTO_VALUE_GSON_VERSION,
    sha1 = "a69a9db5868bb039bd80f60661a771b643eaba59",
)

maven_jar(
    name = "auto-value-gson-extension",
    artifact = "com.ryanharter.auto.value:auto-value-gson-extension:" + AUTO_VALUE_GSON_VERSION,
    sha1 = "6a61236d17b58b05e32b4c532bcb348280d2212b",
)

maven_jar(
    name = "auto-value-gson-factory",
    artifact = "com.ryanharter.auto.value:auto-value-gson-factory:" + AUTO_VALUE_GSON_VERSION,
    sha1 = "b1f01918c0d6cb1f5482500e6b9e62589334dbb0",
)

maven_jar(
    name = "javapoet",
    artifact = "com.squareup:javapoet:1.13.0",
    sha1 = "d6562d385049f35eb50403fa86bb11cce76b866a",
)

maven_jar(
    name = "autotransient",
    artifact = "io.sweers.autotransient:autotransient:1.0.0",
    sha1 = "38b1c630b8e76560221622289f37be40105abb3d",
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

GITILES_VERS = "0.4"

GITILES_REPO = GERRIT

maven_jar(
    name = "blame-cache",
    artifact = "com.google.gitiles:blame-cache:" + GITILES_VERS,
    attach_source = False,
    repository = GITILES_REPO,
    sha1 = "567198123898aa86bd854d3fcb044dc7a1845741",
)

maven_jar(
    name = "gitiles-servlet",
    artifact = "com.google.gitiles:gitiles-servlet:" + GITILES_VERS,
    repository = GITILES_REPO,
    sha1 = "0dd832a6df108af0c75ae29b752fda64ccbd6886",
)

# prettify must match the version used in Gitiles
maven_jar(
    name = "prettify",
    artifact = "com.github.twalcari:java-prettify:1.2.2",
    sha1 = "b8ba1c1eb8b2e45cfd465d01218c6060e887572e",
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

# Base the following org.apache.httpcomponents versions on what
# elasticsearch-rest-client explicitly depends on, except for
# commons-codec (non-http) which is not necessary yet. Note that
# below httpcore version(s) differs from the HTTPCOMP_VERS range,
# upstream: that specific dependency has no HTTPCOMP_VERS version
# equivalent currently.
HTTPCOMP_VERS = "4.5.2"

maven_jar(
    name = "fluent-hc",
    artifact = "org.apache.httpcomponents:fluent-hc:" + HTTPCOMP_VERS,
    sha1 = "7bfdfa49de6d720ad3c8cedb6a5238eec564dfed",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:" + HTTPCOMP_VERS,
    sha1 = "733db77aa8d9b2d68015189df76ab06304406e50",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.4",
    sha1 = "b31526a230871fbe285fbcbe2813f9c0839ae9b0",
)

# Test-only dependencies below.

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

maven_jar(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.3.0",
    sha1 = "7e060dd5b19431e6d198e91ff670644372f60fbd",
)

JETTY_VERS = "9.4.35.v20201120"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
    sha1 = "3e61bcb471e1bfc545ce866cbbe33c3aedeec9b1",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
    sha1 = "80dc2f422789c78315de76d289b7a5b36c3232d5",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
    sha1 = "513502352fd689d4730b2935421b990ada8cc818",
)

maven_jar(
    name = "jetty-jmx",
    artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
    sha1 = "38812031940a466d626ab5d9bbbd9d5d39e9f735",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
    sha1 = "45d35131a35a1e76991682174421e8cdf765fb9f",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
    sha1 = "eb9460700b99b71ecd82a53697f5ff99f69b9e1c",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
    sha1 = "ef61b83f9715c3b5355b633d9f01d2834f908ece",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VERS,
    sha1 = "ebbb43912c6423bedb3458e44aee28eeb4d66f27",
    src_sha1 = "b3acea974a17493afb125a9dfbe783870ce1d2f9",
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

load("@build_bazel_rules_nodejs//:index.bzl", "yarn_install")

yarn_install(
    name = "npm",
    frozen_lockfile = False,
    package_json = "//:package.json",
    yarn_lock = "//:yarn.lock",
)

yarn_install(
    name = "ui_npm",
    args = ["--prod"],
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/app/package.json",
    yarn_lock = "//:polygerrit-ui/app/yarn.lock",
)

yarn_install(
    name = "ui_dev_npm",
    frozen_lockfile = False,
    package_json = "//:polygerrit-ui/package.json",
    yarn_lock = "//:polygerrit-ui/yarn.lock",
)

yarn_install(
    name = "tools_npm",
    frozen_lockfile = False,
    package_json = "//:tools/node_tools/package.json",
    yarn_lock = "//:tools/node_tools/yarn.lock",
)

yarn_install(
    name = "plugins_npm",
    args = ["--prod"],
    frozen_lockfile = False,
    package_json = "//:plugins/package.json",
    yarn_lock = "//:plugins/yarn.lock",
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
    sha1 = "a1ddf3a6dcc6817597eacc52688cfe5083ded4cd",
    version = "5.59.1",
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
    sha1 = "d84f6a13bde5f8fd39ee208d43f33925410530d7",
    version = "6.5.1",
)

external_plugin_deps()

# When upgrading elasticsearch-rest-client, also upgrade httpcore-nio
# and httpasyncclient as necessary in tools/nongoogle.bzl. Consider
# also the other org.apache.httpcomponents dependencies in
# WORKSPACE.
maven_jar(
    name = "elasticsearch-rest-client",
    artifact = "org.elasticsearch.client:elasticsearch-rest-client:7.8.1",
    sha1 = "59feefe006a96a39f83b0dfb6780847e06c1d0a8",
)

maven_jar(
    name = "testcontainers-elasticsearch",
    artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
    sha1 = "6b778a270b7529fcb9b7a6f62f3ae9d38544ce2f",
)
