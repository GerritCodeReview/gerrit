package(default_visibility = ["//gerrit-index:__pkg__"])

[java_library(
    name = n,
    data = ["//lib:LICENSE-antlr"],
    exports = ["@%s//jar" % n],
) for n in [
    "antlr27",
    "stringtemplate",
]]

java_library(
    name = "java_runtime",
    data = ["//lib:LICENSE-antlr"],
    visibility = ["//visibility:public"],
    exports = ["@java_runtime//jar"],
)

java_binary(
    name = "antlr-tool",
    main_class = "org.antlr.Tool",
    visibility = ["//gerrit-index:__pkg__"],
    runtime_deps = [":tool"],
)

java_library(
    name = "tool",
    data = ["//lib:LICENSE-antlr"],
    exports = ["@org_antlr//jar"],
    runtime_deps = [
        ":antlr27",
        ":java_runtime",
        ":stringtemplate",
    ],
)
