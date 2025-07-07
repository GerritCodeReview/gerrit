load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "ow2-asm",
    data = ["//lib:LICENSE-ow2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_ow2_asm_asm"],
)

java_library(
    name = "ow2-asm-analysis",
    data = ["//lib:LICENSE-ow2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_ow2_asm_asm_analysis"],
)

java_library(
    name = "ow2-asm-commons",
    data = ["//lib:LICENSE-ow2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_ow2_asm_asm_commons"],
    runtime_deps = [":ow2-asm-tree"],
)

java_library(
    name = "ow2-asm-tree",
    data = ["//lib:LICENSE-ow2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_ow2_asm_asm_tree"],
)

java_library(
    name = "ow2-asm-util",
    data = ["//lib:LICENSE-ow2"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_ow2_asm_asm_util"],
)
