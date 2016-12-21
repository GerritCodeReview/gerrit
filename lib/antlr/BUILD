[java_library(
    name = n,
    exports = ["@%s//jar" % n],
) for n in [
    "antlr27",
    "stringtemplate",
]]

java_library(
    name = "java_runtime",
    visibility = ["//visibility:public"],
    exports = ["@java_runtime//jar"],
)

java_binary(
    name = "antlr-tool",
    main_class = "org.antlr.Tool",
    visibility = ["//gerrit-antlr:__pkg__"],
    runtime_deps = [":tool"],
)

java_library(
    name = "tool",
    exports = ["@org_antlr//jar"],
    runtime_deps = [
        ":antlr27",
        ":java_runtime",
        ":stringtemplate",
    ],
)
