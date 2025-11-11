load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-slf4j"],
    visibility = [
        "//lib:__pkg__",
        "//plugins:__pkg__",
    ],
    exports = ["@maven_deps//log-api/jar"],
)

java_library(
    name = "ext",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//log-ext/jar"],
)

java_library(
    name = "impl-log4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//impl-log4j/jar"],
    runtime_deps = [":log4j"],
)

java_library(
    name = "jcl-over-slf4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//jcl-over-slf4j/jar"],
)

java_library(
    name = "log4j",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//log4j/jar"],
)

java_library(
    name = "json-smart",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//json-smart/jar"],
)
