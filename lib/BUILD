exports_files(glob([
    "LICENSE-*",
]))

filegroup(
    name = "all-licenses",
    srcs = glob(
        ["LICENSE-*"],
        exclude = ["LICENSE-DO_NOT_DISTRIBUTE"],
    ),
    visibility = ["//visibility:public"],
)

java_library(
    name = "servlet-api-3_1",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "servlet-api-3_1-without-neverlink",
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "gwtjsonrpc",
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar"],
)

java_library(
    name = "gwtjsonrpc_src",
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar:src"],
)

java_library(
    name = "gson",
    visibility = ["//visibility:public"],
    exports = ["@gson//jar"],
)

java_library(
    name = "gwtorm_client",
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar"],
)

java_library(
    name = "gwtorm_client_src",
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar:src"],
)

java_library(
    name = "protobuf",
    visibility = ["//visibility:public"],
    exports = ["@protobuf//jar"],
)

java_library(
    name = "gwtorm",
    visibility = ["//visibility:public"],
    exports = [":gwtorm_client"],
    runtime_deps = [":protobuf"],
)

java_library(
    name = "guava",
    visibility = ["//visibility:public"],
    exports = ["@guava//jar"],
)

java_library(
    name = "velocity",
    visibility = ["//visibility:public"],
    exports = ["@velocity//jar"],
    runtime_deps = [
        "//lib/commons:collections",
        "//lib/commons:lang",
        "//lib/commons:oro",
    ],
)

java_library(
    name = "jsch",
    visibility = ["//visibility:public"],
    exports = ["@jsch//jar"],
)

java_library(
    name = "juniversalchardet",
    visibility = ["//visibility:public"],
    exports = ["@juniversalchardet//jar"],
)

java_library(
    name = "args4j",
    visibility = ["//visibility:public"],
    exports = ["@args4j//jar"],
)

java_library(
    name = "automaton",
    visibility = ["//visibility:public"],
    exports = ["@automaton//jar"],
)

java_library(
    name = "pegdown",
    visibility = ["//visibility:public"],
    exports = ["@pegdown//jar"],
    runtime_deps = [":grappa"],
)

java_library(
    name = "grappa",
    visibility = ["//visibility:public"],
    exports = ["@grappa//jar"],
    runtime_deps = [
        ":jitescript",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-analysis",
        "//lib/ow2:ow2-asm-tree",
        "//lib/ow2:ow2-asm-util",
    ],
)

java_library(
    name = "jitescript",
    visibility = ["//visibility:public"],
    exports = ["@jitescript//jar"],
)

java_library(
    name = "tukaani-xz",
    visibility = ["//visibility:public"],
    exports = ["@tukaani_xz//jar"],
)

java_library(
    name = "mime-util",
    visibility = ["//visibility:public"],
    exports = ["@mime_util//jar"],
)

java_library(
    name = "guava-retrying",
    visibility = ["//visibility:public"],
    exports = ["@guava_retrying//jar"],
    runtime_deps = [":jsr305"],
)

java_library(
    name = "jsr305",
    exports = ["@jsr305//jar"],
)

java_library(
    name = "blame-cache",
    visibility = ["//visibility:public"],
    exports = ["@blame_cache//jar"],
)

java_library(
    name = "h2",
    visibility = ["//visibility:public"],
    exports = ["@h2//jar"],
)

java_library(
    name = "jimfs",
    visibility = ["//visibility:public"],
    exports = ["@jimfs//jar"],
    runtime_deps = [":guava"],
)

java_library(
    name = "junit",
    visibility = ["//visibility:public"],
    exports = [
        ":hamcrest-core",
        "@junit//jar",
    ],
    runtime_deps = [":hamcrest-core"],
)

java_library(
    name = "hamcrest-core",
    visibility = ["//visibility:public"],
    exports = ["@hamcrest_core//jar"],
)

java_library(
    name = "truth",
    visibility = ["//visibility:public"],
    exports = [
        ":guava",
        ":junit",
        "@truth//jar",
    ],
)

java_library(
    name = "javassist",
    visibility = ["//visibility:public"],
    exports = ["@javassist//jar"],
)

java_library(
    name = "derby",
    visibility = ["//visibility:public"],
    exports = ["@derby//jar"],
)

java_library(
    name = "soy",
    visibility = ["//visibility:public"],
    exports = ["@soy//jar"],
    runtime_deps = [
        ":args4j",
        ":gson",
        ":guava",
        ":icu4j",
        ":jsr305",
        ":protobuf",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:javax-inject",
        "//lib/guice:multibindings",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-analysis",
        "//lib/ow2:ow2-asm-commons",
        "//lib/ow2:ow2-asm-util",
    ],
)

java_library(
    name = "icu4j",
    visibility = ["//visibility:public"],
    exports = ["@icu4j//jar"],
)

java_library(
    name = "postgresql",
    visibility = ["//visibility:public"],
    exports = ["@postgresql//jar"],
)

java_library(
    name = "commons-io",
    visibility = ["//visibility:public"],
    exports = ["@commons_io//jar"],
)
