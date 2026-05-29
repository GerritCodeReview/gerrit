load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//antlr3:query_parser",
        "//lib:guava",
        "//lib/antlr:java-runtime",
        "//lib/errorprone:annotations",
        "//lib/truth",
    ],
)
