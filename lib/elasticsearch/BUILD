package(default_visibility = ["//visibility:public"])

java_library(
    name = "elasticsearch",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@elasticsearch//jar"],
    runtime_deps = [
        ":joda-time",
        "//lib/jackson:jackson-core",
        "//lib/lucene:lucene-suggest",
    ],
)

java_library(
    name = "joda-time",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@joda_time//jar"],
    runtime_deps = ["joda-convert"],
)

java_library(
    name = "joda-convert",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@joda_convert//jar"],
)

java_library(
    name = "jsr166e",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//javatests/com/google/gerrit/elasticsearch:__pkg__"],
    exports = ["@jsr166e//jar"],
)

java_library(
    name = "t_digest",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//javatests/com/google/gerrit/elasticsearch:__pkg__"],
    exports = ["@t_digest//jar"],
)

java_library(
    name = "netty",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//javatests/com/google/gerrit/elasticsearch:__pkg__"],
    exports = ["@netty//jar"],
)

java_library(
    name = "hppc",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//javatests/com/google/gerrit/elasticsearch:__pkg__"],
    exports = ["@hppc//jar"],
)
