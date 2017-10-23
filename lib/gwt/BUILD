[java_library(
    name = n,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@%s//jar" % n.replace("-", "_")],
) for n in [
    "ant",
    "colt",
    "dev",
    "javax-validation",
    "jsinterop-annotations",
    "tapestry",
    "user",
    "w3c-css-sac",
]]

java_library(
    name = "user-neverlink",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@user//jar"],
    runtime_deps = [
        "//lib:jsr305",
    ],
)

java_library(
    name = "dev-neverlink",
    data = ["//lib:LICENSE-Apache2.0"],
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@dev//jar"],
    runtime_deps = [
        "//lib:jsr305",
    ],
)

java_library(
    name = "javax-validation_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javax_validation//jar:src"],
)

java_library(
    name = "jsinterop-annotations_src",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jsinterop_annotations//jar:src"],
)
