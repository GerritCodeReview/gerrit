[java_library(
    name = n,
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
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@user//jar"],
)

java_library(
    name = "dev-neverlink",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@dev//jar"],
)

java_library(
    name = "javax-validation_src",
    visibility = ["//visibility:public"],
    exports = ["@javax_validation//jar:src"],
)

java_library(
    name = "jsinterop-annotations_src",
    visibility = ["//visibility:public"],
    exports = ["@jsinterop_annotations//jar:src"],
)
