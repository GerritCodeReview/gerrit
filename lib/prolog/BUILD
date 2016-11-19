java_library(
    name = "runtime",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@prolog_runtime//jar"],
)

java_library(
    name = "compiler",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@prolog_compiler//jar"],
    runtime_deps = [
        ":io",
        ":runtime",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-prologcafe"],
    exports = ["@prolog_io//jar"],
)

java_library(
    name = "cafeteria",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@cafeteria//jar"],
    runtime_deps = [
        "io",
        "runtime",
    ],
)

java_binary(
    name = "compiler_bin",
    main_class = "BuckPrologCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":compiler_lib"],
)

java_library(
    name = "compiler_lib",
    srcs = ["java/BuckPrologCompiler.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":compiler",
        ":runtime",
    ],
)
