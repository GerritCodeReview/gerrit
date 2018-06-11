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
    name = "java-runtime",
    data = ["//lib:LICENSE-antlr"],
    visibility = ["//visibility:public"],
    exports = ["@java-runtime//jar"],
)

# See https://github.com/bazelbuild/bazel/issues/3542
# for why we need to tweak jvm flags in this rule.
java_binary(
    name = "antlr-tool",
    jvm_flags = ["-XX:-UsePerfData"],
    main_class = "org.antlr.Tool",
    visibility = ["//gerrit-index:__pkg__"],
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
