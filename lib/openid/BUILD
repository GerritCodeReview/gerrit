load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "consumer",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit-maven//:org_openid4java_openid4java"],
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
    exports = ["@gerrit-maven//:net_sourceforge_nekohtml_nekohtml"],
    runtime_deps = [":xerces"],
)

java_library(
    name = "xerces",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@gerrit-maven//:xerces_xercesImpl"],
)
