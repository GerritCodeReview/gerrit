java_library(
    name = "consumer",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@openid-consumer//jar"],
    runtime_deps = [
        ":nekohtml",
        ":xerces",
        "//lib/guice",
        "//lib/httpcomponents:httpclient",
        "//lib/log:jcl-over-slf4j",
    ],
)

java_library(
    name = "nekohtml",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@nekohtml//jar"],
    runtime_deps = [":xerces"],
)

java_library(
    name = "xerces",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@xerces//jar"],
)
