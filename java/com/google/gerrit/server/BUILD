CONSTANTS_SRC = [
    "documentation/Constants.java",
]

GERRIT_GLOBAL_MODULE_SRC = [
    "config/GerritGlobalModule.java",
]

java_library(
    name = "constants",
    srcs = CONSTANTS_SRC,
    visibility = ["//visibility:public"],
)

# Giant kitchen-sink target.
#
# The only reason this hasn't been split up further is because we have too many
# tangled dependencies (and Guice unfortunately makes it quite easy to get into
# this state). Which means if you see an opportunity to split something off, you
# should seize it.
java_library(
    name = "server",
    srcs = glob(
        ["**/*.java"],
        exclude = CONSTANTS_SRC + GERRIT_GLOBAL_MODULE_SRC,
    ),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/server"],
    visibility = ["//visibility:public"],
    deps = [
        ":constants",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/config",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/prettify:server",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/cache",
        "//java/com/google/gerrit/server/ioutil",
        "//java/com/google/gerrit/server/securestore",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gerrit/util/common",
        "//java/com/google/gerrit/util/ssl",
        "//java/org/apache/commons/net",
        "//java/org/eclipse/jgit:server",
        "//lib:args4j",
        "//lib:automaton",
        "//lib:blame-cache",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:juniversalchardet",
        "//lib:mime-util",
        "//lib:pegdown",
        "//lib:protobuf",
        "//lib:servlet-api-3_1",
        "//lib:soy",
        "//lib:tukaani-xz",
        "//lib/auto:auto-value",
        "//lib/bouncycastle:bcpkix-neverlink",
        "//lib/bouncycastle:bcprov-neverlink",
        "//lib/commons:codec",
        "//lib/commons:compress",
        "//lib/commons:dbcp",
        "//lib/commons:lang",
        "//lib/commons:net",
        "//lib/commons:validator",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit.archive:jgit-archive",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jsoup",
        "//lib/log:api",
        "//lib/log:jsonevent-layout",
        "//lib/log:log4j",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core-and-backward-codecs",
        "//lib/lucene:lucene-queryparser",
        "//lib/mime4j:core",
        "//lib/mime4j:dom",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-tree",
        "//lib/ow2:ow2-asm-util",
        "//lib/prolog:runtime",
    ],
)

# Large modules that import things from all across the server package
# hierarchy, so they need lots of dependencies.
java_library(
    name = "module",
    srcs = GERRIT_GLOBAL_MODULE_SRC,
    visibility = ["//visibility:public"],
    deps = [
        ":server",
        "//java/com/google/gerrit/config",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/server/cache",
        "//java/com/google/gerrit/server/git/receive",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/securestore",
        "//lib:blame-cache",
        "//lib:guava",
        "//lib:soy",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

load("//tools/bzl:javadoc.bzl", "java_doc")

java_doc(
    name = "doc",
    libs = [":server"],
    pkgs = ["com.google.gerrit"],
    title = "Gerrit Review Server Documentation",
)
