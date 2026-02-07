load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "consumer",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_openid4java_openid4java"],
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
    exports = ["@external_deps//:net_sourceforge_nekohtml_nekohtml"],
    runtime_deps = [":xerces"],
)

java_library(
    name = "xerces",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:xerces_xercesImpl"],
)
