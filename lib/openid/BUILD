java_library(
    name = "consumer",
    visibility = ["//visibility:public"],
    exports = ["@openid_consumer//jar"],
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
    exports = ["@nekohtml//jar"],
    runtime_deps = [":xerces"],
)

java_library(
    name = "xerces",
    exports = ["@xerces//jar"],
)
