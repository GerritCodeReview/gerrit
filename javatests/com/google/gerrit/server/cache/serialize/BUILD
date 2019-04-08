load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/cache/testing",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gwtorm",
        "//lib:guava",
        "//lib:junit",
        "//lib:protobuf",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
        "//proto/testing:test_java_proto",
    ],
)
