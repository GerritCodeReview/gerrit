java_library(
    name = "runtime",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@prolog-runtime//jar"],
)

java_library(
    name = "runtime-neverlink",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@prolog-runtime//jar:neverlink"],
)

java_library(
    name = "compiler",
    data = ["//lib:LICENSE-prologcafe"],
    visibility = ["//visibility:public"],
    exports = ["@prolog-compiler//jar"],
    runtime_deps = [
        ":io",
        ":runtime",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-prologcafe"],
    exports = ["@prolog-io//jar"],
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
    name = "compiler-bin",
    main_class = "BuckPrologCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [":compiler-lib"],
)

java_library(
    name = "compiler-lib",
    srcs = ["java/BuckPrologCompiler.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":compiler",
        ":runtime",
    ],
)
