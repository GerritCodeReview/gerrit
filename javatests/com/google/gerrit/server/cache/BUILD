load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:junit",
        "//lib:protobuf",
        "//lib:truth",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
