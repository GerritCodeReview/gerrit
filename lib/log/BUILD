package(default_visibility = ["//visibility:public"])

load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-slf4j"],
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    visibility = ["//visibility:public"],
=======
    visibility = [
        "//lib:__pkg__",
        "//plugins:__pkg__",
    ],
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    exports = ["@log-api//jar"],
)

java_library(
    name = "ext",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@log-ext//jar"],
)

java_library(
    name = "impl-log4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@impl-log4j//jar"],
    runtime_deps = [
        ":api",
        ":ext",
        ":log4j"
    ],
)

java_library(
    name = "jcl-over-slf4j",
    data = ["//lib:LICENSE-slf4j"],
    visibility = ["//visibility:public"],
    exports = ["@jcl-over-slf4j//jar"],
)

java_library(
    name = "log4j",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@disruptor//jar",
        "@log4j-api//jar",
        "@log4j-core//jar",
        "@log4j-web//jar",
        "@log4j1.2-api//jar",
    ],
)
