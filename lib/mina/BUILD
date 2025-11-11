load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "sshd",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@maven_deps//sshd-mina/jar",
        "@maven_deps//sshd-osgi/jar",
    ],
    runtime_deps = [":core"],
)

java_library(
    name = "sshd-sftp",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//sshd-sftp/jar"],
)

java_library(
    name = "core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//mina-core/jar"],
)
