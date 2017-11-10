load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "patchlib_tests",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/diff:patchlib",
        "//java/org/eclipse/jgit:server",
        "//lib:guava",
        "//lib:truth-java8-extension",
        "//lib/commons:codec",
    ],
)
