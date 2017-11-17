package(default_visibility = ["//visibility:public"])

#VERSION = "2.8.9"

java_library(
    name = "jackson-annotations",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jackson_annotations//jar"],
)

java_library(
    name = "jackson-core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/elasticsearch:__pkg__",
        "//plugins:__pkg__",
    ],
    exports = ["@jackson-core//jar"],
)

java_library(
    name = "jackson-databind",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jackson_databind//jar"],
)
