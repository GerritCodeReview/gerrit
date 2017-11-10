load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "diff_tests",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/diff",
        "//java/org/eclipse/jgit:server",
        "//lib:guava",
        "//lib:truth-java8-extension",
        "//lib/commons:codec",
    ],
)
