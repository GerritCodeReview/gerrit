[java_library(
    name = n,
    data = ["//lib:LICENSE-antlr"],
    exports = ["@%s//jar" % n],
) for n in [
    "antlr27",
    "stringtemplate",
]]

java_library(
    name = "java-runtime",
    data = ["//lib:LICENSE-antlr"],
    visibility = ["//visibility:public"],
    exports = ["@java-runtime//jar"],
)

java_binary(
    name = "antlr-tool",
    main_class = "org.antlr.Tool",
    visibility = ["//gerrit-antlr:__pkg__"],
    runtime_deps = [":tool"],
)

java_library(
    name = "tool",
    data = ["//lib:LICENSE-antlr"],
    exports = ["@org-antlr//jar"],
    runtime_deps = [
        ":antlr27",
        ":java-runtime",
        ":stringtemplate",
    ],
)
