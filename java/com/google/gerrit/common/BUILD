load("@rules_java//java:defs.bzl", "java_library")

ANNOTATIONS = [
    "Nullable.java",
    "UsedAt.java",
]

java_library(
    name = "annotations",
    srcs = ANNOTATIONS,
    visibility = ["//visibility:public"],
    deps = ["//lib:guava"],
)

java_library(
    name = "server",
    srcs = glob(
        ["**/*.java"],
        exclude = ANNOTATIONS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/launcher",
        "//java/com/google/gerrit/prettify:server",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
    ],
)

# ":version" should not be in the dependency graph of the acceptance
# tests to avoid spurious test re-runs. That's because the content of
# //:version.txt is changed when the outcome of `git describe` is changed.
java_library(
    name = "version",
    resources = [":Version"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "gen_version",
    srcs = ["//:version.txt"],
    outs = ["Version"],
    cmd = "cat $< > $@",
)
