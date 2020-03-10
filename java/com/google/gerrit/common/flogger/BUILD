load("@rules_java//java:defs.bzl", "java_library")

# TODO(dpursehouse) remove this when this issue on bazel is resolved:
# https://github.com/bazelbuild/bazel/issues/10948

java_library(
    name = "flogger",
    srcs = ["NamedFluentLogger.java"],
    deps = [
        "//lib/flogger:api",
    ],
)
