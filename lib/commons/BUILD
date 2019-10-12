load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "codec",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/httpcomponents:__pkg__"],
    exports = ["@commons-codec//jar"],
)

java_library(
    name = "compress",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-compress//jar"],
)

java_library(
    name = "lang",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-lang//jar"],
)

java_library(
    name = "lang3",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-lang3//jar"],
)

java_library(
    name = "net",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-net//jar"],
)

java_library(
    name = "dbcp",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-dbcp//jar"],
    runtime_deps = [":pool"],
)

java_library(
    name = "pool",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-pool//jar"],
)

java_library(
    name = "text",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@commons-text//jar"],
)

java_library(
    name = "validator",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-validator//jar"],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@commons-io//jar"],
)
