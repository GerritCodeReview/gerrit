load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_library(
    name = "runtime",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_runtime"],
)

java_library(
    name = "runtime-neverlink",
    data = ["//lib:LICENSE-prologcafe"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_runtime"],
)

java_library(
    name = "compiler",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_compiler"],
    runtime_deps = [
        ":io",
        ":runtime",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-prologcafe"],
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_io"],
)

java_library(
    name = "cafeteria",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_cafeteria"],
    runtime_deps = [
        "io",
        "runtime",
    ],
)

java_binary(
    name = "compiler-bin",
    main_class = "BazelPrologCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":compiler-lib"],
)

java_library(
    name = "compiler-lib",
    srcs = ["java/BazelPrologCompiler.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":compiler",
        ":runtime",
    ],
)
