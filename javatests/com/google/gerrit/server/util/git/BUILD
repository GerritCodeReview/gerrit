load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "git_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/util/git",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/truth",
        "//java/org/eclipse/jgit:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:protobuf",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:codec",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
    ],
)
