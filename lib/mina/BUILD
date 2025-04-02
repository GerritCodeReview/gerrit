load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "sshd",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@sshd-mina//jar",
        "@sshd-osgi//jar",
    ],
    runtime_deps = [":core"],
)

java_library(
    name = "sshd-sftp",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@sshd-sftp//jar"],
)

java_library(
    name = "core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@mina-core//jar"],
)
