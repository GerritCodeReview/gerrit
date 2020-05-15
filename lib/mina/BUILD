load("@rules_java//java:defs.bzl", "java_import", "java_library")

java_import(
    name = "sshd-library",
    jars = ["@sshd//file"],
)

java_import(
    name = "sshd-common",
    jars = ["@sshd-common//file"],
)

java_library(
    name = "sshd",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":eddsa",
        ":sshd-common",
        ":sshd-library",
        "@sshd-mina//jar",
    ],
    runtime_deps = [":core"],
)

java_library(
    name = "eddsa",
    data = ["//lib:LICENSE-CC0-1.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@eddsa//jar",
    ],
)

java_library(
    name = "core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@mina-core//jar"],
)
