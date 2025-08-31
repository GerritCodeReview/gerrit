load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-slf4j"],
    visibility = [
        "//lib:__pkg__",
        "//plugins:__pkg__",
    ],
    exports = ["@external_deps//:org_slf4j_slf4j_api"],
)

java_library(
    name = "ext",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_slf4j_slf4j_ext"],
)

java_library(
    name = "impl-log4j",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_apache_logging_log4j_log4j_slf4j2_impl"],
    runtime_deps = [":log4j"],
)

java_library(
    name = "jcl-over-slf4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_slf4j_jcl_over_slf4j"],
)

java_library(
    name = "log4j",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:org_apache_logging_log4j_log4j_1_2_api",
        "@external_deps//:org_apache_logging_log4j_log4j_api",
        "@external_deps//:org_apache_logging_log4j_log4j_core",
        "@external_deps//:org_apache_logging_log4j_log4j_web",
    ],
)

java_library(
    name = "json-smart",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:net_minidev_json_smart"],
)
