load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "sshd",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:org_apache_sshd_sshd_mina",
        "@external_deps//:org_apache_sshd_sshd_osgi",
    ],
    runtime_deps = [":core"],
)

java_library(
    name = "sshd-sftp",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_apache_sshd_sshd_sftp"],
)

java_library(
    name = "core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_apache_mina_mina_core"],
)
