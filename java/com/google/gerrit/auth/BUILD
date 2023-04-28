load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:javadoc.bzl", "java_doc")

# Giant kitchen-sink target.
#
# The only reason this hasn't been split up further is because we have too many
# tangled dependencies (and Guice unfortunately makes it quite easy to get into
# this state). Which means if you see an opportunity to split something off, you
# should seize it.
java_library(
    name = "auth",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/util/ssl",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib:servlet-api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//proto:cache_java_proto",
    ],
)
