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
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "servlet-api-3_1-without-neverlink",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@servlet_api_3_1//jar"],
)

java_library(
    name = "gwtjsonrpc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar"],
)

java_library(
    name = "gwtjsonrpc_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtjsonrpc//jar:src"],
)

java_library(
    name = "gson",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gson//jar"],
)

java_library(
    name = "gwtorm_client",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar"],
)

java_library(
    name = "gwtorm_client_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gwtorm_client//jar:src"],
)

java_library(
    name = "protobuf",
    data = ["//lib:LICENSE-protobuf"],
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
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guava//jar"],
)

java_library(
    name = "velocity",
    data = ["//lib:LICENSE-Apache2.0"],
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
    data = ["//lib:LICENSE-jsch"],
    visibility = ["//visibility:public"],
    exports = ["@jsch//jar"],
)

java_library(
    name = "juniversalchardet",
    data = ["//lib:LICENSE-MPL1.1"],
    visibility = ["//visibility:public"],
    exports = ["@juniversalchardet//jar"],
)

java_library(
    name = "args4j",
    data = ["//lib:LICENSE-args4j"],
    visibility = ["//visibility:public"],
    exports = ["@args4j//jar"],
)

java_library(
    name = "automaton",
    data = ["//lib:LICENSE-automaton"],
    visibility = ["//visibility:public"],
    exports = ["@automaton//jar"],
)

java_library(
    name = "pegdown",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@pegdown//jar"],
    runtime_deps = [":grappa"],
)

java_library(
    name = "grappa",
    data = ["//lib:LICENSE-Apache2.0"],
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
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jitescript//jar"],
)

java_library(
    name = "tukaani-xz",
    data = ["//lib:LICENSE-xz"],
    visibility = ["//visibility:public"],
    exports = ["@tukaani_xz//jar"],
)

java_library(
    name = "mime-util",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@mime_util//jar"],
)

java_library(
    name = "guava-retrying",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guava_retrying//jar"],
    runtime_deps = [":jsr305"],
)

java_library(
    name = "jsr305",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jsr305//jar"],
)

java_library(
    name = "blame-cache",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@blame_cache//jar"],
)

java_library(
    name = "h2",
    data = ["//lib:LICENSE-h2"],
    visibility = ["//visibility:public"],
    exports = ["@h2//jar"],
)

java_library(
    name = "jimfs",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@jimfs//jar"],
    runtime_deps = [":guava"],
)

java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":hamcrest-core",
        "@junit//jar",
    ],
    runtime_deps = [":hamcrest-core"],
)

java_library(
    name = "hamcrest-core",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@hamcrest_core//jar"],
)

java_library(
    name = "truth",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava",
        ":junit",
        "@truth//jar",
    ],
)

java_library(
    name = "truth-java8-extension",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":guava",
        ":truth",
        "@truth-java8-extension//jar",
    ],
)

java_library(
    name = "javassist",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@javassist//jar"],
)

java_library(
    name = "derby",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@derby//jar"],
)

java_library(
    name = "soy",
    data = ["//lib:LICENSE-Apache2.0"],
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
    data = ["//lib:LICENSE-icu4j"],
    visibility = ["//visibility:public"],
    exports = ["@icu4j//jar"],
)

java_library(
    name = "postgresql",
    data = ["//lib:LICENSE-postgresql"],
    visibility = ["//visibility:public"],
    exports = ["@postgresql//jar"],
)

java_library(
    name = "commons-io",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@commons_io//jar"],
)
