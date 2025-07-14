load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(default_visibility = ["//java/com/google/gerrit/index:__pkg__"])

java_library(
    name = "antlr27",
    data = ["//lib:LICENSE-antlr"],
    exports = ["@gerrit_deps//:antlr_antlr_2_7_7"],
)

java_library(
    name = "stringtemplate",
    data = ["//lib:LICENSE-antlr"],
    exports = ["@gerrit_deps//:org_antlr_stringtemplate"],
)

java_library(
    name = "java-runtime",
    data = ["//lib:LICENSE-antlr"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:org_antlr_antlr_runtime"],
)

# See https://github.com/bazelbuild/bazel/issues/3542
# for why we need to tweak jvm flags in this rule.
java_binary(
    name = "antlr-tool",
    jvm_flags = ["-XX:-UsePerfData"],
    main_class = "org.antlr.Tool",
    visibility = ["//antlr3:__pkg__"],
    runtime_deps = [":tool"],
)

java_library(
    name = "tool",
    data = ["//lib:LICENSE-antlr"],
    exports = ["@gerrit_deps//:org_antlr_antlr"],
    runtime_deps = [
        ":antlr27",
        ":java-runtime",
        ":stringtemplate",
    ],
)
